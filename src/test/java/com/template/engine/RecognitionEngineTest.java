package com.template.engine;

import com.template.entity.EngineConfig;
import com.template.entity.TemplateRule;
import com.template.service.RecognitionEngine;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RecognitionEngineTest — P0/P1 引擎单元测试
 *
 * 纯 JUnit5 测试，无需 Spring 上下文。
 * 使用 Apache POI 动态构造含不同段落类型的 .docx 文档。
 * 覆盖 TC-037 ~ TC-044（适配重构后的 List<EngineConfig> 接口）。
 */
class RecognitionEngineTest {

    private RecognitionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecognitionEngine();
    }

    // ========== 辅助方法 ==========

    /**
     * 创建一个包含指定段落文本的 XWPFDocument 的 byte[]
     */
    private byte[] createDocxWithParagraphs(String... texts) throws IOException {
        XWPFDocument doc = new XWPFDocument();
        for (String text : texts) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(text);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        doc.close();
        return baos.toByteArray();
    }

    /**
     * 将 byte[] 重新加载为 XWPFDocument
     */
    private XWPFDocument loadDocument(byte[] data) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(data));
    }

    /**
     * 快速创建一条 EngineConfig
     */
    private EngineConfig makeConfig(String name, String pattern, String matchType,
                                     Integer matchLevel, Long ruleId, Integer sortOrder, Integer isActive) {
        EngineConfig cfg = new EngineConfig();
        cfg.setConfigName(name);
        cfg.setPattern(pattern);
        cfg.setMatchType(matchType);
        cfg.setMatchLevel(matchLevel);
        cfg.setRuleId(ruleId);
        cfg.setSortOrder(sortOrder);
        cfg.setIsActive(isActive);
        return cfg;
    }

    private EngineConfig makeConfig(String name, String pattern, String matchType,
                                     Integer sortOrder, Integer isActive) {
        return makeConfig(name, pattern, matchType, null, null, sortOrder, isActive);
    }

    // ========== TC-037 ~ TC-044 ==========

    @Test
    @DisplayName("TC-037: 识别引擎应正确匹配封面、目录、标题、正文段落")
    void testFullRecognition() throws IOException {
        // 准备引擎配置（按 match_type + sort_order 排序的 List）
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("封面匹配", "^摘\\s*要$", "COVER", null, null, 1, 1));
        configs.add(makeConfig("目录匹配", "^目\\s*录$", "TOC", null, null, 1, 1));
        configs.add(makeConfig("一级标题", "^第.+章\\s.*", "TITLE", 1, 1L, 1, 1));
        configs.add(makeConfig("正文匹配", "^[^#@].*", "BODY", null, 2L, 1, 1));

        // 准备规则
        TemplateRule titleRule = new TemplateRule();
        titleRule.setId(1L);
        titleRule.setName("一级标题样式");

        TemplateRule bodyRule = new TemplateRule();
        bodyRule.setId(2L);
        bodyRule.setName("正文");

        List<TemplateRule> rules = new ArrayList<>();
        rules.add(titleRule);
        rules.add(bodyRule);

        // 创建测试文档：封面摘要、目录、标题、正文
        byte[] docxData = createDocxWithParagraphs(
                "摘要",
                "目录",
                "第一章 引言",
                "这是一个正文段落内容。",
                "另一个正文段落。"
        );
        XWPFDocument doc = loadDocument(docxData);

        // 执行识别
        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, rules);

        // 验证结果
        assertThat(matches).hasSize(5);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.COVER);
        assertThat(matches.get(1).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.TOC);
        assertThat(matches.get(2).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.TITLE);
        assertThat(matches.get(2).getMatchedLevel()).isEqualTo(1);
        assertThat(matches.get(2).getRuleId()).isEqualTo(1L);
        assertThat(matches.get(2).getRuleName()).isEqualTo("一级标题样式");
        assertThat(matches.get(3).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.BODY);
        assertThat(matches.get(3).getRuleId()).isEqualTo(2L);
        assertThat(matches.get(4).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.BODY);

        doc.close();
    }

    @Test
    @DisplayName("TC-038: 空段落应被跳过")
    void testEmptyParagraphSkipped() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("正文匹配", ".*", "BODY", 1, 1));

        byte[] docxData = createDocxWithParagraphs("第一段", "", "   ", "第二段");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        // 空段落和空白符段落应被跳过
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getText()).isEqualTo("第一段");
        assertThat(matches.get(1).getText()).isEqualTo("第二段");

        doc.close();
    }

    @Test
    @DisplayName("TC-039: 正则匹配优先级：封面 > 目录 > 标题 > 正文")
    void testMatchPriority() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("封面匹配", "封面", "COVER", null, null, 1, 1));
        configs.add(makeConfig("正文匹配", ".*", "BODY", null, null, 1, 1));
        configs.add(makeConfig("标题匹配", "封面", "TITLE", 2, 10L, 1, 1));

        // 一个段落同时符合封面、标题、正文时，封面优先级最高
        byte[] docxData = createDocxWithParagraphs("封面");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.COVER);

        doc.close();
    }

    @Test
    @DisplayName("TC-040: 标题模式按 match_level 升序匹配，最先匹配成功的生效")
    void testTitlePatternLevelOrder() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("正文匹配", ".*", "BODY", 1, 1));
        // 两个正则都能匹配"第一章 概述"，但 level=1 在前
        configs.add(makeConfig("一级标题", "第.+章", "TITLE", 1, 1L, 2, 1));
        configs.add(makeConfig("二级标题", "第.+章\\s+.*", "TITLE", 2, 2L, 1, 1));

        byte[] docxData = createDocxWithParagraphs("第一章 概述");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.TITLE);
        // 按 match_level 排序，level=1 先匹配（尽管它的 sort_order=2 更大）
        assertThat(matches.get(0).getMatchedLevel()).isEqualTo(1);
        assertThat(matches.get(0).getRuleId()).isEqualTo(1L);

        doc.close();
    }

    @Test
    @DisplayName("TC-041: 非法正则表达式不抛出异常，降级为不匹配")
    void testInvalidRegexDoesNotThrow() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("封面匹配", "[invalid", "COVER", 1, 1));  // 非法正则
        configs.add(makeConfig("正文匹配", ".*", "BODY", 1, 1));

        byte[] docxData = createDocxWithParagraphs("测试内容");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        // 封面非法正则降级，body 匹配成功
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.BODY);

        doc.close();
    }

    @Test
    @DisplayName("TC-042: 停用的配置（is_active=0）不应参与匹配")
    void testInactiveConfigSkipped() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        // 封面匹配但已停用
        configs.add(makeConfig("封面匹配", ".*", "COVER", null, null, 1, 0));
        configs.add(makeConfig("正文匹配", ".*", "BODY", null, null, 1, 1));

        byte[] docxData = createDocxWithParagraphs("第一章 引言");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        // COVER 已停用，降级到 BODY
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.BODY);

        doc.close();
    }

    @Test
    @DisplayName("TC-043: null 或空的正则表达式不匹配任何内容")
    void testNullOrEmptyPattern() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("封面匹配", null, "COVER", 1, 1));
        configs.add(makeConfig("目录匹配", "", "TOC", 1, 1));
        configs.add(makeConfig("正文匹配", ".*", "BODY", 1, 1));

        byte[] docxData = createDocxWithParagraphs("测试内容");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        // null 和空正则不匹配，按 body 正则匹配
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.BODY);

        doc.close();
    }

    @Test
    @DisplayName("TC-044: 超过200字符的段落文本被截断")
    void testTextTruncation() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        configs.add(makeConfig("正文匹配", ".*", "BODY", 1, 1));

        // 创建一个 300 字符的段落
        String longText = "A".repeat(300);
        byte[] docxData = createDocxWithParagraphs(longText);
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getText()).hasSize(200);

        doc.close();
    }

    @Test
    @DisplayName("TC-045: 同类型内多条规则按 sort_order 升序匹配")
    void testSortOrderWithinType() throws IOException {
        List<EngineConfig> configs = new ArrayList<>();
        // TITLE 组：sort_order=2 的虽然在前，但应该按 sort_order 升序，先试 sort_order=1
        configs.add(makeConfig("宽匹配", "^第.*", "TITLE", 1, null, 2, 1));
        configs.add(makeConfig("精确匹配", "^第一章\\s.*", "TITLE", 1, null, 1, 1));
        configs.add(makeConfig("正文匹配", ".*", "BODY", null, null, 1, 1));

        byte[] docxData = createDocxWithParagraphs("第一章 引言");
        XWPFDocument doc = loadDocument(docxData);

        List<RecognitionEngine.ParagraphMatch> matches = engine.recognize(doc, configs, Collections.emptyList());

        // sort_order=1 的"精确匹配"先尝试，匹配成功
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchedType()).isEqualTo(RecognitionEngine.MatchedType.TITLE);
        assertThat(matches.get(0).getRuleId()).isNull(); // 精确匹配没有设 ruleId
    }
}
