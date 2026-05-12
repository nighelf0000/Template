package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainFileVO {

    private Long id;

    private Long templateId;

    private String templateName;

    private String originalName;

    private Long originalSize;

    private String status;

    private LocalDateTime createdAt;
}
