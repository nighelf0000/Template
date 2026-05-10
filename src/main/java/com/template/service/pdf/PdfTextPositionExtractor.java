package com.template.service.pdf;

import com.template.dto.PdfParagraphPosition;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 从已生成的 PDF 中提取段落文本位置信息，构建 docx 段落 → PDF 字符位置的映射。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>使用 {@link PdfTextStripperWithIndices} 从 PDF 提取所有字符及全局索引</li>
 *   <li>拼接为 PDF 全文文本并进行标准化（NFC 归一化、合并连续空格）</li>
 *   <li>对每个 docx 段落，在标准化 PDF 文本中执行滑动窗口匹配</li>
 *   <li>将匹配位置映射回原始 PDF 字符索引，填入 {@link PdfParagraphPosition}</li>
 * </ol>
 * <p>
 * 匹配容差：
 * <ul>
 *   <li>文本标准化消除空格/换行符差异</li>
 *   <li>精确匹配失败时自动尝试移除首尾 1-2 字符的模糊匹配</li>
 *   <li>匹配结果做合理性校验（长度偏差超过 20% 降级为估算）</li>
 * </ul>
 */
@Slf4j
public class PdfTextPositionExtractor {

    /**
     * 最大模糊匹配尝试中允许从首尾移除的字符数。
     * 应对 LibreOffice 输出与原始 docx 文本之间的微小差异。
     */
    private static final int MAX_FUZZY_TRIM = 2;

    /**
     * 匹配结果长度偏差阈值。当 |matchLen − paraLen| / paraLen 超过此值时，
     * 认为匹配不可靠，降级为估算。
     */
    private static final double LENGTH_DEVIATION_THRESHOLD = 0.2;

    /**
     * 从已生成的 PDF 字节中提取每个段落在 PDF 文本流中的字符起始/结束位置。
     *
     * @param pdfBytes  已完成转换的 PDF 字节数组
     * @param paragraphs docx 的段落列表（保持文档顺序）
     * @return 段落位置信息列表，按段落索引排序
     * @throws IOException PDF 解析失败时抛出
     */
    public List<PdfParagraphPosition> extractParagraphPositions(
            byte[] pdfBytes, List<XWPFParagraph> paragraphs) throws IOException {

        try (PDDocument pdfDoc = Loader.loadPDF(pdfBytes)) {
            // ── Step 1: 使用 PdfTextStripperWithIndices 提取所有字符 ──
            PdfTextStripperWithIndices stripper = new PdfTextStripperWithIndices();
            stripper.setSortByPosition(true);
            stripper.getText(pdfDoc);
            List<PdfTextStripperWithIndices.CharInfo> allCharInfos = stripper.getCharInfos();

            if (allCharInfos.isEmpty()) {
                log.warn("PDF 中未提取到任何字符，段落位置将全部置零");
                return buildEmptyPositions(paragraphs);
            }

            // ── Step 2: 拼接 PDF 全文并按 NFC 归一化 ──
            StringBuilder rawSb = new StringBuilder(allCharInfos.size());
            for (PdfTextStripperWithIndices.CharInfo ci : allCharInfos) {
                rawSb.append(ci.getCharacter());
            }
            String rawPdfText = rawSb.toString();

            // NFC 归一化 + 合并空格 + 构建索引映射
            NormalizationResult normResult = normalizeAndMap(rawPdfText);
            String pdfNormText = normResult.normalizedText;
            int[] normToRaw = normResult.normToRaw;

            log.debug("PDF 原始文本长度={}, 标准化后长度={}", rawPdfText.length(), pdfNormText.length());

            // ── Step 3: 逐个段落匹配 ──
            List<PdfParagraphPosition> positions = new ArrayList<>(paragraphs.size());
            int lastNormMatchEnd = 0; // 在标准化文本中的搜索起始位置

            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph paragraph = paragraphs.get(i);
                PdfParagraphPosition pos = new PdfParagraphPosition();
                pos.setParagraphIndex(i);

                String paraText = paragraph.getText();
                String paraNormText = normalizeText(paraText);

                if (paraNormText.isEmpty()) {
                    // 空段落：起始=结束，位置沿用上一个匹配的结尾
                    int rawIdx = (lastNormMatchEnd > 0 && lastNormMatchEnd - 1 < normToRaw.length)
                            ? normToRaw[Math.min(lastNormMatchEnd - 1, normToRaw.length - 1)] + 1
                            : 0;
                    pos.setPdfStartPos(rawIdx);
                    pos.setPdfEndPos(rawIdx);
                    log.trace("段落[{}] 空文本, pos={}", i, rawIdx);
                    positions.add(pos);
                    continue;
                }

                // ── Step 4: 在标准化 PDF 文本中搜索段落文本 ──
                MatchResult match = findBestMatch(
                        paraNormText, pdfNormText, normToRaw, lastNormMatchEnd);

                if (match != null) {
                    // 合理性校验：匹配长度与段落文本长度偏差不超过 20%
                    int paraLen = paraText.length();    // 原始段落文本长度（含空格）
                    int matchLen = match.rawEnd - match.rawStart;
                    double deviation = paraLen > 0
                            ? Math.abs((double) matchLen - paraLen) / paraLen
                            : 0;

                    if (deviation <= LENGTH_DEVIATION_THRESHOLD) {
                        pos.setPdfStartPos(match.rawStart);
                        pos.setPdfEndPos(match.rawEnd);
                        lastNormMatchEnd = match.normEnd;
                        log.trace("段落[{}] 精确匹配: raw=[{},{}), norm=[{},{}), dev={}", i,
                                match.rawStart, match.rawEnd, match.normStart, match.normEnd, deviation);
                    } else {
                        // 偏差过大，降级为估算
                        log.warn("段落[{}] 匹配偏差 {} > {}，降级为估算: raw=[{},{})",
                                i, deviation, LENGTH_DEVIATION_THRESHOLD,
                                match.rawStart, match.rawEnd);
                        int fallbackPos = (lastNormMatchEnd > 0
                                && lastNormMatchEnd - 1 < normToRaw.length)
                                ? normToRaw[Math.min(lastNormMatchEnd - 1, normToRaw.length - 1)] + 1
                                : 0;
                        pos.setPdfStartPos(fallbackPos);
                        pos.setPdfEndPos(fallbackPos);
                    }
                } else {
                    // 完全无法匹配，使用上一个匹配结尾作为估算位置
                    log.warn("段落[{}] 未匹配到文本, 使用估算: \"{}\"", i, truncate(paraNormText, 50));
                    int fallbackPos = (lastNormMatchEnd > 0
                            && lastNormMatchEnd - 1 < normToRaw.length)
                            ? normToRaw[Math.min(lastNormMatchEnd - 1, normToRaw.length - 1)] + 1
                            : 0;
                    pos.setPdfStartPos(fallbackPos);
                    pos.setPdfEndPos(fallbackPos);
                }

                positions.add(pos);
            }

            return positions;
        }
    }

    // ======================== 文本标准化 ========================

    /**
     * 对段落文本做标准化：NFC 归一化 → 合并连续空格 → 去除首尾空格。
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        return nfc.replaceAll("\\s+", " ").trim();
    }

    /**
     * 对 PDF 全文做标准化并建立标准化位置 → 原始字符索引的映射。
     * <p>
     * 映射示例：
     * <pre>
     * 原始:  "Hello   World"   (索引 0-12)
     * 标准化: "Hello World"     (索引 0-10)
     * 映射:   [0]=0, [1]=1, ..., [5]=5, [6]=8, [7]=9, ...
     * </pre>
     */
    private NormalizationResult normalizeAndMap(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            NormalizationResult empty = new NormalizationResult();
            empty.normalizedText = "";
            empty.normToRaw = new int[0];
            return empty;
        }

        // 先做 NFC 归一化
        String nfcText = Normalizer.normalize(rawText, Normalizer.Form.NFC);

        StringBuilder norm = new StringBuilder(nfcText.length());
        List<Integer> mappingList = new ArrayList<>();

        boolean prevWasWhitespace = true; // 初始为 true 以过滤前导空格
        for (int i = 0; i < nfcText.length(); i++) {
            char c = nfcText.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!prevWasWhitespace) {
                    norm.append(' ');
                    mappingList.add(i);
                    prevWasWhitespace = true;
                }
                // 连续的空白字符被跳过
            } else {
                norm.append(c);
                mappingList.add(i);
                prevWasWhitespace = false;
            }
        }

        // trim：去除首尾空格
        String normStr = norm.toString();
        int start = 0;
        while (start < normStr.length() && normStr.charAt(start) == ' ') {
            start++;
        }
        int end = normStr.length();
        while (end > start && normStr.charAt(end - 1) == ' ') {
            end--;
        }

        String trimmed = normStr.substring(start, end);
        int[] mapping = new int[trimmed.length()];
        for (int i = 0; i < trimmed.length(); i++) {
            mapping[i] = mappingList.get(start + i);
        }

        NormalizationResult result = new NormalizationResult();
        result.normalizedText = trimmed;
        result.normToRaw = mapping;
        return result;
    }

    // ======================== 文本匹配 ========================

    /**
     * 在标准化 PDF 文本中搜索段落文本的最佳匹配。
     *
     * @param needle      标准化后的段落文本
     * @param haystack    标准化后的 PDF 全文
     * @param normToRaw   标准化位置 → 原始索引映射
     * @param searchStart 在 haystack 中的搜索起始位置
     * @return 匹配结果，找不到返回 null
     */
    private MatchResult findBestMatch(String needle, String haystack,
                                      int[] normToRaw, int searchStart) {
        if (needle.isEmpty() || haystack.isEmpty()) {
            return null;
        }

        // 1. 从搜索起始位置向后精确匹配
        int idx = haystack.indexOf(needle, searchStart);
        if (idx >= 0) {
            return buildMatchResult(idx, needle.length(), normToRaw);
        }

        // 2. 从头搜索（可能在 searchStart 之前找到了，但 searchStart 之后的文本不完整）
        if (searchStart > 0) {
            idx = haystack.indexOf(needle);
            if (idx >= 0) {
                return buildMatchResult(idx, needle.length(), normToRaw);
            }
        }

        // 3. 模糊匹配：从首尾各移除 0~MAX_FUZZY_TRIM 个字符再搜索
        for (int trimStart = 0; trimStart <= Math.min(MAX_FUZZY_TRIM, needle.length() - 1); trimStart++) {
            for (int trimEnd = 0; trimEnd <= Math.min(MAX_FUZZY_TRIM, needle.length() - 1 - trimStart); trimEnd++) {
                if (trimStart == 0 && trimEnd == 0) {
                    continue; // 已在步骤 1/2 尝试过
                }
                String subNeedle = needle.substring(trimStart, needle.length() - trimEnd);
                if (subNeedle.length() < 3) {
                    continue; // 太短的子串匹配不可靠
                }

                // 先尝试从 searchStart 位置向后搜索
                int subIdx = haystack.indexOf(subNeedle, searchStart);
                if (subIdx < 0 && searchStart > 0) {
                    subIdx = haystack.indexOf(subNeedle); // 再从头搜索
                }
                if (subIdx >= 0) {
                    // 估算完整 needle 的起始位置（偏移 trimStart 个字符）
                    int normStart = Math.max(0, subIdx - trimStart);
                    int normEnd = Math.min(haystack.length(), subIdx + subNeedle.length() + trimEnd);
                    // 但如果 trimStart > 0，说明我们跳过了 needle 的开头字符，
                    // 实际匹配起点应该前移 trimStart（模糊容差）
                    return buildMatchResult(normStart, normEnd - normStart, normToRaw);
                }
            }
        }

        // 全部失败
        return null;
    }

    /**
     * 根据标准化文本中的匹配位置构建匹配结果（包含原始索引映射）。
     */
    private MatchResult buildMatchResult(int normStart, int normLen, int[] normToRaw) {
        if (normToRaw.length == 0) {
            return null;
        }
        int normEnd = normStart + normLen;
        int clampedStart = Math.max(0, Math.min(normStart, normToRaw.length - 1));
        int clampedEnd = Math.max(clampedStart, Math.min(normEnd - 1, normToRaw.length - 1));

        int rawStart = normToRaw[clampedStart];
        int rawEnd = normToRaw[clampedEnd] + 1;

        MatchResult result = new MatchResult();
        result.normStart = normStart;
        result.normEnd = normEnd;
        result.rawStart = rawStart;
        result.rawEnd = rawEnd;
        return result;
    }

    // ======================== 工具方法 ========================

    /**
     * 当 PDF 中没有提取到任何字符时，为所有段落生成零值位置。
     */
    private List<PdfParagraphPosition> buildEmptyPositions(List<XWPFParagraph> paragraphs) {
        List<PdfParagraphPosition> positions = new ArrayList<>(paragraphs.size());
        for (int i = 0; i < paragraphs.size(); i++) {
            PdfParagraphPosition pos = new PdfParagraphPosition();
            pos.setParagraphIndex(i);
            pos.setPdfStartPos(0);
            pos.setPdfEndPos(0);
            positions.add(pos);
        }
        return positions;
    }

    /**
     * 截断过长的文本用于日志输出。
     */
    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    // ======================== 内部数据结构 ========================

    /**
     * 文本标准化结果：标准化后的文本 + 标准化位置到原始索引的映射。
     */
    private static class NormalizationResult {
        private String normalizedText;
        private int[] normToRaw;
    }

    /**
     * 匹配结果：在标准化文本和原始文本中的位置范围。
     */
    private static class MatchResult {
        private int normStart;
        private int normEnd;
        private int rawStart;
        private int rawEnd;
    }
}
