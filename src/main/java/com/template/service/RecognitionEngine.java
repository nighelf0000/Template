package com.template.service;

import com.template.entity.EngineConfig;
import com.template.entity.TemplateRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RecognitionEngine {

    /**
     * 对Word文档段落执行内容识别，返回段落→规则的映射
     * 接收 List<EngineConfig>（多条正则匹配规则），按 match_type + sort_order 遍历匹配
     */
    public List<ParagraphMatch> recognize(XWPFDocument doc, List<EngineConfig> engineConfigs, List<TemplateRule> rules) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        List<ParagraphMatch> matches = new ArrayList<>();

        // 构建规则ID→规则对象的快速查找
        Map<Long, TemplateRule> ruleMap = new HashMap<>();
        for (TemplateRule rule : rules) {
            ruleMap.put(rule.getId(), rule);
        }

        // 按 match_type 分组并按 sort_order 排序
        Map<String, List<EngineConfig>> configGroups = groupAndSortConfigs(engineConfigs);

        int cumulativeOffset = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            String rawText = paragraph.getText();
            if (rawText == null) {
                rawText = "";
            }
            String displayText = rawText.trim();

            ParagraphMatch match = new ParagraphMatch();
            match.setIndex(i);
            match.setText(displayText.length() > 200 ? displayText.substring(0, 200) : displayText);
            match.setStartOffset(cumulativeOffset);
            match.setEndOffset(cumulativeOffset + rawText.length());
            cumulativeOffset += rawText.length();

            if (displayText.isEmpty()) {
                continue;
            }

            // 匹配优先级：SPECIAL > COVER > TOC > TITLE > BODY
            boolean matched = false;
            for (String type : new String[]{"SPECIAL", "COVER", "TOC", "TITLE", "BODY"}) {
                if (matched) break;
                List<EngineConfig> group = configGroups.get(type);
                if (group == null || group.isEmpty()) continue;

                for (EngineConfig cfg : group) {
                    if (cfg.getIsActive() != null && cfg.getIsActive() == 0) {
                        continue; // 跳过已停用的配置
                    }
                    if (matchPattern(displayText, cfg.getPattern())) {
                        match.setMatchedType(MatchedType.valueOf(type));
                        match.setRuleId(cfg.getRuleId());
                        if ("SPECIAL".equals(type)) {
                            match.setMatchedEngineConfigId(cfg.getId());
                        }
                        if ("TITLE".equals(type) && cfg.getMatchLevel() != null) {
                            match.setMatchedLevel(cfg.getMatchLevel());
                        } else {
                            match.setMatchedLevel(null);
                        }
                        matched = true;
                        break;
                    }
                }
            }

            // 设置对应的规则详情
            if (match.getRuleId() != null && ruleMap.containsKey(match.getRuleId())) {
                TemplateRule rule = ruleMap.get(match.getRuleId());
                match.setRuleName(rule.getName());
            }

            matches.add(match);
        }
        return matches;
    }

    /**
     * 将引擎配置按 match_type 分组，每组内按 sort_order 升序排列
     */
    private Map<String, List<EngineConfig>> groupAndSortConfigs(List<EngineConfig> configs) {
        Map<String, List<EngineConfig>> groups = new LinkedHashMap<>();
        groups.put("SPECIAL", new ArrayList<>());
        groups.put("COVER", new ArrayList<>());
        groups.put("TOC", new ArrayList<>());
        groups.put("TITLE", new ArrayList<>());
        groups.put("BODY", new ArrayList<>());

        if (configs == null) {
            return groups;
        }

        for (EngineConfig cfg : configs) {
            List<EngineConfig> group = groups.get(cfg.getMatchType());
            if (group != null) {
                group.add(cfg);
            }
        }

        // 每组内按 sort_order 升序排列
        // TITLE 组额外按 match_level 升序（同级别内再按 sort_order）
        for (Map.Entry<String, List<EngineConfig>> entry : groups.entrySet()) {
            List<EngineConfig> group = entry.getValue();
            if ("TITLE".equals(entry.getKey())) {
                group.sort(Comparator.comparingInt(
                                (EngineConfig e) -> e.getMatchLevel() != null ? e.getMatchLevel() : 0)
                        .thenComparingInt(e -> e.getSortOrder() != null ? e.getSortOrder() : 0));
            } else {
                group.sort(Comparator.comparingInt(
                        e -> e.getSortOrder() != null ? e.getSortOrder() : 0));
            }
        }

        return groups;
    }

    private boolean matchPattern(String text, String regex) {
        if (regex == null || regex.isEmpty()) {
            return false;
        }
        try {
            return Pattern.compile(regex).matcher(text).find();
        } catch (Exception e) {
            log.warn("正则编译失败: {}", regex, e);
            return false;
        }
    }

    // ---- 内部类 ----

    public enum MatchedType {
        SPECIAL, COVER, TOC, TITLE, BODY, UNKNOWN
    }

    public static class ParagraphMatch {
        private int index;
        private String text;
        private MatchedType matchedType;
        private Integer matchedLevel;
        private Long ruleId;
        private String ruleName;
        private int startOffset;
        private int endOffset;
        private Long matchedEngineConfigId;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public MatchedType getMatchedType() { return matchedType; }
        public void setMatchedType(MatchedType matchedType) { this.matchedType = matchedType; }
        public Integer getMatchedLevel() { return matchedLevel; }
        public void setMatchedLevel(Integer matchedLevel) { this.matchedLevel = matchedLevel; }
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public int getStartOffset() { return startOffset; }
        public void setStartOffset(int startOffset) { this.startOffset = startOffset; }
        public int getEndOffset() { return endOffset; }
        public void setEndOffset(int endOffset) { this.endOffset = endOffset; }
        public Long getMatchedEngineConfigId() { return matchedEngineConfigId; }
        public void setMatchedEngineConfigId(Long matchedEngineConfigId) { this.matchedEngineConfigId = matchedEngineConfigId; }
    }
}
