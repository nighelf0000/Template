package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("smart_match_task")
public class SmartMatchTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String taskName;
    private String status;
    private Integer progress;
    private Integer fileCount;
    private String errorMessage;
    private Integer ruleCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
