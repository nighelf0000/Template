package com.template.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 元素类型统计 VO
 */
@Data
@AllArgsConstructor
public class ElementTypeCountVO {
    private String elementType;
    private int count;
}
