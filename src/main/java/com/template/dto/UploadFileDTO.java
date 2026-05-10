package com.template.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UploadFileDTO {
    private Long id;
    private Long templateId;
    private String originalName;
    private Long originalSize;
    private String status;
    private LocalDateTime createdAt;
}
