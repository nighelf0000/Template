package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_upload_file")
public class UploadFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;

    @TableField(exist = false)
    private String templateName;

    private String originalName;
    private Long originalSize;
    private byte[] originalContent;
    private byte[] parsedContent;
    private String parsedJson;
    private LocalDateTime parsedAt;
    private LocalDateTime adjustedAt;
    private byte[] outputContent;
    private String outputName;
    private LocalDateTime exportedAt;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
