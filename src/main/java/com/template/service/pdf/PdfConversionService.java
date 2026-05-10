package com.template.service.pdf;

public interface PdfConversionService {
    byte[] convertToPdf(byte[] docxContent, String originalFilename) throws PdfConversionException;
}
