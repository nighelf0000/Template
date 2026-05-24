package com.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("parse_diff_report")
public class ParseDiffReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String diffType;

    private Long recordIdA;

    private Long recordIdB;

    private Long standardAnswerId;

    private String summary;

    private String structuralChanges;

    private String statisticsDiff;

    private LocalDateTime createdAt;
}
