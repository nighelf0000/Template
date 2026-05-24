package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解析记录完整详情 VO，包含结构树、文档元数据、元素统计
 */
@Data
public class ParseRecordDetailVO {
    private Long id;
    private Long templateId;
    private String templateName;
    private String sourceFile;
    private String status;
    private LocalDateTime parsedAt;
    private String engineVersion;
    private String rulesetName;
    private Integer processingTimeMs;
    private Object structureTree;
    private Object documentMeta;
    private Object elementSummary;
}
