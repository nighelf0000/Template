package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.EngineConfigDTO;
import com.template.dto.LegendItemDTO;
import com.template.dto.ParagraphItemDTO;
import com.template.dto.PdfParagraphPosition;
import com.template.dto.PreviewResultDTO;
import com.template.entity.*;
import com.template.service.WordParseService;
import com.template.service.pdf.PdfConversionService;
import com.template.util.ParagraphStyleExtractor;
import com.template.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordParseService {

    private final UploadFileMapper uploadFileMapper;
    private final TemplateConfigMapper templateConfigMapper;
    private final TemplateRuleMapper templateRuleMapper;
    private final EngineConfigMapper engineConfigMapper;
    private final RecognitionEngine recognitionEngine;
    private final ObjectMapper objectMapper;
    private final PdfConversionService pdfConversionService;

    public Page<UploadFile> list(int page, int size) {
        LambdaQueryWrapper<UploadFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(UploadFile::getCreatedAt)
               .select(UploadFile.class, info -> !info.getColumn().equals("original_content")
                       && !info.getColumn().equals("parsed_content")
                       && !info.getColumn().equals("parsed_json")
                       && !info.getColumn().equals("output_content"));
        Page<UploadFile> result = uploadFileMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量填充模板名称
        List<Long> templateIds = result.getRecords().stream()
                .map(UploadFile::getTemplateId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (!templateIds.isEmpty()) {
            Map<Long, String> nameMap = templateConfigMapper.selectBatchIds(templateIds).stream()
                    .collect(Collectors.toMap(TemplateConfig::getId, TemplateConfig::getName));
            result.getRecords().forEach(f -> {
                if (f.getTemplateId() != null) {
                    f.setTemplateName(nameMap.get(f.getTemplateId()));
                }
            });
        }

        return result;
    }

    @Transactional
    public UploadFile upload(MultipartFile file, Long templateId) throws IOException {
        UploadFile uf = new UploadFile();
        uf.setTemplateId(templateId);
        uf.setOriginalName(file.getOriginalFilename());
        uf.setOriginalSize(file.getSize());
        uf.setOriginalContent(file.getBytes());
        uf.setStatus("UPLOADED");
        uploadFileMapper.insert(uf);
        return uf;
    }

    @Transactional
    public UploadFile parse(Long fileId) {
        UploadFile uf = uploadFileMapper.selectById(fileId);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        if (uf.getTemplateId() == null) {
            throw new RuntimeException("未指定解析模板");
        }

        TemplateConfig config = templateConfigMapper.selectById(uf.getTemplateId());
        if (config == null || config.getIsActive() != 1) {
            throw new RuntimeException("模板不存在或已停用");
        }

        // 获取规则列表
        LambdaQueryWrapper<TemplateRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(TemplateRule::getTemplateId, uf.getTemplateId());
        List<TemplateRule> rules = templateRuleMapper.selectList(ruleWrapper);

        // 获取引擎配置列表
        LambdaQueryWrapper<EngineConfig> engineWrapper = new LambdaQueryWrapper<>();
        engineWrapper.eq(EngineConfig::getTemplateId, uf.getTemplateId());
        List<EngineConfig> engineConfigs = engineConfigMapper.selectList(engineWrapper);

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(uf.getOriginalContent());
            XWPFDocument doc = new XWPFDocument(bais);

            // 执行识别
            List<RecognitionEngine.ParagraphMatch> matches = recognitionEngine.recognize(doc, engineConfigs, rules);

            // 构建JSON
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("template_id", config.getId());
            json.put("template_name", config.getName());
            json.put("paragraphs", matches.stream().map(this::matchToMap).collect(Collectors.toList()));

            String parsedJsonStr = objectMapper.writeValueAsString(matches);

            // 生成预览Word文件
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            byte[] parsedContent = baos.toByteArray();

            uf.setParsedContent(parsedContent);
            uf.setParsedJson(parsedJsonStr);
            uf.setParsedAt(LocalDateTime.now());
            uf.setStatus("PARSED");
            uf.setErrorMessage(null);
            uploadFileMapper.updateById(uf);

            doc.close();
            bais.close();
            baos.close();
        } catch (Exception e) {
            uf.setStatus("PARSE_FAILED");
            uf.setErrorMessage(e.getMessage());
            uploadFileMapper.updateById(uf);
            log.error("文件解析失败: fileId={}", fileId, e);
            throw new RuntimeException("解析失败: " + e.getMessage(), e);
        }

        return uf;
    }

    public UploadFile getFile(Long id) {
        return uploadFileMapper.selectById(id);
    }

    public String getPreviewJson(Long id) {
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        return uf.getParsedJson();
    }

    /**
     * 结构化预览：提取段落原始样式 + 规则底色，返回 DTO。
     */
    public PreviewResultDTO preview(Long fileId) {
        UploadFile uf = uploadFileMapper.selectById(fileId);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        if (uf.getTemplateId() == null) {
            throw new RuntimeException("未指定模板");
        }
        if (uf.getOriginalContent() == null) {
            throw new RuntimeException("文件内容为空");
        }
        if (uf.getParsedJson() == null) {
            throw new RuntimeException("文件尚未解析，请先执行解析操作");
        }

        // 查询模板名称
        String templateName = null;
        TemplateConfig config = templateConfigMapper.selectById(uf.getTemplateId());
        if (config != null) {
            templateName = config.getName();
        }

        // 查询规则列表
        List<TemplateRule> rules = templateRuleMapper.selectList(
                new LambdaQueryWrapper<TemplateRule>().eq(TemplateRule::getTemplateId, uf.getTemplateId()));
        Map<Long, TemplateRule> ruleMap = rules.stream()
                .collect(Collectors.toMap(TemplateRule::getId, r -> r));

        // 反序列化匹配数据
        List<RecognitionEngine.ParagraphMatch> matches;
        try {
            matches = objectMapper.readValue(
                    uf.getParsedJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RecognitionEngine.ParagraphMatch.class));
        } catch (Exception e) {
            log.error("parsedJson 反序列化失败: fileId={}", fileId, e);
            throw new RuntimeException("解析数据反序列化失败", e);
        }

        // 从 original_content 构建 XWPFDocument（获取段落原始样式）
        XWPFDocument doc;
        List<XWPFParagraph> paragraphs;
        try {
            doc = new XWPFDocument(new ByteArrayInputStream(uf.getOriginalContent()));
            paragraphs = doc.getParagraphs();
        } catch (Exception e) {
            log.error("originalContent 读取失败: fileId={}", fileId, e);
            throw new RuntimeException("文档内容读取失败", e);
        }

        // 生成图例
        List<LegendItemDTO> legend = generateLegend(matches, ruleMap);

        // 遍历匹配项，组装 DTO
        List<ParagraphItemDTO> items = new ArrayList<>();
        for (RecognitionEngine.ParagraphMatch match : matches) {
            ParagraphItemDTO item = new ParagraphItemDTO();
            item.setIndex(match.getIndex());
            item.setText(match.getText());
            item.setMatchedType(match.getMatchedType() != null ? match.getMatchedType().name() : "UNKNOWN");
            item.setMatchedLevel(match.getMatchedLevel());
            item.setRuleId(match.getRuleId());
            item.setRuleName(match.getRuleName());
            item.setStartOffset(match.getStartOffset());
            item.setEndOffset(match.getEndOffset());
            item.setMatchedEngineConfigId(match.getMatchedEngineConfigId());

            // 提取段落原始样式（索引越界保护）
            if (match.getIndex() < paragraphs.size()) {
                XWPFParagraph paragraph = paragraphs.get(match.getIndex());
                item.setStyle(ParagraphStyleExtractor.extract(paragraph));
            } else {
                log.warn("预览索引越界: index={}, totalParagraphs={}", match.getIndex(), paragraphs.size());
                item.setStyle(new LinkedHashMap<>());
            }

            // 确定底色
            TemplateRule rule = match.getRuleId() != null ? ruleMap.get(match.getRuleId()) : null;
            if (match.getMatchedType() == RecognitionEngine.MatchedType.UNKNOWN) {
                item.setBackgroundColor(null);
            } else {
                item.setBackgroundColor(determineBackgroundColor(match.getRuleId(), rule));
            }

            items.add(item);
        }

        // 关闭文档
        try {
            doc.close();
        } catch (Exception e) {
            log.warn("关闭XWPFDocument异常", e);
        }

        // 生成 PDF 段落位置信息（用于前端精确高亮）
        int totalPdfChars = 0;
        try {
            List<PdfParagraphPosition> pdfPositions = new ArrayList<>();
            pdfConversionService.convertToPdfWithPositions(uf.getOriginalContent(), uf.getOriginalName(), pdfPositions);
            java.util.Map<Integer, PdfParagraphPosition> posMap = new java.util.HashMap<>();
            for (PdfParagraphPosition pos : pdfPositions) {
                posMap.put(pos.getParagraphIndex(), pos);
            }
            for (ParagraphItemDTO item : items) {
                PdfParagraphPosition pos = posMap.get(item.getIndex());
                if (pos != null) {
                    item.setPdfStartPos(pos.getPdfStartPos());
                    item.setPdfEndPos(pos.getPdfEndPos());
                }
            }

            for (PdfParagraphPosition pos : pdfPositions) {
                if (pos.getPdfEndPos() > totalPdfChars) {
                    totalPdfChars = pos.getPdfEndPos();
                }
            }
        } catch (Exception e) {
            log.warn("PDF 位置生成失败，高亮将使用比例估算: fileId={}", fileId, e);
        }

        // 组装结果
        PreviewResultDTO result = new PreviewResultDTO();
        result.setTotalPdfChars(totalPdfChars);
        result.setTemplateId(uf.getTemplateId());
        result.setTemplateName(templateName);
        result.setPdfUrl("/api/word/" + fileId + "/preview/pdf");
        result.setParagraphs(items);
        result.setLegend(legend);

        return result;
    }

    /**
     * 从前端调整 JSON 解析的 matches 可能缺少 ruleId/ruleName，
     * 根据 matchedType + 当前模板规则回填。
     */
    private void backfillFromManualAdjust(List<RecognitionEngine.ParagraphMatch> matches,
                                           List<TemplateRule> rules) {
        // 按 matchedType 查找对应的规则
        java.util.Map<String, TemplateRule> typeRuleMap = new java.util.HashMap<>();
        for (TemplateRule rule : rules) {
            if (rule.getName() != null) {
                String upperName = rule.getName().toUpperCase();
                typeRuleMap.put(upperName, rule);
            }
        }
        // 常见类型映射
        typeRuleMap.put("COVER", null);
        typeRuleMap.put("TOC", null);

        for (RecognitionEngine.ParagraphMatch match : matches) {
            if (match.getRuleId() != null) {
                continue; // 已有 ruleId，不需要回填
            }
            if (match.getMatchedType() == null) {
                continue;
            }
            String typeName = match.getMatchedType().name();
            TemplateRule rule = typeRuleMap.get(typeName);
            if (rule != null) {
                match.setRuleId(rule.getId());
                match.setRuleName(rule.getName());
            } else if ("TITLE".equals(typeName)) {
                // 标题类型查找标题规则（取第一个标题规则如果没有匹配）
                for (TemplateRule r : rules) {
                    if (r.getName() != null && r.getName().contains("标题")) {
                        match.setRuleId(r.getId());
                        match.setRuleName(r.getName());
                        break;
                    }
                }
            } else if ("BODY".equals(typeName)) {
                // 正文类型
                for (TemplateRule r : rules) {
                    if ("正文".equals(r.getName())) {
                        match.setRuleId(r.getId());
                        match.setRuleName(r.getName());
                        break;
                    }
                }
            }
        }
    }

    /**
     * 生成图例：收集所有非空 ruleId 对应的规则及其底色。
     */
    private List<LegendItemDTO> generateLegend(List<RecognitionEngine.ParagraphMatch> matches,
                                               Map<Long, TemplateRule> ruleMap) {
        List<LegendItemDTO> legend = new ArrayList<>();
        java.util.Set<Long> added = new HashSet<>();

        for (RecognitionEngine.ParagraphMatch match : matches) {
            if (match.getRuleId() == null || added.contains(match.getRuleId())) {
                continue;
            }
            added.add(match.getRuleId());

            TemplateRule rule = ruleMap.get(match.getRuleId());
            String color = determineBackgroundColor(match.getRuleId(), rule);

            LegendItemDTO item = new LegendItemDTO();
            item.setRuleId(match.getRuleId());
            item.setRuleName(match.getRuleName());
            item.setMatchedType(match.getMatchedType() != null ? match.getMatchedType().name() : "UNKNOWN");
            item.setColor(color != null ? color : "#f0f0f0");
            legend.add(item);
        }

        return legend;
    }

    /**
     * 确定规则底色：优先使用 highlightColor，否则按 ruleId HSL 自动生成。
     */
    private String determineBackgroundColor(Long ruleId, TemplateRule rule) {
        if (rule != null && rule.getHighlightColor() != null && !rule.getHighlightColor().isEmpty()) {
            return rule.getHighlightColor();
        }
        if (ruleId != null) {
            int hue = (int) ((ruleId * 137.508) % 360);
            return String.format("hsl(%d, 60%%, 85%%)", hue);
        }
        return null;
    }

    @Transactional
    public void saveAdjust(Long id, String adjustJson) {
        if (adjustJson == null || adjustJson.isBlank()) {
            throw new IllegalArgumentException("adjustJson 不能为空");
        }
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }

        // 反序列化调整 JSON，回填 ruleId/ruleName
        try {
            List<TemplateRule> rules = templateRuleMapper.selectList(
                    new LambdaQueryWrapper<TemplateRule>().eq(TemplateRule::getTemplateId, uf.getTemplateId()));

            List<RecognitionEngine.ParagraphMatch> matches = objectMapper.readValue(
                    adjustJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RecognitionEngine.ParagraphMatch.class));

            backfillFromManualAdjust(matches, rules);

            // 处理 SPECIAL/UNKNOWN 段落的引擎配置创建或更新
            for (RecognitionEngine.ParagraphMatch match : matches) {
                if (match.getRuleId() == null || match.getText() == null || match.getText().isEmpty()) {
                    continue;
                }
                if (match.getMatchedType() == RecognitionEngine.MatchedType.SPECIAL) {
                    // SPECIAL 段落：更新已有配置
                    if (match.getMatchedEngineConfigId() != null) {
                        updateSpecialConfig(match.getMatchedEngineConfigId(), match.getText(), match.getRuleId());
                    } else {
                        // 兼容旧数据：matchedEngineConfigId 为 null 时降级为按文本查找
                        updateSpecialConfigByTemplateAndText(uf.getTemplateId(), match.getText(), match.getRuleId());
                    }
                } else if (match.getMatchedType() == RecognitionEngine.MatchedType.UNKNOWN) {
                    // UNKNOWN 段落：创建或更新配置
                    autoCreateOrUpdateSpecialConfig(uf.getTemplateId(), match.getText(), match.getRuleId());
                    match.setMatchedType(RecognitionEngine.MatchedType.SPECIAL);
                }
            }

            uf.setParsedJson(objectMapper.writeValueAsString(matches));
        } catch (Exception e) {
            log.error("调整 JSON 处理失败: fileId={}", id, e);
            throw new RuntimeException("调整 JSON 处理失败: " + e.getMessage(), e);
        }

        uf.setAdjustedAt(LocalDateTime.now());
        uf.setStatus("ADJUSTED");
        uploadFileMapper.updateById(uf);
    }

    /**
     * 自动创建或更新 SPECIAL 类型的 engine_config 记录。
     * 如果同模板下同一段落文本已有 SPECIAL 配置（无论 ruleId），则更新其 ruleId；
     * 否则创建新记录。
     */
    private void autoCreateOrUpdateSpecialConfig(Long templateId, String text, Long ruleId) {
        String pattern = "^" + Pattern.quote(text) + "$";

        LambdaQueryWrapper<EngineConfig> check = new LambdaQueryWrapper<>();
        check.eq(EngineConfig::getTemplateId, templateId)
             .eq(EngineConfig::getMatchType, "SPECIAL")
             .eq(EngineConfig::getPattern, pattern);
        List<EngineConfig> existingList = engineConfigMapper.selectList(check);
        if (!existingList.isEmpty()) {
            // 更新已有配置的 ruleId
            EngineConfig existing = existingList.get(0);
            existing.setRuleId(ruleId);
            engineConfigMapper.updateById(existing);
            return;
        }

        LambdaQueryWrapper<EngineConfig> maxOrderQuery = new LambdaQueryWrapper<>();
        maxOrderQuery.eq(EngineConfig::getTemplateId, templateId)
                     .eq(EngineConfig::getMatchType, "SPECIAL");
        Integer maxSort = engineConfigMapper.selectList(maxOrderQuery).stream()
                .map(EngineConfig::getSortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        EngineConfig config = new EngineConfig();
        config.setTemplateId(templateId);
        config.setConfigName(generateConfigName(text));
        config.setPattern(pattern);
        config.setMatchType("SPECIAL");
        config.setRuleId(ruleId);
        config.setSortOrder(maxSort + 1);
        config.setIsActive(1);
        engineConfigMapper.insert(config);
    }

    /**
     * 生成配置名称：特殊样式 + 截取段落前10个字符
     */
    private String generateConfigName(String text) {
        String cleanText = text != null ? text.trim().replaceAll("\\s+", " ") : "";
        if (cleanText.length() > 10) {
            cleanText = cleanText.substring(0, 10);
        }
        return "特殊样式" + cleanText;
    }

    /**
     * 更新指定 SPECIAL 引擎配置的段落文本和规则。
     */
    private void updateSpecialConfig(Long configId, String text, Long ruleId) {
        EngineConfig config = engineConfigMapper.selectById(configId);
        if (config == null) {
            log.warn("SPECIAL 配置不存在: configId={}", configId);
            return;
        }
        String pattern = "^" + Pattern.quote(text) + "$";
        config.setPattern(pattern);
        config.setRuleId(ruleId);
        config.setConfigName(generateConfigName(text));
        engineConfigMapper.updateById(config);
    }

    /**
     * 按模板+文本查找 SPECIAL 配置并更新其 ruleId（兼容旧数据降级使用）。
     */
    private void updateSpecialConfigByTemplateAndText(Long templateId, String text, Long ruleId) {
        String pattern = "^" + Pattern.quote(text) + "$";
        LambdaQueryWrapper<EngineConfig> query = new LambdaQueryWrapper<>();
        query.eq(EngineConfig::getTemplateId, templateId)
             .eq(EngineConfig::getMatchType, "SPECIAL")
             .eq(EngineConfig::getPattern, pattern);
        List<EngineConfig> list = engineConfigMapper.selectList(query);
        if (!list.isEmpty()) {
            EngineConfig config = list.get(0);
            config.setRuleId(ruleId);
            config.setConfigName(generateConfigName(text));
            engineConfigMapper.updateById(config);
        }
    }

    @Transactional
    public UploadFile export(Long fileId) {
        UploadFile uf = uploadFileMapper.selectById(fileId);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        // 状态校验：仅当文件已解析/已调整/已导出时才允许导出
        String status = uf.getStatus();
        if (!"PARSED".equals(status) && !"ADJUSTED".equals(status) && !"EXPORTED".equals(status)) {
            throw new RuntimeException("文件尚未解析，请先执行解析操作");
        }

        try {
            byte[] sourceContent = uf.getOriginalContent();
            ByteArrayInputStream bais = new ByteArrayInputStream(sourceContent);
            XWPFDocument doc = new XWPFDocument(bais);

            // 获取规则
            List<TemplateRule> rules = templateRuleMapper.selectList(
                    new LambdaQueryWrapper<TemplateRule>().eq(TemplateRule::getTemplateId, uf.getTemplateId()));

            Map<Long, TemplateRule> ruleMap = new LinkedHashMap<>();
            for (TemplateRule rule : rules) {
                ruleMap.put(rule.getId(), rule);
            }

            // 使用 parsedJson（已包含人工调整结果）
            String jsonToUse = uf.getParsedJson();

            List<RecognitionEngine.ParagraphMatch> matches = objectMapper.readValue(
                    jsonToUse, objectMapper.getTypeFactory().constructCollectionType(List.class, RecognitionEngine.ParagraphMatch.class));

            // 应用样式到段落
            applyStyles(doc, matches, ruleMap);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            byte[] outputBytes = baos.toByteArray();

            String outputName = uf.getOriginalName();
            if (outputName != null && outputName.endsWith(".docx")) {
                outputName = outputName.substring(0, outputName.length() - 5) + "_格式化.docx";
            } else {
                outputName = "导出文件_格式化.docx";
            }

            uf.setOutputContent(outputBytes);
            uf.setOutputName(outputName);
            uf.setExportedAt(LocalDateTime.now());
            uf.setStatus("EXPORTED");
            uf.setErrorMessage(null);
            uploadFileMapper.updateById(uf);

            doc.close();
            bais.close();
            baos.close();
        } catch (Exception e) {
            uf.setStatus("EXPORT_FAILED");
            uf.setErrorMessage(e.getMessage());
            uploadFileMapper.updateById(uf);
            log.error("文件导出失败: fileId={}", fileId, e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }

        return uf;
    }

    private void applyStyles(XWPFDocument doc, List<RecognitionEngine.ParagraphMatch> matches,
                             Map<Long, TemplateRule> ruleMap) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (RecognitionEngine.ParagraphMatch match : matches) {
            if (match.getIndex() >= paragraphs.size()) continue;
            XWPFParagraph paragraph = paragraphs.get(match.getIndex());

            TemplateRule rule = ruleMap.get(match.getRuleId());
            if (rule == null) continue;

            // 应用字体和段落样式
            for (XWPFRun run : paragraph.getRuns()) {
                if (rule.getFontName() != null) run.setFontFamily(rule.getFontName());
                if (rule.getFontSize() != null) run.setFontSize(rule.getFontSize());
                if (rule.getFontBold() != null) run.setBold(rule.getFontBold() == 1);
                if (rule.getFontItalic() != null) run.setItalic(rule.getFontItalic() == 1);
                if (rule.getFontUnderline() != null)
                    run.setUnderline(rule.getFontUnderline() == 1 ? UnderlinePatterns.SINGLE : UnderlinePatterns.NONE);
                if (rule.getFontColor() != null) {
                    try {
                        String hex = rule.getFontColor().replace("#", "");
                        int r = Integer.parseInt(hex.substring(0, 2), 16);
                        int g = Integer.parseInt(hex.substring(2, 4), 16);
                        int b = Integer.parseInt(hex.substring(4, 6), 16);
                        run.setColor(String.format("%02X%02X%02X", r, g, b));
                    } catch (Exception ignored) {}
                }
            }

            // 段落对齐
            if (rule.getTextAlign() != null) {
                switch (rule.getTextAlign()) {
                    case "LEFT": paragraph.setAlignment(ParagraphAlignment.LEFT); break;
                    case "CENTER": paragraph.setAlignment(ParagraphAlignment.CENTER); break;
                    case "RIGHT": paragraph.setAlignment(ParagraphAlignment.RIGHT); break;
                    case "JUSTIFY": paragraph.setAlignment(ParagraphAlignment.BOTH); break;
                }
            }

            // 段落缩进
            if (rule.getTextIndent() != null) {
                paragraph.setIndentationFirstLine((int) (rule.getTextIndent().doubleValue() * 240));
            }

            // 行距
            if (rule.getLineSpacing() != null) {
                paragraph.setSpacingBetween(
                        rule.getLineSpacing().doubleValue(),
                        org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
            }

            // 段前段后间距
            if (rule.getSpaceBefore() != null) {
                paragraph.setSpacingBefore(rule.getSpaceBefore().intValue());
            }
            if (rule.getSpaceAfter() != null) {
                paragraph.setSpacingAfter(rule.getSpaceAfter().intValue());
            }
        }
    }

    private Map<String, Object> matchToMap(RecognitionEngine.ParagraphMatch match) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", match.getIndex());
        map.put("text", match.getText());
        map.put("matched_type", match.getMatchedType() != null ? match.getMatchedType().name() : "UNKNOWN");
        map.put("matched_level", match.getMatchedLevel());
        map.put("rule_id", match.getRuleId());
        map.put("rule_name", match.getRuleName());
        map.put("start_offset", match.getStartOffset());
        map.put("end_offset", match.getEndOffset());
        map.put("matched_engine_config_id", match.getMatchedEngineConfigId());
        return map;
    }

    @Transactional
    public void updateTemplate(Long fileId, Long templateId) {
        UploadFile uf = uploadFileMapper.selectById(fileId);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        if (templateId != null) {
            TemplateConfig config = templateConfigMapper.selectById(templateId);
            if (config == null || config.getIsActive() != 1) {
                throw new RuntimeException("模板不存在或已停用");
            }
        }
        uf.setTemplateId(templateId);
        uploadFileMapper.updateById(uf);
    }

    public byte[] download(Long id) {
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null || uf.getOutputContent() == null) {
            throw new RuntimeException("导出文件不存在");
        }
        return uf.getOutputContent();
    }

    @Transactional
    public void delete(Long id) {
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null) {
            throw new RuntimeException("文件不存在");
        }
        uploadFileMapper.deleteById(id);
        log.info("文件已删除: id={}", id);
    }
}
