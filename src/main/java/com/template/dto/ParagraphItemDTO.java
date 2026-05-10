package com.template.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ParagraphItemDTO {
    private int index;
    private String text;
    private String matchedType;      // COVER/TOC/TITLE/BODY/UNKNOWN
    private Integer matchedLevel;
    private Long ruleId;
    private String ruleName;
    private Map<String, Object> style;    // POI原始格式映射的CSS属性
    private String backgroundColor;         // 规则底色
    private int startOffset;
    private int endOffset;
    /** 该段落在 PDF 中的起始字符位置（由 PDF 生成时精确记录） */
    private int pdfStartPos;
    /** 该段落在 PDF 中的结束字符位置（由 PDF 生成时精确记录） */
    private int pdfEndPos;
    private Long matchedEngineConfigId;
}
