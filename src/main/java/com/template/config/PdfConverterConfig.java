package com.template.config;

import com.template.service.pdf.LibreOfficePdfConversionService;
import com.template.service.pdf.PdfConversionService;
import com.template.service.pdf.PoiPdfConversionService;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "template.pdf")
@Data
public class PdfConverterConfig {

    /**
     * PDF 转换器类型：poi（默认）或 libreoffice
     */
    private String converter = "poi";

    /**
     * LibreOffice 相关配置
     */
    private LibreOffice libreoffice = new LibreOffice();

    /**
     * 缓存相关配置
     */
    private Cache cache = new Cache();

    /**
     * 中文字体配置
     */
    private Font font = new Font();

    @Data
    public static class Font {
        private String path;
    }

    @Data
    public static class LibreOffice {
        private String path = "soffice";
        private int timeoutSeconds = 30;
    }

    @Data
    public static class Cache {
        private boolean enabled = true;
        private int ttlMinutes = 5;
    }

    @Bean
    @ConditionalOnProperty(name = "template.pdf.converter", havingValue = "poi", matchIfMissing = true)
    public PdfConversionService pdfConversionService() {
        return new PoiPdfConversionService(font.getPath());
    }

    @Bean
    @ConditionalOnProperty(name = "template.pdf.converter", havingValue = "libreoffice")
    public PdfConversionService pdfConversionServiceLibreOffice() {
        return new LibreOfficePdfConversionService(libreoffice.getPath(), libreoffice.getTimeoutSeconds());
    }
}
