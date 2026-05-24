package com.template.dto;

import lombok.Data;

/**
 * Python 引擎保存解析结果的请求体
 * POST /api/parse-record
 */
@Data
public class ParseRecordCreateDTO {
    private Long templateId;
    private Long uploadFileId;
    private String sourceFile;
    private String sourceChecksum;
    private Long fileSize;
    private String engineVersion;
    private String rulesetName;
    private String rulesetVersion;
    private Integer processingTimeMs;
    private String status;
    private String errorMessage;

    // 完整的 StructureTree（JSON 对象，Python 端直接传 dict）
    private Object structureTree;

    // 文档级元数据（JSON 对象）
    private Object documentMeta;

    // 元素统计摘要（JSON 对象）
    private Object elementSummary;

    private String tags;
}
