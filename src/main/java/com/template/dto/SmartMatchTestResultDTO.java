package com.template.dto;

import lombok.Data;

@Data
public class SmartMatchTestResultDTO {
    private int paragraphIndex;
    private String text;
    private String matchedType;
    private Integer matchLevel;
    private Long styleRuleId;
    private String ruleName;
    private double confidence;
}
