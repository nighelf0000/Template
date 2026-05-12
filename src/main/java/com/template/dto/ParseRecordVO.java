package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParseRecordVO {
    private Long id;
    private Long templateId;
    private String templateName;
    private Long uploadFileId;
    private String sourceFile;
    private String sourceChecksum;
    private Long fileSize;
    private LocalDateTime parsedAt;
    private String engineVersion;
    private String rulesetName;
    private String rulesetVersion;
    private Integer processingTimeMs;
    private String status;
    private String errorMessage;
    private String tags;
    private LocalDateTime createdAt;

    // 元素统计摘要（从 element_summary JSON 解析）
    private Object elementSummary;
}
