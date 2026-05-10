package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("smart_match_rule")
public class SmartMatchRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long taskId;
    private String ruleName;
    private String matchType;
    private Integer matchLevel;
    private String keywords;
    private String featureVector;
    private Long styleRuleId;
    private BigDecimal threshold;
    private Integer isActive;
    private Integer matchOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
