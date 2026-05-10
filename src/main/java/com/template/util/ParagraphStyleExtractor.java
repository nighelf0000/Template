package com.template.util;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 POI XWPFParagraph 提取原始样式，映射为 CSS 属性 Map。
 */
public class ParagraphStyleExtractor {

    private ParagraphStyleExtractor() {}

    /**
     * 提取段落原始样式。
     * 策略：取第一个非空 Run 作为"代表 Run"，null/默认值不放入 Map。
     */
    public static Map<String, Object> extract(XWPFParagraph paragraph) {
        Map<String, Object> style = new LinkedHashMap<>();

        if (paragraph == null) {
            return style;
        }

        // 取第一个非空 Run
        XWPFRun run = findFirstNonEmptyRun(paragraph);
        if (run != null) {
            // 字体名称
            String fontFamily = run.getFontFamily();
            if (fontFamily != null && !fontFamily.isEmpty()) {
                style.put("fontName", fontFamily);
            }

            // 字号（未显式设置时 getFontSizeAsDouble() 返回 null）
            Double fontSize = run.getFontSizeAsDouble();
            if (fontSize != null && fontSize > 0) {
                style.put("fontSize", fontSize.intValue() + "pt");
            }

            // 加粗
            if (run.isBold()) {
                style.put("fontBold", true);
            }

            // 斜体
            if (run.isItalic()) {
                style.put("fontItalic", true);
            }

            // 下划线
            if (run.getUnderline() != UnderlinePatterns.NONE) {
                style.put("fontUnderline", true);
            }

            // 字体颜色
            String color = run.getColor();
            if (color != null && !color.isEmpty()) {
                style.put("fontColor", "#" + color);
            }

            // 删除线
            if (run.isStrikeThrough()) {
                style.put("fontStrike", true);
            }
        }

        // 段落对齐
        ParagraphAlignment alignment = paragraph.getAlignment();
        if (alignment != null) {
            style.put("textAlign", alignment.name());
        }

        // 首行缩进（缇 → em，1/240 英寸）
        int firstLineIndent = paragraph.getIndentationFirstLine();
        if (firstLineIndent > 0) {
            double indentEm = firstLineIndent / 240.0;
            style.put("textIndent", String.format("%.2fem", indentEm));
        }

        // 行距（缇 → 倍数）
        double spacingBetween = paragraph.getSpacingBetween();
        if (spacingBetween > 0) {
            double lineHeight = spacingBetween / 240.0;
            style.put("lineHeight", String.format("%.2f", lineHeight));
        }

        // 段前间距（缇 → pt）
        int spacingBefore = paragraph.getSpacingBefore();
        if (spacingBefore > 0) {
            double pt = spacingBefore / 20.0;
            style.put("spaceBefore", String.format("%.1fpt", pt));
        }

        // 段后间距（缇 → pt）
        int spacingAfter = paragraph.getSpacingAfter();
        if (spacingAfter > 0) {
            double pt = spacingAfter / 20.0;
            style.put("spaceAfter", String.format("%.1fpt", pt));
        }

        return style;
    }

    /**
     * 找到段落中第一个包含非空文本内容的 Run。
     */
    private static XWPFRun findFirstNonEmptyRun(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null && !text.trim().isEmpty()) {
                return run;
            }
        }
        // 没有非空 run，返回第一个（如果有）
        if (!paragraph.getRuns().isEmpty()) {
            return paragraph.getRuns().get(0);
        }
        return null;
    }
}
