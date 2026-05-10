package com.template.config;

import com.template.service.pdf.PdfConversionService;
import com.template.service.pdf.PoiPdfConversionService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "template.pdf")
@Data
public class PdfConverterConfig {

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
    public static class Cache {
        private boolean enabled = true;
        private int ttlMinutes = 5;
    }

    @Bean
    public PdfConversionService pdfConversionService() {
        return new PoiPdfConversionService(font.getPath());
    }
}
