package com.template.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 差异变化项 VO
 */
@Data
public class DiffChangeVO {
    private String changeType; // added / removed / changed / type_changed / unchanged
    private String elementId;
    private String elementType;
    private String elementTypeA;
    private String elementTypeB;
    private String contentText;
    private String contentA;
    private String contentB;
    private BigDecimal confidence;
    private BigDecimal confidenceA;
    private BigDecimal confidenceB;
    private Integer levelA;
    private Integer levelB;
}
