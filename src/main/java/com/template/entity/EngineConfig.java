package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("template_engine_config")
public class EngineConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String configName;
    private String pattern;
    private String matchType;
    private Integer matchLevel;
    private Long ruleId;
    private Integer sortOrder;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
