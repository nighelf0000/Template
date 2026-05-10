package com.template.dto;

import lombok.Data;

import java.util.List;

@Data
public class PreviewResultDTO {
    private Long templateId;
    private String templateName;
    private String pdfUrl;              // PDF 预览 URL
    private List<ParagraphItemDTO> paragraphs;
    private List<LegendItemDTO> legend;
}
