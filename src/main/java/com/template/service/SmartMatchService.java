package com.template.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.SmartMatchRuleDTO;
import com.template.dto.SmartMatchTestResultDTO;
import com.template.entity.SmartMatchRule;
import com.template.entity.SmartMatchTask;
import com.template.entity.TemplateRule;
import com.template.mapper.SmartMatchRuleMapper;
import com.template.mapper.SmartMatchTaskMapper;
import com.template.mapper.TemplateRuleMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartMatchService {

    private final SmartMatchTaskMapper smartMatchTaskMapper;
    private final SmartMatchRuleMapper smartMatchRuleMapper;
    private final TemplateRuleMapper templateRuleMapper;
    private final SmartMatchTaskService smartMatchTaskService;

    @Value("${template.smart-match.async:true}")
    private boolean asyncEnabled;

    /**
     * 服务启动时重置中间状态（PENDING/RUNNING）的训练任务为 FAILED。
     * 避免因服务重启导致任务永远卡在中间状态。
     */
    @PostConstruct
    public void initResetPendingTasks() {
        log.info("服务启动 - 检查并重置中间状态的训练任务");

        LambdaQueryWrapper<SmartMatchTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SmartMatchTask::getStatus, "PENDING", "RUNNING");

        List<SmartMatchTask> pendingTasks = smartMatchTaskMapper.selectList(wrapper);
        if (pendingTasks.isEmpty()) {
            log.info("无中间状态的训练任务需要重置");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (SmartMatchTask task : pendingTasks) {
            task.setStatus("FAILED");
            task.setErrorMessage("服务重启，停止训练");
            task.setCompletedAt(now);
            task.setUpdatedAt(now);
            smartMatchTaskMapper.updateById(task);
            log.info("重置训练任务: taskId={}, oldStatus={}", task.getId(), task.getStatus());
        }

        log.info("已重置 {} 个中间状态的训练任务", pendingTasks.size());
    }

    // ========== 训练任务 ==========

    /**
     * 触发智能匹配训练。创建任务记录后异步执行 Python 训练。
     */
    @Transactional
    public SmartMatchTask startTraining(Long templateId, String taskName) {
        // 检查同模板是否有正在运行的任务
        LambdaQueryWrapper<SmartMatchTask> runningCheck = new LambdaQueryWrapper<>();
        runningCheck.eq(SmartMatchTask::getTemplateId, templateId)
                .in(SmartMatchTask::getStatus, "PENDING", "RUNNING");
        Long runningCount = smartMatchTaskMapper.selectCount(runningCheck);
        if (runningCount > 0) {
            throw new RuntimeException("该模板已有正在执行的训练任务，请等待完成");
        }

        SmartMatchTask task = new SmartMatchTask();
        task.setTemplateId(templateId);
        task.setTaskName(taskName != null ? taskName : "智能匹配训练");
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setFileCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        smartMatchTaskMapper.insert(task);

        // 事务提交后再触发训练，确保异步线程能读到已提交的任务记录
        Long taskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (asyncEnabled) {
                    log.info("开始异步训练: templateId={}, taskId={}", templateId, taskId);
                    smartMatchTaskService.runTrainingAsync(templateId, taskId);
                } else {
                    log.info("开始同步训练: templateId={}, taskId={}", templateId, taskId);
                    smartMatchTaskService.runTraining(templateId, taskId);
                }
            }
        });

        return task;
    }

    /**
     * 查询训练任务列表（分页）
     */
    public Page<SmartMatchTask> getTasks(Long templateId, int page, int size) {
        LambdaQueryWrapper<SmartMatchTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartMatchTask::getTemplateId, templateId)
                .orderByDesc(SmartMatchTask::getCreatedAt);
        return smartMatchTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 查询训练任务详情
     */
    public SmartMatchTask getTask(Long templateId, Long taskId) {
        return smartMatchTaskMapper.selectById(taskId);
    }

    // ========== 规则管理 ==========

    /**
     * 查询智能匹配规则列表（分页）
     */
    public Page<SmartMatchRule> getRules(Long templateId, int page, int size) {
        LambdaQueryWrapper<SmartMatchRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartMatchRule::getTemplateId, templateId)
                .orderByAsc(SmartMatchRule::getMatchOrder)
                .orderByDesc(SmartMatchRule::getCreatedAt);
        return smartMatchRuleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 查询模板的所有智能匹配规则（不分页，供 TemplateController 调用）
     */
    public List<SmartMatchRule> getRulesByTemplateId(Long templateId) {
        LambdaQueryWrapper<SmartMatchRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartMatchRule::getTemplateId, templateId)
                .orderByAsc(SmartMatchRule::getMatchOrder);
        return smartMatchRuleMapper.selectList(wrapper);
    }

    /**
     * 切换规则启用/停用状态
     */
    @Transactional
    public void toggleRule(Long templateId, Long ruleId) {
        SmartMatchRule rule = smartMatchRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在");
        }
        rule.setIsActive(rule.getIsActive() != null && rule.getIsActive() == 1 ? 0 : 1);
        rule.setUpdatedAt(LocalDateTime.now());
        smartMatchRuleMapper.updateById(rule);
    }

    /**
     * 编辑智能匹配规则
     */
    @Transactional
    public SmartMatchRule updateRule(Long templateId, Long ruleId, SmartMatchRuleDTO dto) {
        SmartMatchRule rule = smartMatchRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在");
        }
        if (dto.getRuleName() != null) rule.setRuleName(dto.getRuleName());
        if (dto.getMatchType() != null) rule.setMatchType(dto.getMatchType());
        if (dto.getMatchLevel() != null) rule.setMatchLevel(dto.getMatchLevel());
        if (dto.getStyleRuleId() != null) rule.setStyleRuleId(dto.getStyleRuleId());
        if (dto.getThreshold() != null) rule.setThreshold(dto.getThreshold());
        if (dto.getIsActive() != null) rule.setIsActive(dto.getIsActive());
        rule.setUpdatedAt(LocalDateTime.now());
        smartMatchRuleMapper.updateById(rule);
        return rule;
    }

    /**
     * 删除智能匹配规则
     */
    @Transactional
    public void deleteRule(Long templateId, Long ruleId) {
        smartMatchRuleMapper.deleteById(ruleId);
    }

    // ========== 智能匹配测试 ==========

    /**
     * 测试智能匹配：上传 Word 文件，使用已训练的规则进行匹配
     */
    public List<SmartMatchTestResultDTO> testMatch(Long templateId, MultipartFile file) throws IOException {
        // 获取模板所有已启用的智能匹配规则
        List<SmartMatchRule> rules = getActiveRules(templateId);
        if (rules.isEmpty()) {
            throw new RuntimeException("该模板暂无已启用的智能匹配规则，请先训练");
        }

        // 获取关联的样式规则名称
        Map<Long, String> ruleNameMap = new HashMap<>();
        Set<Long> styleRuleIds = new HashSet<>();
        for (SmartMatchRule rule : rules) {
            if (rule.getStyleRuleId() != null) {
                styleRuleIds.add(rule.getStyleRuleId());
            }
        }
        if (!styleRuleIds.isEmpty()) {
            List<TemplateRule> templateRules = templateRuleMapper.selectBatchIds(styleRuleIds);
            for (TemplateRule tr : templateRules) {
                ruleNameMap.put(tr.getId(), tr.getName());
            }
        }

        // 查找默认正文样式规则，用于 BODY 兜底
        TemplateRule bodyRule = findBodyTemplateRule(templateId);

        // 解析 Word 文档，提取段落
        List<String> paragraphs = extractParagraphs(file);

        // 对每个段落执行智能匹配
        List<SmartMatchTestResultDTO> results = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i);
            if (text == null || text.trim().isEmpty()) {
                results.add(createEmptyResult(i, text));
                continue;
            }

            SmartMatchTestResultDTO bestMatch = findBestMatch(i, text, rules, ruleNameMap);
            // BODY 兜底：无规则命中时默认为正文
            if ("UNKNOWN".equals(bestMatch.getMatchedType())) {
                bestMatch.setMatchedType("BODY");
                if (bodyRule != null) {
                    bestMatch.setStyleRuleId(bodyRule.getId());
                    bestMatch.setRuleName(bodyRule.getName());
                }
            }
            results.add(bestMatch);
        }

        return results;
    }

    /**
     * 获取模板已启用的智能匹配规则，按 match_order 排序
     */
    private List<SmartMatchRule> getActiveRules(Long templateId) {
        LambdaQueryWrapper<SmartMatchRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmartMatchRule::getTemplateId, templateId)
                .eq(SmartMatchRule::getIsActive, 1)
                .orderByAsc(SmartMatchRule::getMatchOrder);
        return smartMatchRuleMapper.selectList(wrapper);
    }

    /**
     * 从 Word 文件中提取所有段落文本
     */
    private List<String> extractParagraphs(MultipartFile file) throws IOException {
        List<String> paragraphs = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                paragraphs.add(text != null ? text.trim() : "");
            }
        }
        return paragraphs;
    }

    /**
     * 为段落寻找最佳匹配规则
     */
    private SmartMatchTestResultDTO findBestMatch(int index, String text,
                                                   List<SmartMatchRule> rules,
                                                   Map<Long, String> ruleNameMap) {
        SmartMatchTestResultDTO result = new SmartMatchTestResultDTO();
        result.setParagraphIndex(index);
        result.setText(text.length() > 200 ? text.substring(0, 200) : text);

        double bestConfidence = 0;
        SmartMatchRule bestRule = null;

        for (SmartMatchRule rule : rules) {
            double confidence = calculateSimilarity(text, rule);
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestRule = rule;
            }
        }

        // 阈值判断
        BigDecimal threshold = bestRule != null ? bestRule.getThreshold() : BigDecimal.valueOf(0.3);
        if (bestRule != null && bestConfidence >= threshold.doubleValue()) {
            result.setMatchedType(bestRule.getMatchType());
            result.setMatchLevel(bestRule.getMatchLevel());
            result.setStyleRuleId(bestRule.getStyleRuleId());
            result.setRuleName(ruleNameMap.get(bestRule.getStyleRuleId()));
            result.setConfidence(bestConfidence);
        } else {
            result.setMatchedType("UNKNOWN");
            result.setConfidence(bestConfidence);
        }

        return result;
    }

    /**
     * 计算文本与规则的相似度：基于关键词加权匹配
     * 关键词按 TF-IDF 权重降序排列，位置越靠前权重越大。
     */
    private double calculateSimilarity(String text, SmartMatchRule rule) {
        if (rule.getKeywords() == null || rule.getKeywords().isEmpty()) {
            return 0;
        }

        String[] keywords = rule.getKeywords().split(",");
        if (keywords.length == 0) {
            return 0;
        }

        double totalWeight = 0;
        double matchWeight = 0;

        for (int i = 0; i < keywords.length; i++) {
            String kw = keywords[i].trim();
            if (kw.isEmpty()) {
                continue;
            }

            // 位置权重：首位关键词（TF-IDF最高）权重=1.0，末位=0.5
            double weight = 1.0 - (i / (double) keywords.length) * 0.5;

            totalWeight += weight;

            if (text.contains(kw)) {
                matchWeight += weight;
            }
        }

        return totalWeight > 0 ? matchWeight / totalWeight : 0;
    }

    /**
     * 创建空匹配结果
     */
    private SmartMatchTestResultDTO createEmptyResult(int index, String text) {
        SmartMatchTestResultDTO result = new SmartMatchTestResultDTO();
        result.setParagraphIndex(index);
        result.setText(text != null && text.length() > 200 ? text.substring(0, 200) : text);
        result.setMatchedType("UNKNOWN");
        result.setConfidence(0);
        return result;
    }

    /**
     * 查找模板的默认正文样式规则（名称为"正文"）
     */
    private TemplateRule findBodyTemplateRule(Long templateId) {
        LambdaQueryWrapper<TemplateRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateRule::getTemplateId, templateId)
               .eq(TemplateRule::getName, "正文");
        List<TemplateRule> rules = templateRuleMapper.selectList(wrapper);
        return rules.isEmpty() ? null : rules.get(0);
    }
}
