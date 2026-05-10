package com.template.dto;

import lombok.Data;

/**
 * PDF 生成时记录的段落字符位置信息。
 * 在 PoiPdfConversionService 逐段落生成 PDF 文本时，
 * 累计记录每个段落处理前后在 PDF 中的精确字符位置。
 * 前端直接使用此位置渲染高亮，不再依赖比例估算。
 */
@Data
public class PdfParagraphPosition {
    /** 段落索引（对应 docx 中的段落序号） */
    private int paragraphIndex;

    /** 该段落在 PDF 中的起始字符位置（基于处理后累计字符数） */
    private int pdfStartPos;

    /** 该段落在 PDF 中的结束字符位置（基于处理后累计字符数） */
    private int pdfEndPos;
}
