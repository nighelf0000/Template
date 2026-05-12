package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("parse_record")
public class ParseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private Long uploadFileId;

    private String sourceFile;

    private String sourceChecksum;

    private Long fileSize;

    private LocalDateTime parsedAt;

    private String engineVersion;

    private String rulesetName;

    private String rulesetVersion;

    private Integer processingTimeMs;

    private String status;

    private String errorMessage;

    private String structureTree;

    private String documentMeta;

    private String elementSummary;

    private String tags;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String templateName;
}
