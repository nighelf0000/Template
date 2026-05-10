package com.template.dto;

import lombok.Data;

@Data
public class EngineConfigDTO {
    private Long id;
    private Long templateId;
    private String configName;
    private String pattern;
    private String matchType;
    private Integer matchLevel;
    private Long ruleId;
    private String ruleName;        // 关联规则名称（只读，从 template_rule 联表查询）
    private Integer sortOrder;
    private Integer isActive;
}
