package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("parse_element")
public class ParseElement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private String elementId;

    private String elementType;

    private Integer level;

    private String contentText;

    private BigDecimal confidence;

    private String parentElementId;

    private Integer sortOrder;

    private String metadata;

    private String styleFeatures;

    private LocalDateTime createdAt;
}
