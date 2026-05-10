package com.template.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TemplateRuleDTO {
    private Long id;
    private Long templateId;
    private String name;
    private String fontName;
    private Integer fontSize;
    private Integer fontBold;
    private Integer fontItalic;
    private Integer fontUnderline;
    private String fontColor;
    private Integer fontStrike;
    private String textAlign;
    private BigDecimal textIndent;
    private BigDecimal lineSpacing;
    private BigDecimal spaceBefore;
    private BigDecimal spaceAfter;
    private String highlightColor;
}
