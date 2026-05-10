package com.template.service.pdf;

import com.template.dto.PdfParagraphPosition;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LibreOffice headless 转换实现。
 * 调用 soffice --headless --convert-to pdf 命令。
 * 需要系统安装 LibreOffice 并配置 path。
 * 由 PdfConverterConfig 按需实例化，不通过组件扫描注册。
 *
 * @deprecated 已弃用，统一使用 POI+PDFBox 实现（PoiPdfConversionService）。
 *             保留代码仅为向后兼容参考，不再注册为 Spring Bean。
 */
@Deprecated
@Slf4j
public class LibreOfficePdfConversionService implements PdfConversionService {

    private final String libreOfficePath;
    private final int timeoutSeconds;

    public LibreOfficePdfConversionService(String libreOfficePath, int timeoutSeconds) {
        this.libreOfficePath = libreOfficePath;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public byte[] convertToPdf(byte[] docxContent, String originalFilename) throws PdfConversionException {
        Path tempDocx = null;
        Path tempOutputDir = null;
        Path tempPdf = null;

        try {
            // 创建临时文件
            String baseName = originalFilename != null
                    ? originalFilename.replaceAll("\\.[^.]+$", "")
                    : "document";
            tempDocx = Files.createTempFile("pdfconv_", ".docx");
            Files.write(tempDocx, docxContent);

            // 创建临时输出目录
            tempOutputDir = Files.createTempDirectory("pdfconv_out_");

            // 构造 soffice 命令
            ProcessBuilder pb = new ProcessBuilder(
                    libreOfficePath,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", tempOutputDir.toAbsolutePath().toString(),
                    tempDocx.toAbsolutePath().toString()
            );

            log.debug("执行 LibreOffice 转换: {}", pb.command());

            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new PdfConversionException("LibreOffice 转换超时（" + timeoutSeconds + "秒）");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // 读取错误输出
                String errorOutput = readStream(process.getErrorStream());
                log.error("LibreOffice 转换失败, exitCode={}, stderr={}", exitCode, errorOutput);
                throw new PdfConversionException("LibreOffice 转换失败，退出码: " + exitCode);
            }

            // 查找生成的 PDF
            String pdfFileName = baseName + ".pdf";
            tempPdf = tempOutputDir.resolve(pdfFileName);
            if (!Files.exists(tempPdf)) {
                // LibreOffice 可能以输入文件名生成 PDF，尝试同名文件
                String docxFileName = tempDocx.getFileName().toString();
                String expectedPdfName = docxFileName.replaceAll("\\.[^.]+$", "") + ".pdf";
                tempPdf = tempOutputDir.resolve(expectedPdfName);
                if (!Files.exists(tempPdf)) {
                    // 最后尝试：列表输出目录下的第一个 PDF
                    File[] pdfFiles = tempOutputDir.toFile().listFiles((dir, name) -> name.endsWith(".pdf"));
                    if (pdfFiles != null && pdfFiles.length > 0) {
                        tempPdf = pdfFiles[0].toPath();
                    } else {
                        throw new PdfConversionException("LibreOffice 转换后未找到 PDF 输出文件");
                    }
                }
            }

            // 读取生成的 PDF
            byte[] pdfBytes = Files.readAllBytes(tempPdf);
            if (pdfBytes.length == 0) {
                throw new PdfConversionException("LibreOffice 生成的 PDF 文件为空");
            }
            log.info("LibreOffice PDF 转换成功: size={} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (IOException e) {
            log.error("LibreOffice 转换 I/O 异常: filename={}", originalFilename, e);
            throw new PdfConversionException("LibreOffice 转换 I/O 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfConversionException("LibreOffice 转换被中断", e);
        } finally {
            // 清理临时文件
            cleanupTempFile(tempDocx);
            cleanupTempDir(tempOutputDir);
        }
    }

    @Override
    public byte[] convertToPdfWithPositions(byte[] docxContent, String originalFilename,
                                            List<PdfParagraphPosition> positions) throws PdfConversionException {
        // Step 1: 使用 LibreOffice 生成完整 PDF（含图片/公式/表格）
        byte[] pdfBytes = convertToPdf(docxContent, originalFilename);
        // Step 2: 从 docx 获取段落列表，使用 PDFBox 从 PDF 中提取段落位置
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxContent))) {
            PdfTextPositionExtractor extractor = new PdfTextPositionExtractor();
            List<PdfParagraphPosition> extracted = extractor.extractParagraphPositions(pdfBytes, doc.getParagraphs());
            positions.addAll(extracted);
            log.info("PDF 段落位置提取完成: 共 {} 个段落", extracted.size());
        } catch (IOException e) {
            log.error("提取 PDF 段落位置失败: filename={}", originalFilename, e);
            throw new PdfConversionException("提取 PDF 段落位置失败: " + e.getMessage(), e);
        }
        return pdfBytes;
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    private void cleanupTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("清理临时文件失败: {}", path, e);
            }
        }
    }

    private void cleanupTempDir(Path path) {
        if (path != null) {
            try {
                Files.walk(path)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("清理临时目录失败: {}", p, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("清理临时目录失败: {}", path, e);
            }
        }
    }
}
