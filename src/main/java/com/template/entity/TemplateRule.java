package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("template_rule")
public class TemplateRule {
    @TableId(type = IdType.AUTO)
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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
