package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TrainTaskVO {

    private Long id;

    private Long templateId;

    private String templateName;

    private String taskName;

    private String status;

    private Integer progress;

    private Integer totalFiles;

    private Integer fileCount;

    private Integer parseRecordCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<ParseRecordSimpleVO> parseRecords;
}
