package com.template.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.DiffChangeVO;
import com.template.dto.ParseDiffCompareRequest;
import com.template.dto.ParseDiffReportVO;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseDiffReportService {

    private final ParseDiffReportMapper parseDiffReportMapper;
    private final ParseElementMapper parseElementMapper;
    private final ParseRecordMapper parseRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 任务间差异对比
     */
    @Transactional
    public ParseDiffReportVO compareTask(ParseDiffCompareRequest request) {
        Long recordIdA = request.getRecordIdA();
        Long recordIdB = request.getRecordIdB();

        // 校验记录存在
        ParseRecord recordA = parseRecordMapper.selectById(recordIdA);
        ParseRecord recordB = parseRecordMapper.selectById(recordIdB);
        if (recordA == null || recordB == null) {
            throw new IllegalArgumentException("解析记录不存在");
        }

        // 校验同一模板
        if (!Objects.equals(recordA.getTemplateId(), recordB.getTemplateId())) {
            throw new IllegalArgumentException("两条记录必须属于同一模板");
        }

        // 获取元素列表
        List<ParseElement> elementsA = listElements(recordIdA);
        List<ParseElement> elementsB = listElements(recordIdB);

        // 执行对比
        return doCompare(elementsA, elementsB, "TASK", recordIdA, recordIdB, null);
    }

    /**
     * 与标准答案对比
     */
    @Transactional
    public ParseDiffReportVO compareStandard(Long recordId, Long standardAnswerId,
                                              List<ParseElement> standardElements) {
        List<ParseElement> elementsA = listElements(recordId);
        return doCompare(elementsA, standardElements, "STANDARD", recordId, null, standardAnswerId);
    }

    /**
     * 核心对比算法：基于 elementId 的元素级对齐对比
     */
    private ParseDiffReportVO doCompare(List<ParseElement> elementsA, List<ParseElement> elementsB,
                                         String diffType, Long recordIdA, Long recordIdB,
                                         Long standardAnswerId) {
        // 建立元素索引
        Map<String, ParseElement> mapA = elementsA.stream()
                .collect(Collectors.toMap(ParseElement::getElementId, e -> e, (a, b) -> a));
        Map<String, ParseElement> mapB = elementsB.stream()
                .collect(Collectors.toMap(ParseElement::getElementId, e -> e, (a, b) -> a));

        List<DiffChangeVO> changes = new ArrayList<>();
        int unchanged = 0, changed = 0, removed = 0, added = 0;

        // 遍历 MapA：unchanged / changed / removed
        for (Map.Entry<String, ParseElement> entry : mapA.entrySet()) {
            String elementId = entry.getKey();
            ParseElement elemA = entry.getValue();
            ParseElement elemB = mapB.get(elementId);

            if (elemB == null) {
                // 记录B中不存在 -> removed
                DiffChangeVO vo = new DiffChangeVO();
                vo.setChangeType("removed");
                vo.setElementId(elementId);
                vo.setElementType(elemA.getElementType());
                vo.setContentText(elemA.getContentText());
                vo.setConfidence(elemA.getConfidence());
                changes.add(vo);
                removed++;
            } else {
                // 都存在 -> 对比差异
                String typeA = elemA.getElementType();
                String typeB = elemB.getElementType();
                String contentA = elemA.getContentText() != null ? elemA.getContentText() : "";
                String contentB = elemB.getContentText() != null ? elemB.getContentText() : "";
                BigDecimal confA = elemA.getConfidence() != null ? elemA.getConfidence() : BigDecimal.ZERO;
                BigDecimal confB = elemB.getConfidence() != null ? elemB.getConfidence() : BigDecimal.ZERO;

                if (!typeA.equals(typeB)) {
                    // 类型变化
                    DiffChangeVO vo = new DiffChangeVO();
                    vo.setChangeType("type_changed");
                    vo.setElementId(elementId);
                    vo.setElementTypeA(typeA);
                    vo.setElementTypeB(typeB);
                    vo.setContentText(contentA.isEmpty() ? contentB : contentA);
                    vo.setConfidenceA(confA);
                    vo.setConfidenceB(confB);
                    vo.setLevelA(elemA.getLevel());
                    vo.setLevelB(elemB.getLevel());
                    changes.add(vo);
                    changed++;
                } else if (!contentA.equals(contentB) || confA.compareTo(confB) != 0) {
                    // 内容或置信度变化
                    DiffChangeVO vo = new DiffChangeVO();
                    vo.setChangeType("changed");
                    vo.setElementId(elementId);
                    vo.setElementType(typeA);
                    vo.setContentA(contentA);
                    vo.setContentB(contentB);
                    vo.setConfidenceA(confA);
                    vo.setConfidenceB(confB);
                    changes.add(vo);
                    changed++;
                } else {
                    unchanged++;
                }
            }
        }

        // 遍历 MapB：added
        for (Map.Entry<String, ParseElement> entry : mapB.entrySet()) {
            String elementId = entry.getKey();
            if (!mapA.containsKey(elementId)) {
                ParseElement elemB = entry.getValue();
                DiffChangeVO vo = new DiffChangeVO();
                vo.setChangeType("added");
                vo.setElementId(elementId);
                vo.setElementType(elemB.getElementType());
                vo.setContentText(elemB.getContentText());
                vo.setConfidence(elemB.getConfidence());
                changes.add(vo);
                added++;
            }
        }

        int totalA = elementsA.size();
        int totalB = elementsB.size();

        // 计算统计指标
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalA", totalA);
        summary.put("totalB", totalB);
        summary.put("added", added);
        summary.put("removed", removed);
        summary.put("changed", changed);
        summary.put("unchanged", unchanged);

        // 准确率 = unchanged / max(totalA, totalB)
        int maxTotal = Math.max(totalA, totalB);
        if (maxTotal > 0) {
            BigDecimal accuracy = BigDecimal.valueOf(unchanged)
                    .divide(BigDecimal.valueOf(maxTotal), 4, RoundingMode.HALF_UP);
            summary.put("accuracy", accuracy.doubleValue());
        } else {
            summary.put("accuracy", 1.0);
        }

        // 召回率 = unchanged / totalA (原始记录的元素覆盖率)
        if (totalA > 0) {
            BigDecimal recall = BigDecimal.valueOf(unchanged)
                    .divide(BigDecimal.valueOf(totalA), 4, RoundingMode.HALF_UP);
            summary.put("recall", recall.doubleValue());
        } else {
            summary.put("recall", 1.0);
        }

        // F1 Score
        double accuracy = (double) summary.get("accuracy");
        double recall = (double) summary.get("recall");
        if (accuracy + recall > 0) {
            double f1 = 2 * accuracy * recall / (accuracy + recall);
            summary.put("f1Score", BigDecimal.valueOf(f1).setScale(4, RoundingMode.HALF_UP).doubleValue());
        } else {
            summary.put("f1Score", 0.0);
        }

        // 类型分布统计
        Map<String, Integer> typeDistA = countByType(elementsA);
        Map<String, Integer> typeDistB = countByType(elementsB);

        Map<String, Object> statisticsDiff = new LinkedHashMap<>();
        statisticsDiff.put("typeDistributionA", typeDistA);
        statisticsDiff.put("typeDistributionB", typeDistB);

        // 平均置信度
        statisticsDiff.put("avgConfidenceA", avgConfidence(elementsA));
        statisticsDiff.put("avgConfidenceB", avgConfidence(elementsB));

        // 保存报告
        ParseDiffReport report = new ParseDiffReport();
        report.setDiffType(diffType);
        report.setRecordIdA(recordIdA);
        report.setRecordIdB(recordIdB);
        report.setStandardAnswerId(standardAnswerId);
        try {
            report.setSummary(objectMapper.writeValueAsString(summary));
            report.setStructuralChanges(objectMapper.writeValueAsString(changes));
            report.setStatisticsDiff(objectMapper.writeValueAsString(statisticsDiff));
        } catch (Exception e) {
            log.warn("JSON 序列化失败", e);
        }
        parseDiffReportMapper.insert(report);

        // 构建 VO
        return toVO(report, changes, summary, statisticsDiff);
    }

    /**
     * 获取报告详情
     */
    public ParseDiffReportVO getReport(Long id) {
        ParseDiffReport report = parseDiffReportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报告不存在: " + id);
        }
        return toVO(report, null, null, null);
    }

    /**
     * 删除报告
     */
    public void deleteReport(Long id) {
        ParseDiffReport report = parseDiffReportMapper.selectById(id);
        if (report == null) {
            throw new IllegalArgumentException("报告不存在: " + id);
        }
        parseDiffReportMapper.deleteById(id);
        log.info("差异报告已删除: id={}", id);
    }

    private List<ParseElement> listElements(Long recordId) {
        return parseElementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParseElement>()
                        .eq(ParseElement::getRecordId, recordId)
                        .orderByAsc(ParseElement::getSortOrder)
        );
    }

    private Map<String, Integer> countByType(List<ParseElement> elements) {
        return elements.stream()
                .collect(Collectors.groupingBy(
                        ParseElement::getElementType,
                        LinkedHashMap::new,
                        Collectors.summingInt(e -> 1)
                ));
    }

    private double avgConfidence(List<ParseElement> elements) {
        if (elements.isEmpty()) return 0.0;
        return elements.stream()
                .filter(e -> e.getConfidence() != null)
                .mapToDouble(e -> e.getConfidence().doubleValue())
                .average()
                .orElse(0.0);
    }

    @SuppressWarnings("unchecked")
    private ParseDiffReportVO toVO(ParseDiffReport report,
                                    List<DiffChangeVO> changes,
                                    Map<String, Object> summary,
                                    Map<String, Object> statisticsDiff) {
        ParseDiffReportVO vo = new ParseDiffReportVO();
        vo.setId(report.getId());
        vo.setDiffType(report.getDiffType());
        vo.setCreatedAt(report.getCreatedAt());

        // 解析 summary JSON
        if (summary != null) {
            vo.setSummary(summary);
        } else if (report.getSummary() != null) {
            try {
                vo.setSummary(objectMapper.readValue(report.getSummary(), Map.class));
            } catch (Exception e) {
                vo.setSummary(report.getSummary());
            }
        }

        // 解析 structuralChanges JSON
        if (changes != null) {
            vo.setStructuralChanges(changes);
        } else if (report.getStructuralChanges() != null) {
            try {
                vo.setStructuralChanges(objectMapper.readValue(
                        report.getStructuralChanges(), new TypeReference<List<DiffChangeVO>>() {}));
            } catch (Exception e) {
                vo.setStructuralChanges(new ArrayList<>());
            }
        }

        // 解析 statisticsDiff JSON
        if (statisticsDiff != null) {
            vo.setStatisticsDiff(statisticsDiff);
        } else if (report.getStatisticsDiff() != null) {
            try {
                vo.setStatisticsDiff(objectMapper.readValue(report.getStatisticsDiff(), Map.class));
            } catch (Exception e) {
                vo.setStatisticsDiff(report.getStatisticsDiff());
            }
        }

        return vo;
    }
}
