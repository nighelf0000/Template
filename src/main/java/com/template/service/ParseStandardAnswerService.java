package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.ParseStandardAnswerVO;
import com.template.entity.ParseStandardAnswer;
import com.template.entity.ParseElement;
import com.template.mapper.ParseElementMapper;
import com.template.mapper.ParseStandardAnswerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseStandardAnswerService {

    private final ParseStandardAnswerMapper parseStandardAnswerMapper;
    private final ParseElementMapper parseElementMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 上传标准答案
     */
    @Transactional
    public ParseStandardAnswerVO upload(Long templateId, String sourceFile, String answerName,
                                         String description, String structureTreeJson) {
        // 解析 JSON 以验证格式
        Object structureTree;
        try {
            structureTree = objectMapper.readValue(structureTreeJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式无效: " + e.getMessage());
        }

        ParseStandardAnswer entity = new ParseStandardAnswer();
        entity.setTemplateId(templateId);
        entity.setSourceFile(sourceFile);
        entity.setAnswerName(answerName);
        entity.setStructureTree(structureTreeJson);
        entity.setDescription(description);

        // 提取文档元数据和元素统计
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tree = (Map<String, Object>) structureTree;
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) tree.get("root");
            if (root != null) {
                // 提取 document_meta
                Object meta = tree.get("document_meta");
                if (meta != null) {
                    entity.setDocumentMeta(objectMapper.writeValueAsString(meta));
                }

                // 生成 element_summary
                Map<String, Object> summary = generateElementSummary(root);
                entity.setElementSummary(objectMapper.writeValueAsString(summary));
            }
        } catch (Exception e) {
            log.warn("标准答案元数据提取失败: {}", e.getMessage());
        }

        parseStandardAnswerMapper.insert(entity);
        log.info("标准答案已上传: id={}, answerName={}, templateId={}",
                entity.getId(), answerName, templateId);

        return toVO(entity);
    }

    /**
     * 分页查询标准答案列表
     */
    public Page<ParseStandardAnswerVO> list(Long templateId, String sourceFile, int page, int size) {
        LambdaQueryWrapper<ParseStandardAnswer> wrapper = new LambdaQueryWrapper<>();
        if (templateId != null) {
            wrapper.eq(ParseStandardAnswer::getTemplateId, templateId);
        }
        if (sourceFile != null && !sourceFile.isEmpty()) {
            wrapper.eq(ParseStandardAnswer::getSourceFile, sourceFile);
        }
        wrapper.orderByDesc(ParseStandardAnswer::getCreatedAt);

        Page<ParseStandardAnswer> pageResult = parseStandardAnswerMapper.selectPage(new Page<>(page, size), wrapper);

        Page<ParseStandardAnswerVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 删除标准答案
     */
    public void delete(Long id) {
        ParseStandardAnswer entity = parseStandardAnswerMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("标准答案不存在: " + id);
        }
        parseStandardAnswerMapper.deleteById(id);
        log.info("标准答案已删除: id={}", id);
    }

    /**
     * 获取标准答案的元素列表（用于差异对比）
     */
    public List<ParseElement> getElementsFromStandard(Long standardAnswerId) {
        ParseStandardAnswer answer = parseStandardAnswerMapper.selectById(standardAnswerId);
        if (answer == null) {
            throw new IllegalArgumentException("标准答案不存在: " + standardAnswerId);
        }

        List<ParseElement> elements = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tree = objectMapper.readValue(answer.getStructureTree(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) tree.get("root");
            if (root != null) {
                extractElements(root, null, 0, elements);
            }
        } catch (Exception e) {
            log.warn("标准答案元素提取失败: id={}, error={}", standardAnswerId, e.getMessage());
        }
        return elements;
    }

    @SuppressWarnings("unchecked")
    private void extractElements(Map<String, Object> node, String parentElementId,
                                 int sortOrder, List<ParseElement> result) {
        ParseElement element = new ParseElement();
        element.setElementId((String) node.getOrDefault("id", ""));
        element.setElementType((String) node.getOrDefault("type", ""));
        element.setParentElementId(parentElementId);
        element.setSortOrder(sortOrder);

        Object levelObj = node.get("level");
        if (levelObj instanceof Integer) {
            element.setLevel((Integer) levelObj);
        }

        Object confidenceObj = node.get("confidence");
        if (confidenceObj instanceof Number) {
            element.setConfidence(java.math.BigDecimal.valueOf(((Number) confidenceObj).doubleValue()));
        }

        Map<String, Object> content = (Map<String, Object>) node.get("content");
        if (content != null) {
            Object text = content.get("text");
            if (text instanceof String) {
                element.setContentText((String) text);
            }
        }

        result.add(element);

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                extractElements(children.get(i), element.getElementId(), i, result);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateElementSummary(Map<String, Object> root) {
        java.util.LinkedHashMap<String, Object> summary = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Integer> byType = new java.util.LinkedHashMap<>();
        int[] total = {0};
        double[] confSum = {0};
        double[] confMin = {Double.MAX_VALUE};
        double[] confMax = {Double.MIN_VALUE};

        countRecursive(root, byType, total, confSum, confMin, confMax);

        summary.put("totalElements", total[0]);
        summary.put("byType", byType);
        summary.put("avgConfidence", total[0] > 0 ?
                java.math.BigDecimal.valueOf(confSum[0] / total[0])
                        .setScale(4, java.math.RoundingMode.HALF_UP).doubleValue() : 0);
        summary.put("minConfidence", confMin[0] == Double.MAX_VALUE ? 0 : confMin[0]);
        summary.put("maxConfidence", confMax[0] == Double.MIN_VALUE ? 0 : confMax[0]);

        return summary;
    }

    @SuppressWarnings("unchecked")
    private void countRecursive(Map<String, Object> node,
                                 java.util.Map<String, Integer> byType,
                                 int[] total, double[] confSum,
                                 double[] confMin, double[] confMax) {
        String type = (String) node.get("type");
        if (type != null) {
            byType.merge(type, 1, Integer::sum);
            total[0]++;

            Object confObj = node.get("confidence");
            if (confObj instanceof Number) {
                double conf = ((Number) confObj).doubleValue();
                confSum[0] += conf;
                if (conf < confMin[0]) confMin[0] = conf;
                if (conf > confMax[0]) confMax[0] = conf;
            }
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            for (Map<String, Object> child : children) {
                countRecursive(child, byType, total, confSum, confMin, confMax);
            }
        }
    }

    private ParseStandardAnswerVO toVO(ParseStandardAnswer entity) {
        ParseStandardAnswerVO vo = new ParseStandardAnswerVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setSourceFile(entity.getSourceFile());
        vo.setAnswerName(entity.getAnswerName());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());

        // 解析 elementSummary JSON
        if (entity.getElementSummary() != null) {
            try {
                vo.setElementSummary(objectMapper.readValue(entity.getElementSummary(), Map.class));
            } catch (Exception e) {
                vo.setElementSummary(entity.getElementSummary());
            }
        }

        return vo;
    }
}
