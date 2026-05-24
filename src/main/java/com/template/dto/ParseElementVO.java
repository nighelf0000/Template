package com.template.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 解析元素 VO，用于前端展示元素明细
 */
@Data
public class ParseElementVO {
    private Long id;
    private Long recordId;
    private String elementId;
    private String elementType;
    private Integer level;
    private String contentText;
    private BigDecimal confidence;
    private String parentElementId;
    private Integer sortOrder;
    private Object metadata;
    private String styleFeatures;
}
