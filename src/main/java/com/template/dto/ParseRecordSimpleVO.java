package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParseRecordSimpleVO {

    private Long id;

    private String sourceFile;

    private String status;

    private LocalDateTime parsedAt;
}
