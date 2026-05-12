package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("train_file")
public class TrainFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String originalName;

    private Long originalSize;

    private byte[] originalContent;

    private String status;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String templateName;
}
