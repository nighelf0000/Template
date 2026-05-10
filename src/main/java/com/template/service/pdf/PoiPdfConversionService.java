package com.template.service.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.template.dto.ParagraphItemDTO;
import com.template.dto.PdfParagraphPosition;

/**
 * 纯 Java 实现：使用 POI 读取 .docx + PDFBox 逐段绘制 PDF。
 * 支持 A4 分页、基本字体和缩进映射。
 * 由 PdfConverterConfig 按需实例化，不通过组件扫描注册。
 */
@Slf4j
public class PoiPdfConversionService implements PdfConversionService {

    /** A4 页面尺寸（pt） */
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    /** 页边距（pt） */
    private static final float MARGIN_LEFT = 56.7f;   // ~2cm
    private static final float MARGIN_RIGHT = 56.7f;
    private static final float MARGIN_TOP = 56.7f;
    private static final float MARGIN_BOTTOM = 56.7f;

    /** 正文可用宽度 */
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
    /** 正文可用高度 */
    private static final float CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM;

    /** 默认字体大小（pt） */
    private static final float DEFAULT_FONT_SIZE = 11f;
    /** 行高倍数 */
    private static final float LINE_HEIGHT_RATIO = 1.3f;

    /** 绝对降级字体（仅拉丁字符，无中文时保底使用） */
    private static final PDType1Font FALLBACK_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    /** 配置指定的中文字体路径（可为 null） */
    private final String fontPath;

    /** 无参构造（fontPath = null） */
    public PoiPdfConversionService() {
        this.fontPath = null;
    }

    /** 带字体路径的构造 */
    public PoiPdfConversionService(String fontPath) {
        this.fontPath = fontPath;
    }

    @Override
    public byte[] convertToPdf(byte[] docxContent, String originalFilename) throws PdfConversionException {
        return convertToPdfWithPositions(docxContent, originalFilename, null);
    }

    @Override
    public byte[] convertToPdfWithPositions(byte[] docxContent, String originalFilename,
                                            List<PdfParagraphPosition> positions) throws PdfConversionException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(docxContent);
             XWPFDocument doc = new XWPFDocument(bais);
             PDDocument pdfDoc = new PDDocument()) {

            // 尝试加载中文字体（加载失败时降级为 null，后续使用 FALLBACK_FONT）
            PDType0Font cjkFont = loadCjkFont(pdfDoc);
            if (cjkFont == null) {
                log.warn("未找到中文字体，中文文本可能无法正常显示");
            }

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            if (paragraphs.isEmpty()) {
                // 空文档也生成一页空白 PDF
                pdfDoc.addPage(new PDPage(PDRectangle.A4));
            } else {
                renderParagraphs(pdfDoc, paragraphs, cjkFont, positions);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfDoc.save(baos);
            return baos.toByteArray();

        } catch (IOException | IllegalArgumentException e) {
            log.error("POI+PDFBox PDF 转换失败: filename={}", originalFilename, e);
            throw new PdfConversionException("PDF 转换失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] convertToPdfWithBackground(byte[] docxContent, String originalFilename,
                                              List<ParagraphItemDTO> paragraphs) throws PdfConversionException {
        // 构建段落索引 -> 底色映射
        Map<Integer, String> bgColorMap = new HashMap<>();
        if (paragraphs != null) {
            for (ParagraphItemDTO p : paragraphs) {
                String bgColor = p.getBackgroundColor();
                if (bgColor != null && !bgColor.isEmpty()) {
                    bgColorMap.put(p.getIndex(), bgColor);
                }
            }
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(docxContent);
             XWPFDocument doc = new XWPFDocument(bais);
             PDDocument pdfDoc = new PDDocument()) {

            PDType0Font cjkFont = loadCjkFont(pdfDoc);
            if (cjkFont == null) {
                log.warn("未找到中文字体，中文文本可能无法正常显示");
            }

            List<XWPFParagraph> xwpfParagraphs = doc.getParagraphs();
            if (xwpfParagraphs.isEmpty()) {
                pdfDoc.addPage(new PDPage(PDRectangle.A4));
            } else {
                renderParagraphsWithBackground(pdfDoc, xwpfParagraphs, cjkFont, bgColorMap);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfDoc.save(baos);
            return baos.toByteArray();

        } catch (IOException | IllegalArgumentException e) {
            log.error("POI+PDFBox 带底色 PDF 转换失败: filename={}", originalFilename, e);
            throw new PdfConversionException("PDF 转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 遍历所有段落，逐段绘制到 PDF 页面上（带底色支持），自动分页。
     * bgColorMap 为段落索引到底色 CSS 颜色的映射，无底色的段落不绘制背景。
     */
    private void renderParagraphsWithBackground(PDDocument pdfDoc, List<XWPFParagraph> paragraphs,
                                                PDType0Font font, Map<Integer, String> bgColorMap) throws IOException {
        PDPage currentPage = new PDPage(PDRectangle.A4);
        pdfDoc.addPage(currentPage);
        PDPageContentStream[] csHolder = new PDPageContentStream[] {
            new PDPageContentStream(pdfDoc, currentPage, PDPageContentStream.AppendMode.APPEND, true)
        };

        try {
            float cursorY = PAGE_HEIGHT - MARGIN_TOP;

            for (int paraIdx = 0; paraIdx < paragraphs.size(); paraIdx++) {
                XWPFParagraph paragraph = paragraphs.get(paraIdx);
                String text = sanitizeText(paragraph.getText());

                if (text == null || text.trim().isEmpty()) {
                    cursorY -= getLineHeight(DEFAULT_FONT_SIZE);
                    if (needNewPage(cursorY)) {
                        csHolder[0].close();
                        currentPage = new PDPage(PDRectangle.A4);
                        pdfDoc.addPage(currentPage);
                        csHolder[0] = new PDPageContentStream(pdfDoc, currentPage,
                                PDPageContentStream.AppendMode.APPEND, true);
                        cursorY = PAGE_HEIGHT - MARGIN_TOP;
                    }
                    continue;
                }

                float indent = estimateIndent(paragraph);
                float fontSize = extractFontSize(paragraph);
                float lineHeight = getLineHeight(fontSize);
                float paraHeight = calculateParagraphHeight(text, fontSize, lineHeight);

                if (needNewPage(cursorY) || cursorY - paraHeight < MARGIN_BOTTOM) {
                    csHolder[0].close();
                    currentPage = new PDPage(PDRectangle.A4);
                    pdfDoc.addPage(currentPage);
                    csHolder[0] = new PDPageContentStream(pdfDoc, currentPage,
                            PDPageContentStream.AppendMode.APPEND, true);
                    cursorY = PAGE_HEIGHT - MARGIN_TOP;
                }

                float spaceBefore = extractSpaceBefore(paragraph, fontSize);
                cursorY -= spaceBefore;

                String bgColor = bgColorMap.get(paraIdx);
                cursorY = renderText(csHolder, text, fontSize, lineHeight, indent, cursorY, pdfDoc, font, null, bgColor);

                float spaceAfter = extractSpaceAfter(paragraph, fontSize);
                cursorY -= spaceAfter;
            }
        } finally {
            csHolder[0].close();
        }
    }

    /**
     * 遍历所有段落，逐段绘制到 PDF 页面上，自动分页。
     * 保留 3 参数签名以兼容原有调用。
     */
    private void renderParagraphs(PDDocument pdfDoc, List<XWPFParagraph> paragraphs, PDType0Font font) throws IOException {
        renderParagraphs(pdfDoc, paragraphs, font, null);
    }

    /**
     * 遍历所有段落，逐段绘制到 PDF 页面上，自动分页。
     * 当 positions 不为 null 时，同时记录每个段落在 PDF 中的精确字符位置。
     */
    private void renderParagraphs(PDDocument pdfDoc, List<XWPFParagraph> paragraphs, PDType0Font font,
                                  List<PdfParagraphPosition> positions) throws IOException {
        PDPage currentPage = new PDPage(PDRectangle.A4);
        pdfDoc.addPage(currentPage);
        PDPageContentStream[] csHolder = new PDPageContentStream[] {
            new PDPageContentStream(pdfDoc, currentPage, PDPageContentStream.AppendMode.APPEND, true)
        };

        // PDF 累计字符计数器（基于实际写入 PDF 的字符数）
        int totalRenderedChars = 0;

        try {
            // 当前绘制位置（Y 坐标从页面顶部开始递减）
            float cursorY = PAGE_HEIGHT - MARGIN_TOP;

            for (int paraIdx = 0; paraIdx < paragraphs.size(); paraIdx++) {
                XWPFParagraph paragraph = paragraphs.get(paraIdx);
                String text = sanitizeText(paragraph.getText());

                // 记录段落起始位置（即使空段落也记录，方便索引对齐）
                if (positions != null) {
                    PdfParagraphPosition pos = new PdfParagraphPosition();
                    pos.setParagraphIndex(paraIdx);
                    pos.setPdfStartPos(totalRenderedChars);

                    if (text == null || text.trim().isEmpty()) {
                        // 空段落：起始=结束
                        pos.setPdfEndPos(totalRenderedChars);
                        positions.add(pos);
                    } else {
                        positions.add(pos);
                    }
                }

                if (text == null || text.trim().isEmpty()) {
                    // 空段落，留一行空白
                    cursorY -= getLineHeight(DEFAULT_FONT_SIZE);
                    if (needNewPage(cursorY)) {
                        csHolder[0].close();
                        currentPage = new PDPage(PDRectangle.A4);
                        pdfDoc.addPage(currentPage);
                        csHolder[0] = new PDPageContentStream(pdfDoc, currentPage,
                                PDPageContentStream.AppendMode.APPEND, true);
                        cursorY = PAGE_HEIGHT - MARGIN_TOP;
                    }
                    continue;
                }

                // 计算空格之前的缩进（制表符或首行缩进近似）
                float indent = estimateIndent(paragraph);

                // 确定字号（取第一个 Run 的字号，否则默认）
                float fontSize = extractFontSize(paragraph);

                // 行高
                float lineHeight = getLineHeight(fontSize);

                // 计算段落所需总高度
                float paraHeight = calculateParagraphHeight(text, fontSize, lineHeight);

                // 如果当前页剩余空间不足，换页
                if (needNewPage(cursorY) || cursorY - paraHeight < MARGIN_BOTTOM) {
                    csHolder[0].close();
                    currentPage = new PDPage(PDRectangle.A4);
                    pdfDoc.addPage(currentPage);
                    csHolder[0] = new PDPageContentStream(pdfDoc, currentPage,
                            PDPageContentStream.AppendMode.APPEND, true);
                    cursorY = PAGE_HEIGHT - MARGIN_TOP;
                }

                // 换行前段间距
                float spaceBefore = extractSpaceBefore(paragraph, fontSize);
                cursorY -= spaceBefore;

                // 绘制文本（自动换行），同时获取实际写入的字符数
                int[] charCountOut = positions != null ? new int[1] : null;
                cursorY = renderText(csHolder, text, fontSize, lineHeight, indent, cursorY, pdfDoc, font, charCountOut);

                // 更新位置记录的结束字符位置
                if (positions != null && charCountOut != null) {
                    totalRenderedChars += charCountOut[0];
                    // positions 中的最后一条就是当前段落的记录
                    PdfParagraphPosition lastPos = positions.get(positions.size() - 1);
                    lastPos.setPdfEndPos(totalRenderedChars);
                }

                // 换行后段间距
                float spaceAfter = extractSpaceAfter(paragraph, fontSize);
                cursorY -= spaceAfter;
            }
        } finally {
            csHolder[0].close();
        }
    }

    /**
     * 在 PDF 上绘制文本，自动换行。
     * 返回绘制结束后的 cursorY 位置。
     * 当 charCountOut 不为 null 时，将实际写入 PDF 的字符数存入 charCountOut[0]。
     */
    private float renderText(PDPageContentStream[] csHolder, String text, float fontSize,
                             float lineHeight, float indent, float startY,
                             PDDocument pdfDoc, PDType0Font font,
                             int[] charCountOut) throws IOException {
        PDPageContentStream cs = csHolder[0];
        PDFont activeFont = font != null ? font : FALLBACK_FONT;
        cs.beginText();
        cs.setFont(activeFont, fontSize);

        float cursorY = startY;
        float cursorX = MARGIN_LEFT + indent;
        boolean firstOnPage = true;

        // 文本换行处理
        List<String> lines = wrapText(text, fontSize);
        int charsWritten = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // 检查是否需要换页
            if (cursorY - lineHeight < MARGIN_BOTTOM) {
                cs.endText();
                cs.close();

                // 创建新页面和新内容流
                PDPage newPage = new PDPage(PDRectangle.A4);
                pdfDoc.addPage(newPage);
                cs = new PDPageContentStream(pdfDoc, newPage,
                        PDPageContentStream.AppendMode.APPEND, true);
                csHolder[0] = cs;
                cs.beginText();
                cs.setFont(activeFont, fontSize);
                cursorY = PAGE_HEIGHT - MARGIN_TOP;
                cursorX = MARGIN_LEFT;
                firstOnPage = true;
            }

            if (firstOnPage) {
                cs.newLineAtOffset(cursorX, cursorY - lineHeight);
                firstOnPage = false;
            } else {
                cs.newLineAtOffset(0, -lineHeight);
            }
            cs.showText(line);
            charsWritten += line.length();

            cursorX = MARGIN_LEFT;
            cursorY -= lineHeight;
        }

        cs.endText();
        if (charCountOut != null) {
            charCountOut[0] = charsWritten;
        }
        return cursorY;
    }

    /**
     * 在 PDF 上绘制文本（带整行底色支持），自动换行。
     * backgroundColor 为 CSS 颜色值（如 #ff0000 或 hsl(h, s%, l%)），
     * 非 null 时在每行文字前绘制整行底色矩形（35% 透明度）。
     */
    private float renderText(PDPageContentStream[] csHolder, String text, float fontSize,
                             float lineHeight, float indent, float startY,
                             PDDocument pdfDoc, PDType0Font font,
                             int[] charCountOut, String backgroundColor) throws IOException {
        PDPageContentStream cs = csHolder[0];
        PDFont activeFont = font != null ? font : FALLBACK_FONT;
        cs.beginText();
        cs.setFont(activeFont, fontSize);

        float cursorY = startY;
        float cursorX = MARGIN_LEFT + indent;
        boolean firstOnPage = true;

        List<String> lines = wrapText(text, fontSize);
        int charsWritten = 0;
        Color bgColor = (backgroundColor != null && !backgroundColor.isEmpty()) ? parseColor(backgroundColor) : null;

        // textMode 追踪当前是否在 BT/ET 对中
        boolean textMode = true;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // 检查是否需要换页
            if (cursorY - lineHeight < MARGIN_BOTTOM) {
                cs.endText();
                textMode = false;
                cs.close();

                PDPage newPage = new PDPage(PDRectangle.A4);
                pdfDoc.addPage(newPage);
                cs = new PDPageContentStream(pdfDoc, newPage,
                        PDPageContentStream.AppendMode.APPEND, true);
                csHolder[0] = cs;
                cs.beginText();
                cs.setFont(activeFont, fontSize);
                textMode = true;
                cursorY = PAGE_HEIGHT - MARGIN_TOP;
                cursorX = MARGIN_LEFT;
                firstOnPage = true;
            }

            // 绘制底色矩形（必须在 text mode 之外）
            if (bgColor != null) {
                if (textMode) {
                    cs.endText();
                    textMode = false;
                }
                cs.saveGraphicsState();
                setTransparency(cs, 0.35f);
                cs.setNonStrokingColor(bgColor);
                cs.addRect(MARGIN_LEFT, cursorY - lineHeight, CONTENT_WIDTH, lineHeight);
                cs.fill();
                cs.restoreGraphicsState();
            }

            // 进入 text mode 并定位
            if (!textMode) {
                cs.beginText();
                cs.setFont(activeFont, fontSize);
                cs.newLineAtOffset(cursorX, cursorY - lineHeight);
                textMode = true;
                firstOnPage = false;
            } else if (firstOnPage) {
                cs.newLineAtOffset(cursorX, cursorY - lineHeight);
                firstOnPage = false;
            } else {
                cs.newLineAtOffset(0, -lineHeight);
            }

            cs.showText(line);
            charsWritten += line.length();

            cursorX = MARGIN_LEFT;
            cursorY -= lineHeight;
        }

        if (textMode) {
            cs.endText();
        }
        if (charCountOut != null) {
            charCountOut[0] = charsWritten;
        }
        return cursorY;
    }

    /**
     * 将文本按可用宽度换行，返回行列表。
     * CJK 字符按 fontSize 计算宽度，拉丁字符按 fontSize * 0.5 计算。
     */
    private List<String> wrapText(String text, float fontSize) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        // 按现有换行符分段
        String[] rawLines = text.split("\n", -1);
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }
            int start = 0;
            while (start < rawLine.length()) {
                float lineWidth = 0;
                int lastSpace = -1;
                int i = start;

                // 逐字符累计宽度，直到超过可用宽度
                for (; i < rawLine.length(); i++) {
                    char c = rawLine.charAt(i);
                    float charWidth = isCjkChar(c) ? fontSize : fontSize * 0.5f;

                    if (lineWidth + charWidth > CONTENT_WIDTH) {
                        break;
                    }

                    if (c == ' ') {
                        lastSpace = i;
                    }

                    lineWidth += charWidth;
                }

                if (i == start) {
                    // 单个字符即超宽，强制放入一个字符
                    lines.add(rawLine.substring(start, start + 1));
                    start++;
                } else if (i >= rawLine.length()) {
                    // 剩余文本刚好在一行内
                    lines.add(rawLine.substring(start));
                    start = rawLine.length();
                } else if (lastSpace >= start) {
                    // 在空格处断行
                    lines.add(rawLine.substring(start, lastSpace));
                    start = lastSpace + 1; // 跳过空格
                } else {
                    // 无合适空格，在超宽位置截断
                    lines.add(rawLine.substring(start, i));
                    start = i;
                }
            }
        }
        return lines;
    }

    /**
     * 估算首行缩进。
     */
    private float estimateIndent(XWPFParagraph paragraph) {
        int firstLineIndent = paragraph.getIndentationFirstLine();
        if (firstLineIndent > 0) {
            // POI 缩进单位是缇（1/20 pt），转换为 pt
            return firstLineIndent / 20f;
        }
        return 0;
    }

    /**
     * 从段落第一个 Run 提取字号。
     */
    private float extractFontSize(XWPFParagraph paragraph) {
        if (paragraph.getRuns().isEmpty()) {
            return DEFAULT_FONT_SIZE;
        }
        Double fontSize = paragraph.getRuns().get(0).getFontSizeAsDouble();
        return fontSize != null && fontSize > 0 ? fontSize.floatValue() : DEFAULT_FONT_SIZE;
    }

    /**
     * 提取段前间距。
     */
    private float extractSpaceBefore(XWPFParagraph paragraph, float fontSize) {
        int spacingBefore = paragraph.getSpacingBefore();
        if (spacingBefore > 0) {
            return spacingBefore / 20f; // 缇 → pt
        }
        return fontSize * 0.3f; // 默认段前间距
    }

    /**
     * 提取段后间距。
     */
    private float extractSpaceAfter(XWPFParagraph paragraph, float fontSize) {
        int spacingAfter = paragraph.getSpacingAfter();
        if (spacingAfter > 0) {
            return spacingAfter / 20f; // 缇 → pt
        }
        return fontSize * 0.3f; // 默认段后间距
    }

    /**
     * 计算行高。
     */
    private float getLineHeight(float fontSize) {
        return fontSize * LINE_HEIGHT_RATIO;
    }

    /**
     * 估算段落文本所需总高度（CJK 字符宽度感知）。
     */
    private float calculateParagraphHeight(String text, float fontSize, float lineHeight) {
        String[] rawLines = text.split("\n", -1);
        int totalLines = 0;
        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                totalLines++;
            } else {
                float lineWidth = 0;
                for (int i = 0; i < rawLine.length(); i++) {
                    char c = rawLine.charAt(i);
                    float charWidth = isCjkChar(c) ? fontSize : fontSize * 0.5f;
                    if (lineWidth + charWidth > CONTENT_WIDTH && lineWidth > 0) {
                        totalLines++;
                        lineWidth = 0;
                    }
                    lineWidth += charWidth;
                }
                if (lineWidth > 0) {
                    totalLines++;
                }
            }
        }
        return totalLines * lineHeight;
    }

    /**
     * 判断是否需要新建页面（当前 Y 已低于底部边距）。
     */
    private boolean needNewPage(float cursorY) {
        return cursorY < MARGIN_BOTTOM;
    }

    /**
     * 清理文本中的控制字符和字体不支持的符号字符，避免 showText() 报错。
     * 保留：拉丁字符、通用标点、CJK 全系列、全角半角、换行符。
     */
    private String sanitizeText(String text) {
        if (text == null) return null;
        // 1. 替换除 \n 外的所有控制字符为空格
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\n]]", " ");
        // 2. 移除 Unicode 格式字符（零宽连接符、BOM 等）
        cleaned = cleaned.replaceAll("\\p{Cf}", "");
        // 3. 过滤不在字体安全范围内的符号（SIMHEI 仅支持拉丁+CJK+标点）
        cleaned = cleaned.replaceAll("[^\\u0020-\\u024F"
                + "\\u2000-\\u206F"
                + "\\u3000-\\u303F"
                + "\\u3400-\\u4DBF"
                + "\\u4E00-\\u9FFF"
                + "\\uF900-\\uFAFF"
                + "\\uFF00-\\uFFEF"
                + "\n\t]", " ");
        return cleaned;
    }

    // ==================== CJK 字体支持 ====================

    /**
     * 判断字符是否为 CJK（中日韩统一表意文字 / 符号 / 全角字符）。
     */
    private boolean isCjkChar(char c) {
        return (c >= '一' && c <= '鿿')   // CJK 统一表意文字
            || (c >= '　' && c <= '〿')   // CJK 符号和标点
            || (c >= '＀' && c <= '￯');  // 全角形式
    }

    /**
     * 加载中文字体，按优先级降序探测。
     * <ol>
     *   <li>配置路径（fontPath）</li>
     *   <li>系统常见中文字体路径</li>
     *   <li>classpath /fonts/fallback.ttf</li>
     * </ol>
     * 全部失败时返回 {@code null}（调用方使用 FALLBACK_FONT 保底）。
     */
    private PDType0Font loadCjkFont(PDDocument pdfDoc) {
        // 1. 使用配置路径
        if (fontPath != null && !fontPath.isEmpty()) {
            File fontFile = new File(fontPath);
            if (fontFile.exists()) {
                PDType0Font font = tryLoadFontFile(pdfDoc, fontFile);
                if (font != null) {
                    log.info("加载配置中文字体: {}", fontPath);
                    return font;
                }
                log.warn("配置字体不可用: {}", fontPath);
            } else {
                log.warn("配置字体文件不存在: {}", fontPath);
            }
        }

        // 2. 自动探测系统字体（仅 TTF/OTF，PDFBox 3.0.1 无法直接读取 TTC）
        String[] systemPaths = {
            "C:\\Windows\\Fonts\\simhei.ttf",
            "C:\\Windows\\Fonts\\simkai.ttf",
            "C:\\Windows\\Fonts\\simfang.ttf",
            "C:\\Windows\\Fonts\\simsunb.ttf",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf",
            "/System/Library/Fonts/STHeiti Light.ttf",
        };
        for (String path : systemPaths) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                PDType0Font font = tryLoadFontFile(pdfDoc, fontFile);
                if (font != null) {
                    log.info("加载系统中文字体: {}", path);
                    return font;
                }
            }
        }

        // 3. 从 classpath 加载 fallback
        try (InputStream is = getClass().getResourceAsStream("/fonts/fallback.ttf")) {
            if (is != null) {
                PDType0Font font = PDType0Font.load(pdfDoc, is);
                log.info("加载 classpath fallback 字体");
                return font;
            }
        } catch (IOException e) {
            log.warn("classpath fallback 字体加载失败", e);
        }

        return null;
    }

    /**
     * 尝试从文件加载字体。PDFBox 3.0.1 的 {@code TrueTypeCollection.getFontAtIndex()}
     * 为 private，无法直接读取 TTC 集合。此处统一使用 {@code PDType0Font.load()}，
     * TTC 文件会加载失败并自动尝试下一个字体。
     */
    private PDType0Font tryLoadFontFile(PDDocument pdfDoc, File fontFile) {
        try {
            return PDType0Font.load(pdfDoc, fontFile);
        } catch (IOException e) {
            log.warn("字体文件加载失败: {}", fontFile.getAbsolutePath(), e);
            return null;
        }
    }

    // ==================== 底色绘制支持 ====================

    /**
     * 设置 PDFBox 内容流的透明度（填充和描边）。
     */
    private void setTransparency(PDPageContentStream cs, float alpha) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(alpha);
        gs.setStrokingAlphaConstant(alpha);
        cs.setGraphicsStateParameters(gs);
    }

    /**
     * 将 CSS 颜色值（如 #ff0000 或 hsl(h, 60%, 85%)）转换为 AWT Color。
     * 解析失败时返回 null。
     */
    private Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return null;
        try {
            if (colorStr.startsWith("#")) {
                return Color.decode(colorStr);
            } else if (colorStr.startsWith("hsl")) {
                return parseHsl(colorStr);
            }
        } catch (Exception e) {
            log.warn("颜色解析失败: {}", colorStr, e);
        }
        return null;
    }

    /**
     * 解析 hsl(h, s%, l%) 格式的颜色字符串。
     */
    private Color parseHsl(String hslStr) {
        // 移除 "hsl(" 前缀和 ")" 后缀及空格
        String inner = hslStr.substring(4, hslStr.length() - 1).replace(" ", "");
        String[] parts = inner.split(",");
        float h = Float.parseFloat(parts[0]);
        float s = Float.parseFloat(parts[1].replace("%", "")) / 100f;
        float l = Float.parseFloat(parts[2].replace("%", "")) / 100f;
        return hslToRgb(h, s, l);
    }

    /**
     * HSL 转 RGB，返回 AWT Color。
     */
    private Color hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = l - c / 2;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new Color(r + m, g + m, b + m);
    }
}
