package com.template.service.pdf;

import com.template.dto.ParagraphItemDTO;
import com.template.dto.PdfParagraphPosition;
import java.util.List;

public interface PdfConversionService {
    byte[] convertToPdf(byte[] docxContent, String originalFilename) throws PdfConversionException;

    /**
     * 生成 PDF 并同时记录每个段落在 PDF 中的精确字符位置。
     * @param docxContent 原始 docx 字节
     * @param originalFilename 原始文件名
     * @param positions 传入空列表，方法执行后填充段落位置信息
     * @return PDF 字节
     */
    default byte[] convertToPdfWithPositions(byte[] docxContent, String originalFilename,
                                             List<PdfParagraphPosition> positions) throws PdfConversionException {
        // 默认实现：不支持位置记录的转换器（如 LibreOffice）只生成 PDF，不填充位置
        return convertToPdf(docxContent, originalFilename);
    }

    /**
     * 生成 PDF 并根据段落底色信息绘制整行底色（35%透明度）。
     * @param docxContent 原始 docx 字节
     * @param originalFilename 原始文件名（用于日志）
     * @param paragraphs 段落列表，包含 backgroundColor 等信息
     * @return 已绘制底色的 PDF 字节
     * @throws PdfConversionException PDF 转换失败时抛出
     */
    default byte[] convertToPdfWithBackground(byte[] docxContent, String originalFilename,
                                              List<ParagraphItemDTO> paragraphs) throws PdfConversionException {
        // 默认实现：不支持底色的转换器只生成无底色 PDF
        return convertToPdf(docxContent, originalFilename);
    }
}
