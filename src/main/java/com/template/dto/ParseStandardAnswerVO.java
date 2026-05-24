package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准答案 VO
 */
@Data
public class ParseStandardAnswerVO {
    private Long id;
    private Long templateId;
    private String sourceFile;
    private String answerName;
    private String description;
    private Object elementSummary;
    private LocalDateTime createdAt;
}
