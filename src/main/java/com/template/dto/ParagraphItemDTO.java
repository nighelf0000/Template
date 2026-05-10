package com.template.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ParagraphItemDTO {
    private int index;
    private String text;
    private String matchedType;      // COVER/TOC/TITLE/BODY/UNKNOWN
    private Integer matchedLevel;
    private Long ruleId;
    private String ruleName;
    private Map<String, Object> style;    // POI原始格式映射的CSS属性
    private String backgroundColor;         // 规则底色
}
