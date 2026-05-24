package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.ParseRecordCreateDTO;
import com.template.dto.ParseRecordDetailVO;
import com.template.dto.ParseRecordVO;
import com.template.entity.ParseDiffReport;
import com.template.entity.ParseElement;
import com.template.entity.ParseRecord;
import com.template.mapper.ParseDiffReportMapper;
import com.template.mapper.ParseElementMapper;
import com.template.mapper.ParseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseRecordService {

    private final ParseRecordMapper parseRecordMapper;
    private final ParseElementMapper parseElementMapper;
    private final ParseDiffReportMapper parseDiffReportMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存解析结果（供 Python 引擎调用），同时提取元素并生成 diff 报告
     */
    @Transactional
    public Long save(ParseRecordCreateDTO dto) {
        log.info("收到解析结果保存请求: templateId={}, sourceFile={}, fileSize={}",
                dto.getTemplateId(), dto.getSourceFile(), dto.getFileSize());

        ParseRecord record = new ParseRecord();
        record.setTemplateId(dto.getTemplateId());
        record.setUploadFileId(dto.getUploadFileId());
        record.setSourceFile(dto.getSourceFile());
        record.setSourceChecksum(dto.getSourceChecksum());
        record.setFileSize(dto.getFileSize());
        record.setParsedAt(LocalDateTime.now());
        record.setEngineVersion(dto.getEngineVersion() != null ? dto.getEngineVersion() : "1.0.0");
        record.setRulesetName(dto.getRulesetName() != null ? dto.getRulesetName() : "default");
        record.setRulesetVersion(dto.getRulesetVersion() != null ? dto.getRulesetVersion() : "1.0");
        record.setProcessingTimeMs(dto.getProcessingTimeMs() != null ? dto.getProcessingTimeMs() : 0);
        record.setStatus(dto.getStatus() != null ? dto.getStatus() : "success");
        record.setErrorMessage(dto.getErrorMessage());
        record.setStructureTree(toJsonString(dto.getStructureTree()));
        record.setDocumentMeta(toJsonString(dto.getDocumentMeta()));
        record.setElementSummary(toJsonString(dto.getElementSummary()));
        record.setTags(dto.getTags());

        parseRecordMapper.insert(record);
        Long recordId = record.getId();
        log.info("解析结果已保存: id={}, sourceFile={}, templateId={}", recordId, record.getSourceFile(), record.getTemplateId());

        // 提取元素并写入 parse_element
        int elementCount = extractAndSaveElements(recordId, dto.getStructureTree());
        log.info("解析元素已保存: recordId={}, elementCount={}", recordId, elementCount);

        // 与同模板前一条记录对比，生成 diff 报告
        generateDiffReport(recordId, dto.getTemplateId());

        return recordId;
    }

    /**
     * 将 Object 序列化为 JSON 字符串，null 返回 null
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 structureTree 中提取元素，批量插入 parse_element
     * @return 插入的元素数量
     */
    @SuppressWarnings("unchecked")
    private int extractAndSaveElements(Long recordId, Object structureTree) {
        if (structureTree == null) {
            log.info("structureTree 为空，跳过元素提取: recordId={}", recordId);
            return 0;
        }
        try {
            Map<String, Object> tree = (Map<String, Object>) structureTree;
            Map<String, Object> root = (Map<String, Object>) tree.get("root");
            if (root == null) {
                log.info("structureTree.root 为空，跳过元素提取: recordId={}", recordId);
                return 0;
            }
            List<ParseElement> elements = new ArrayList<>();
            collectElements(root, null, recordId, 0, elements);
            for (ParseElement el : elements) {
                parseElementMapper.insert(el);
            }
            return elements.size();
        } catch (Exception e) {
            log.warn("元素提取失败: recordId={}, error={}", recordId, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectElements(Map<String, Object> node, String parentElementId,
                                  Long recordId, int sortOrder, List<ParseElement> result) {
        String elemId = (String) node.get("id");
        String elemType = (String) node.get("type");
        Object levelObj = node.get("level");
        Integer level = levelObj instanceof Integer ? (Integer) levelObj : null;
        Object confidenceObj = node.get("confidence");
        BigDecimal confidence = confidenceObj instanceof Number
                ? BigDecimal.valueOf(((Number) confidenceObj).doubleValue()) : BigDecimal.ZERO;

        String contentText = null;
        Map<String, Object> content = (Map<String, Object>) node.get("content");
        if (content != null) {
            Object text = content.get("text");
            if (text instanceof String) {
                contentText = (String) text;
            }
        }

        Map<String, Object> metadata = (Map<String, Object>) node.get("metadata");
        String metadataJson = toJsonString(metadata);
        String styleFeaturesJson = metadata != null ? toJsonString(metadata.get("style_features")) : null;

        ParseElement element = new ParseElement();
        element.setRecordId(recordId);
        element.setElementId(elemId);
        element.setElementType(elemType);
        element.setLevel(level != null ? level : 0);
        element.setContentText(contentText);
        element.setConfidence(confidence);
        element.setParentElementId(parentElementId);
        element.setSortOrder(sortOrder);
        element.setMetadata(metadataJson);
        element.setStyleFeatures(styleFeaturesJson);
        result.add(element);

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectElements(children.get(i), elemId, recordId, i, result);
            }
        }
    }

    /**
     * 与同模板上一条 parse_record 对比，生成 parse_diff_report
     */
    @SuppressWarnings("unchecked")
    private void generateDiffReport(Long recordId, Long templateId) {
        if (templateId == null) {
            log.info("templateId 为空，跳过 diff 生成: recordId={}", recordId);
            return;
        }
        try {
            // 查找同模板下最近的一条其他记录
            LambdaQueryWrapper<ParseRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ParseRecord::getTemplateId, templateId)
                    .ne(ParseRecord::getId, recordId)
                    .orderByDesc(ParseRecord::getParsedAt)
                    .last("LIMIT 1");
            ParseRecord prevRecord = parseRecordMapper.selectOne(wrapper);
            if (prevRecord == null || prevRecord.getStructureTree() == null) {
                log.info("无前一条记录可对比: recordId={}, templateId={}", recordId, templateId);
                return;
            }

            ParseRecord currRecord = parseRecordMapper.selectById(recordId);
            if (currRecord == null || currRecord.getStructureTree() == null) {
                return;
            }

            Map<String, Object> prevTree = objectMapper.readValue(prevRecord.getStructureTree(), Map.class);
            Map<String, Object> currTree = objectMapper.readValue(currRecord.getStructureTree(), Map.class);

            // 对比 flat_index
            Map<String, String> prevIndex = (Map<String, String>) prevTree.get("flat_index");
            Map<String, String> currIndex = (Map<String, String>) currTree.get("flat_index");
            if (prevIndex == null) prevIndex = Map.of();
            if (currIndex == null) currIndex = Map.of();

            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> changed = new ArrayList<>();

            for (String key : currIndex.keySet()) {
                if (!prevIndex.containsKey(key)) {
                    added.add(key);
                } else if (!currIndex.get(key).equals(prevIndex.get(key))) {
                    changed.add(key);
                }
            }
            for (String key : prevIndex.keySet()) {
                if (!currIndex.containsKey(key)) {
                    removed.add(key);
                }
            }

            // 统计元素类型变化
            Map<String, Integer> prevTypeCount = countElementTypes(prevTree);
            Map<String, Integer> currTypeCount = countElementTypes(currTree);

            Map<String, Object> statisticsDiff = new LinkedHashMap<>();
            statisticsDiff.put("prev_total", prevIndex.size());
            statisticsDiff.put("curr_total", currIndex.size());
            statisticsDiff.put("added", added.size());
            statisticsDiff.put("removed", removed.size());
            statisticsDiff.put("changed", changed.size());

            Map<String, Object> structuralChanges = new LinkedHashMap<>();
            structuralChanges.put("added_elements", added);
            structuralChanges.put("removed_elements", removed);
            structuralChanges.put("changed_elements", changed);
            structuralChanges.put("prev_type_count", prevTypeCount);
            structuralChanges.put("curr_type_count", currTypeCount);

            String summary = String.format("元素数 %d→%d (新增%d, 移除%d, 变更%d)",
                    prevIndex.size(), currIndex.size(), added.size(), removed.size(), changed.size());

            ParseDiffReport diffReport = new ParseDiffReport();
            diffReport.setRecordIdA(prevRecord.getId());
            diffReport.setRecordIdB(recordId);
            diffReport.setSummary(summary);
            diffReport.setStructuralChanges(toJsonString(structuralChanges));
            diffReport.setStatisticsDiff(toJsonString(statisticsDiff));
            parseDiffReportMapper.insert(diffReport);
            log.info("Diff 报告已生成: id={}, recordA={}, recordB={}, {}",
                    diffReport.getId(), prevRecord.getId(), recordId, summary);

        } catch (Exception e) {
            log.warn("Diff 报告生成失败: recordId={}, templateId={}, error={}", recordId, templateId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> countElementTypes(Map<String, Object> tree) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        Map<String, Object> root = (Map<String, Object>) tree.get("root");
        if (root != null) {
            countTypesRecursive(root, counter);
        }
        return counter;
    }

    private void countTypesRecursive(Map<String, Object> node, Map<String, Integer> counter) {
        String type = (String) node.get("type");
        if (type != null) {
            counter.merge(type, 1, Integer::sum);
        }
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            for (Map<String, Object> child : children) {
                countTypesRecursive(child, counter);
            }
        }
    }

    /**
     * 分页查询
     */
    public Page<ParseRecordVO> page(Long templateId, String status, String keyword,
                                     String startTime, String endTime,
                                     String rulesetName, int page, int size) {
        LambdaQueryWrapper<ParseRecord> wrapper = new LambdaQueryWrapper<>();

        if (templateId != null) {
            wrapper.eq(ParseRecord::getTemplateId, templateId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ParseRecord::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ParseRecord::getSourceFile, keyword);
        }
        if (rulesetName != null && !rulesetName.isEmpty()) {
            wrapper.eq(ParseRecord::getRulesetName, rulesetName);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(ParseRecord::getParsedAt, startTime + " 00:00:00");
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(ParseRecord::getParsedAt, endTime + " 23:59:59");
        }

        wrapper.orderByDesc(ParseRecord::getParsedAt);

        Page<ParseRecord> pageResult = parseRecordMapper.selectPage(new Page<>(page, size), wrapper);

        Page<ParseRecordVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 获取详情
     */
    public ParseRecordVO detail(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        return toVO(record);
    }

    /**
     * 获取结构树 JSON
     */
    public String getStructureTree(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        return record.getStructureTree();
    }

    /**
     * 更新解析记录的结构树（供 Python 引擎在元素拆解后更新使用）
     */
    @Transactional
    public void updateStructureTree(Long id, Object structureTree) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        record.setStructureTree(toJsonString(structureTree));
        parseRecordMapper.updateById(record);
        log.info("结构树已更新: id={}", id);
    }

    /**
     * 获取完整详情（含结构树、文档元数据、元素统计）
     */
    public ParseRecordDetailVO getDetail(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        ParseRecordDetailVO vo = new ParseRecordDetailVO();
        vo.setId(record.getId());
        vo.setTemplateId(record.getTemplateId());
        vo.setTemplateName(record.getTemplateName());
        vo.setSourceFile(record.getSourceFile());
        vo.setStatus(record.getStatus());
        vo.setParsedAt(record.getParsedAt());
        vo.setEngineVersion(record.getEngineVersion());
        vo.setRulesetName(record.getRulesetName());
        vo.setProcessingTimeMs(record.getProcessingTimeMs());

        // 解析 JSON 字段为对象
        try {
            if (record.getStructureTree() != null && !record.getStructureTree().isEmpty()) {
                vo.setStructureTree(objectMapper.readValue(record.getStructureTree(), Map.class));
            }
        } catch (Exception e) {
            log.warn("structureTree JSON 解析失败: id={}, error={}", id, e.getMessage());
            vo.setStructureTree(record.getStructureTree());
        }

        try {
            if (record.getDocumentMeta() != null && !record.getDocumentMeta().isEmpty()) {
                vo.setDocumentMeta(objectMapper.readValue(record.getDocumentMeta(), Map.class));
            }
        } catch (Exception e) {
            log.warn("documentMeta JSON 解析失败: id={}, error={}", id, e.getMessage());
            vo.setDocumentMeta(record.getDocumentMeta());
        }

        try {
            if (record.getElementSummary() != null && !record.getElementSummary().isEmpty()) {
                vo.setElementSummary(objectMapper.readValue(record.getElementSummary(), Map.class));
            }
        } catch (Exception e) {
            log.warn("elementSummary JSON 解析失败: id={}, error={}", id, e.getMessage());
            vo.setElementSummary(record.getElementSummary());
        }

        return vo;
    }

    /**
     * 删除解析记录
     */
    @Transactional
    public void delete(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        parseRecordMapper.deleteById(id);
        log.info("解析记录已删除: id={}", id);
    }

    private ParseRecordVO toVO(ParseRecord entity) {
        ParseRecordVO vo = new ParseRecordVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setUploadFileId(entity.getUploadFileId());
        vo.setSourceFile(entity.getSourceFile());
        vo.setSourceChecksum(entity.getSourceChecksum());
        vo.setFileSize(entity.getFileSize());
        vo.setParsedAt(entity.getParsedAt());
        vo.setEngineVersion(entity.getEngineVersion());
        vo.setRulesetName(entity.getRulesetName());
        vo.setRulesetVersion(entity.getRulesetVersion());
        vo.setProcessingTimeMs(entity.getProcessingTimeMs());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setTags(entity.getTags());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
