package com.template.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SmartMatchRuleDTO {
    private Long id;
    private Long templateId;
    private Long taskId;
    private String ruleName;
    private String matchType;
    private Integer matchLevel;
    private String keywords;
    private Long styleRuleId;
    private BigDecimal threshold;
    private Integer isActive;
    private Integer matchOrder;
}
