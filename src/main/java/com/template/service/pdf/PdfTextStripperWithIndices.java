package com.template.service.pdf;

import lombok.Data;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 扩展 PDFBox 的 {@link PDFTextStripper}，在提取文本的同时记录每个字符的全局索引、所在页码和坐标。
 * 用于 {@link PdfTextPositionExtractor} 从 PDF 中提取字符级位置信息以匹配段落。
 * <p>
 * 使用方式：
 * <pre>{@code
 * PdfTextStripperWithIndices stripper = new PdfTextStripperWithIndices();
 * stripper.setSortByPosition(true);
 * stripper.getText(pdfDocument);
 * List<CharInfo> charInfos = stripper.getCharInfos();
 * }</pre>
 */
public class PdfTextStripperWithIndices extends PDFTextStripper {

    /** 全部提取到的字符信息列表 */
    private final List<CharInfo> charInfos = new ArrayList<>();

    /** 全局字符累计计数器（基于 PDF 中实际提取的字符数） */
    private int globalCharIndex = 0;

    public PdfTextStripperWithIndices() {
        super();
    }


    /**
     * 重写 writeString 以捕获每个字符的 {@link TextPosition} 信息。
     * <p>
     * PDFBox 3.0.1 在 {@link PDFTextStripper#writeString(String, List)}
     * 中会遍历 textPositions 并逐个字符传递给此方法。
     */
    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        for (TextPosition tp : textPositions) {
            String unicode = tp.getUnicode();
            if (unicode == null || unicode.isEmpty()) {
                // 无法映射的字符（如不可见控制字符）跳过
                continue;
            }
            // 一个 TextPosition 可能对应多个 Unicode 码点（如合字 fi → "fi"）
            for (int i = 0; i < unicode.length(); i++) {
                CharInfo ci = new CharInfo();
                ci.setCharIndex(globalCharIndex++);
                ci.setCharacter(unicode.charAt(i));
                ci.setX(tp.getX());
                ci.setY(tp.getY());
                ci.setWidth(tp.getWidth());
                ci.setHeight(tp.getHeight());
                ci.setPageNum(getCurrentPageNo());
                charInfos.add(ci);
            }
        }
    }

    /**
     * 返回当前已提取的全部字符信息（防修改副本）。
     */
    public List<CharInfo> getCharInfos() {
        return new ArrayList<>(charInfos);
    }

    /**
     * 单个字符在 PDF 中的详细信息。
     * <ul>
     *   <li>charIndex — 该字符在整个 PDF 文本流中的全局序号（0-based）</li>
     *   <li>character — 字符本身</li>
     *   <li>x, y — 在 PDF 页面中的坐标（pt）</li>
     *   <li>width, height — 字符的宽度和高度（pt）</li>
     *   <li>pageNum — 所在页码（1-based）</li>
     * </ul>
     */
    @Data
    public static class CharInfo {
        private int charIndex;
        private char character;
        private float x;
        private float y;
        private float width;
        private float height;
        private int pageNum;
    }
}
