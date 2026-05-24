package com.template.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 差异对比报告 VO
 */
@Data
public class ParseDiffReportVO {
    private Long id;
    private String diffType;
    private Object summary;
    private List<DiffChangeVO> structuralChanges;
    private Object statisticsDiff;
    private LocalDateTime createdAt;
}
