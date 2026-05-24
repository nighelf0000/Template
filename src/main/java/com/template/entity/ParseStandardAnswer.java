package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标准答案实体
 */
@Data
@TableName("parse_standard_answer")
public class ParseStandardAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String sourceFile;

    private String answerName;

    private String structureTree;

    private String documentMeta;

    private String elementSummary;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
