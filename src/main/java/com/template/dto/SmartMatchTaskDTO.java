package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SmartMatchTaskDTO {
    private Long id;
    private Long templateId;
    private String taskName;
    private String status;
    private Integer progress;
    private Integer fileCount;
    private String errorMessage;
    private Integer ruleCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
