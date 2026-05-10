package com.template.dto;

import lombok.Data;
import java.util.List;

@Data
public class TemplateConfigDTO {
    private Long id;
    private String name;
    private Integer isActive;
    private List<TemplateRuleDTO> rules;
    private List<EngineConfigDTO> engineConfigs;
}
