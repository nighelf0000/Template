package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.template.dto.ParseRecordSimpleVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("train_task")
public class TrainTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    private String taskName;

    private String status;

    private Integer progress;

    private Integer totalFiles;

    private Integer fileCount;

    private Integer parseRecordCount;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String templateName;

    @TableField(exist = false)
    private List<ParseRecordSimpleVO> parseRecords;
}
