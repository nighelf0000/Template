package com.template.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.entity.SmartMatchRule;
import com.template.entity.SmartMatchTask;
import com.template.entity.UploadFile;
import com.template.mapper.SmartMatchRuleMapper;
import com.template.mapper.SmartMatchTaskMapper;
import com.template.mapper.UploadFileMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartMatchTaskService {

    private final SmartMatchTaskMapper smartMatchTaskMapper;
    private final SmartMatchRuleMapper smartMatchRuleMapper;
    private final UploadFileMapper uploadFileMapper;
    private final ObjectMapper objectMapper;

    @Value("${template.smart-match.python-path:python}")
    private String pythonPath;

    @Value("${template.smart-match.script-path:python/smart_train.py}")
    private String scriptPath;

    @Value("${template.smart-match.training-timeout-minutes:10}")
    private int trainingTimeoutMinutes;

    @Value("${template.smart-match.async:true}")
    private boolean asyncEnabled;

    /**
     * 执行智能匹配训练任务
     */
    public void runTraining(Long templateId, Long taskId) {
        // 更新任务为 RUNNING
        SmartMatchTask task = smartMatchTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("训练任务不存在: taskId={}", taskId);
            return;
        }
        task.setStatus("RUNNING");
        task.setProgress(5);
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        smartMatchTaskMapper.updateById(task);

        try {
            // 1. 查询已处理文件（EXPORTED / ADJUSTED 状态）
            LambdaQueryWrapper<UploadFile> fileQuery = new LambdaQueryWrapper<>();
            fileQuery.eq(UploadFile::getTemplateId, templateId)
                    .in(UploadFile::getStatus, "EXPORTED", "ADJUSTED")
                    .select(UploadFile.class, info -> !info.getColumn().equals("original_content")
                            && !info.getColumn().equals("parsed_content")
                            && !info.getColumn().equals("output_content"));
            List<UploadFile> files = uploadFileMapper.selectList(fileQuery);

            if (files.isEmpty()) {
                throw new RuntimeException("没有已处理的文件可用于训练，请先解析并导出文件");
            }
            log.info("runTraining start");
            task.setFileCount(files.size());
            task.setProgress(15);
            smartMatchTaskMapper.updateById(task);

            // 2. 提取训练数据
            Map<String, TrainingGroup> trainingGroups = new LinkedHashMap<>();

            for (UploadFile file : files) {
                if (file.getParsedJson() == null || file.getParsedJson().isBlank()) {
                    log.warn("文件 parsed_json 为空: fileId={}", file.getId());
                    continue;
                }
                extractTrainingData(file.getParsedJson(), trainingGroups);
            }

            if (trainingGroups.isEmpty()) {
                throw new RuntimeException("未能从文件中提取有效的训练数据");
            }

            task.setProgress(30);
            smartMatchTaskMapper.updateById(task);

            // 3. 构建训练数据 JSON
            List<Map<String, Object>> trainingDataList = new ArrayList<>();
            for (TrainingGroup group : trainingGroups.values()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("match_type", group.matchType);
                item.put("match_level", group.matchLevel);
                item.put("texts", group.texts);
                item.put("style_rule_id", group.styleRuleId);
                trainingDataList.add(item);
            }

            Map<String, Object> inputJson = new LinkedHashMap<>();
            inputJson.put("template_id", templateId);
            inputJson.put("training_data", trainingDataList);

            String trainingJson = objectMapper.writeValueAsString(inputJson);
            log.info("训练数据构建完成: groups={}, texts={}",
                    trainingDataList.size(),
                    trainingDataList.stream().mapToInt(d -> ((List<String>) d.get("texts")).size()).sum());

            task.setProgress(50);
            smartMatchTaskMapper.updateById(task);

            // 4. 写入训练数据到临时文件，调用 Python 脚本
            String scriptAbsolutePath = resolveScriptPath(scriptPath);
            log.info("调用 Python 训练脚本: path={}, script={}", pythonPath, scriptAbsolutePath);

            File tempFile = File.createTempFile("training_" + taskId + "_", ".json");
            tempFile.deleteOnExit(); // JVM 退出时自动清理（兜底保证）
            String resultJson = null;

            try {
                // 写入训练数据到临时文件
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                    writer.write(trainingJson);
                    writer.flush();
                }
                log.info("训练数据已写入临时文件: path={}, size={}", tempFile.getAbsolutePath(), tempFile.length());

                // 通过 --input 参数传递临时文件路径
                ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptAbsolutePath, "--input", tempFile.getAbsolutePath());
                Process process = pb.start();
                log.info("process.isAlive():{}", process.isAlive());

                // 启动线程消费 stdout/stderr，避免管道缓冲区满导致死锁
                ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
                ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
                Thread stdoutReader = new Thread(() -> {
                    try (InputStream is = process.getInputStream()) {
                        is.transferTo(stdoutBuffer);
                    } catch (Exception e) {
                        log.warn("读取 stdout 异常", e);
                    }
                });
                Thread stderrReader = new Thread(() -> {
                    try (InputStream is = process.getErrorStream()) {
                        is.transferTo(stderrBuffer);
                    } catch (Exception e) {
                        log.warn("读取 stderr 异常", e);
                    }
                });
                stdoutReader.start();
                stderrReader.start();

                // 检查进程是否存活（若已退出，读取错误信息）
                if (!process.isAlive()) {
                    stdoutReader.join(5000);
                    stderrReader.join(5000);
                    String errorOutput = stderrBuffer.toString(StandardCharsets.UTF_8);
                    if (errorOutput.isEmpty()) {
                        errorOutput = stdoutBuffer.toString(StandardCharsets.UTF_8);
                    }
                    int exitCode = process.exitValue();
                    log.error("Python 进程启动后立即退出: exitCode={}, output={}", exitCode, errorOutput);
                    throw new RuntimeException("Python脚本启动失败: " + (errorOutput.length() > 500 ? errorOutput.substring(0, 500) : errorOutput));
                }

                task.setProgress(70);
                smartMatchTaskMapper.updateById(task);

                // 等待训练完成
                boolean finished = process.waitFor(trainingTimeoutMinutes, TimeUnit.MINUTES);
                if (!finished) {
                    stdoutReader.interrupt();
                    stderrReader.interrupt();
                    process.destroyForcibly();
                    throw new RuntimeException("训练超时（" + trainingTimeoutMinutes + "分钟）");
                }

                // 等待读取线程结束，获取输出
                stdoutReader.join(5000);
                stderrReader.join(5000);
                resultJson = stdoutBuffer.toString(StandardCharsets.UTF_8);

                int exitCode = process.exitValue();
                String stderrOutput = stderrBuffer.toString(StandardCharsets.UTF_8);
                log.info("Python 脚本执行完成: exitCode={}, stderr={}", exitCode, stderrOutput);

                if (exitCode != 0) {
                    log.error("Python 脚本执行失败: exitCode={}, stdout={}, stderr={}", exitCode, resultJson, stderrOutput);
                    String errDetail = !stderrOutput.isBlank() ? stderrOutput : resultJson;
                    throw new RuntimeException("训练脚本执行失败: " + (errDetail.length() > 500 ? errDetail.substring(0, 500) : errDetail));
                }
            } finally {
                // 删除临时文件
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (!deleted) {
                        log.warn("临时文件删除失败，JVM退出时将由 deleteOnExit 兜底清理: {}", tempFile.getAbsolutePath());
                    }
                }
            }

            // 6. 解析训练结果
            Map<String, Object> resultMap = objectMapper.readValue(resultJson,
                    new TypeReference<Map<String, Object>>() {});

            boolean success = Boolean.TRUE.equals(resultMap.get("success"));
            if (!success) {
                String errMsg = (String) resultMap.getOrDefault("error", "训练失败");
                throw new RuntimeException(errMsg);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) resultMap.get("rules");

            if (rules == null || rules.isEmpty()) {
                throw new RuntimeException("训练未能生成有效规则");
            }

            // 7. 查询该模板已有规则（用于后续去重判断）
            List<SmartMatchRule> existingRules = smartMatchRuleMapper.selectList(
                    new LambdaQueryWrapper<SmartMatchRule>()
                            .eq(SmartMatchRule::getTemplateId, templateId));

            // 8. 保存规则到数据库（去重更新 or 插入）
            int order = 0;
            for (Map<String, Object> ruleData : rules) {
                // ----- 构建规则对象（与原逻辑一致） -----
                SmartMatchRule rule = new SmartMatchRule();
                rule.setTemplateId(templateId);
                rule.setTaskId(taskId);
                rule.setRuleName((String) ruleData.getOrDefault("rule_name",
                        ruleData.get("match_type") + "_" + order));
                rule.setMatchType((String) ruleData.get("match_type"));
                Object level = ruleData.get("match_level");
                rule.setMatchLevel(level instanceof Integer ? (Integer) level : null);

                // keywords
                Object kw = ruleData.get("keywords");
                if (kw instanceof List) {
                    rule.setKeywords(String.join(",", (List<String>) kw));
                } else {
                    rule.setKeywords(kw != null ? kw.toString() : "");
                }

                // feature_vector - 转为 JSON 字符串
                Object fv = ruleData.get("feature_vector");
                rule.setFeatureVector(fv != null ? objectMapper.writeValueAsString(fv) : "[]");

                rule.setStyleRuleId(ruleData.get("style_rule_id") != null
                        ? ((Number) ruleData.get("style_rule_id")).longValue() : null);

                // threshold
                Object thr = ruleData.get("threshold");
                rule.setThreshold(thr instanceof Number
                        ? BigDecimal.valueOf(((Number) thr).doubleValue())
                        : BigDecimal.valueOf(0.3));

                rule.setIsActive(1);
                rule.setMatchOrder(order);
                rule.setUpdatedAt(LocalDateTime.now());
                // 注意：createdAt 在 insert 时设置，update 时保留原值

                // ----- 去重判断 -----
                Set<String> keywordSet = parseKeywordsToSet(rule.getKeywords());
                SmartMatchRule duplicate = findDuplicateRule(templateId, keywordSet, existingRules);

                if (duplicate != null) {
                    // 重复 → 更新已有规则（仅更新非标识性字段）
                    duplicate.setFeatureVector(rule.getFeatureVector());
                    duplicate.setThreshold(rule.getThreshold());
                    duplicate.setRuleName(rule.getRuleName());
                    duplicate.setTaskId(rule.getTaskId());
                    duplicate.setMatchOrder(rule.getMatchOrder());
                    duplicate.setUpdatedAt(rule.getUpdatedAt());
                    smartMatchRuleMapper.updateById(duplicate);
                    log.info("规则去重更新: ruleId={}, keywords={}", duplicate.getId(), duplicate.getKeywords());
                } else {
                    // 无重复 → 插入新规则
                    rule.setCreatedAt(LocalDateTime.now());
                    smartMatchRuleMapper.insert(rule);
                    log.info("规则新增插入: keywords={}", rule.getKeywords());
                }
                order++;
            }

            // 8. 更新任务为成功
            task.setStatus("SUCCESS");
            task.setProgress(100);
            task.setRuleCount(rules.size());
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            smartMatchTaskMapper.updateById(task);

            log.info("训练任务完成: taskId={}, rules={}", taskId, rules.size());

        } catch (Exception e) {
            log.error("训练任务执行失败: taskId={}", taskId, e);

            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage() != null ? e.getMessage() : "训练失败");
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            smartMatchTaskMapper.updateById(task);
        }
    }

    /**
     * 异步执行智能匹配训练任务（委托给 runTraining）
     */
    @Async("smartMatchTaskExecutor")
    public void runTrainingAsync(Long templateId, Long taskId) {
        runTraining(templateId, taskId);
    }

    /**
     * 将 keywords 字符串按逗号拆分、trim 后转为 Set，用于集合匹配比较
     */
    private Set<String> parseKeywordsToSet(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return new HashSet<>();
        }
        String[] parts = keywords.split(",");
        Set<String> set = new HashSet<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    /**
     * 在指定 templateId 下查找与目标 keywords 集合匹配的已有规则。
     * 如果有多条重复，按优先级返回：isActive=1 且 matchOrder 最小的那条；
     * 如果没有激活的，返回 matchOrder 最小的那条。
     *
     * @return 匹配的已有规则，若无重复返回 null
     */
    private SmartMatchRule findDuplicateRule(Long templateId, Set<String> keywordSet,
                                              List<SmartMatchRule> existingRules) {
        SmartMatchRule candidate = null;

        for (SmartMatchRule existing : existingRules) {
            Set<String> existingSet = parseKeywordsToSet(existing.getKeywords());
            if (keywordSet.equals(existingSet)) {
                // 集合相等，视为重复
                if (candidate == null) {
                    candidate = existing;
                } else {
                    // 优先级：isActive=1 优先，其次 matchOrder 小优先
                    boolean currentIsActive = Integer.valueOf(1).equals(existing.getIsActive());
                    boolean candidateIsActive = Integer.valueOf(1).equals(candidate.getIsActive());

                    if (currentIsActive && !candidateIsActive) {
                        // 当前规则激活，候选未激活 → 替换
                        candidate = existing;
                    } else if (currentIsActive == candidateIsActive) {
                        // 激活状态相同 → 取 matchOrder 较小的
                        int currentOrder = existing.getMatchOrder() != null ? existing.getMatchOrder() : Integer.MAX_VALUE;
                        int candidateOrder = candidate.getMatchOrder() != null ? candidate.getMatchOrder() : Integer.MAX_VALUE;
                        if (currentOrder < candidateOrder) {
                            candidate = existing;
                        }
                    }
                    // 如果当前未激活而候选已激活，保留候选
                }
            }
        }
        return candidate;
    }

    /**
     * 从文件的 parsed_json 中提取训练数据，按 (matchType, matchLevel, styleRuleId) 分组
     */
    private void extractTrainingData(String parsedJson,
                                      Map<String, TrainingGroup> trainingGroups) {
        try {
            List<Map<String, Object>> paragraphs = objectMapper.readValue(
                    parsedJson, new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> para : paragraphs) {
                String matchedType = (String) para.get("matchedType");
                if (matchedType == null || "UNKNOWN".equals(matchedType) || "BODY".equals(matchedType)) {
                    continue;
                }

                Object textObj = para.get("text");
                if (textObj == null || textObj.toString().isBlank()) {
                    continue;
                }

                Number levelNum = (Number) para.get("matchedLevel");
                Integer matchLevel = levelNum != null ? levelNum.intValue() : null;

                Number ruleIdNum = (Number) para.get("ruleId");
                Long styleRuleId = ruleIdNum != null ? ruleIdNum.longValue() : null;

                String key = matchedType + "|" + matchLevel + "|" + styleRuleId;

                TrainingGroup group = trainingGroups.get(key);
                if (group == null) {
                    group = new TrainingGroup();
                    group.matchType = matchedType;
                    group.matchLevel = matchLevel;
                    group.styleRuleId = styleRuleId;
                    group.texts = new ArrayList<>();
                    trainingGroups.put(key, group);
                }
                group.texts.add(textObj.toString());
            }
        } catch (Exception e) {
            log.warn("解析训练数据失败", e);
        }
    }

    /**
     * 解析脚本路径：如果是相对路径，则相对于项目根目录
     */
    private String resolveScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.startsWith("/") || scriptPath.contains(":")) {
            return scriptPath;
        }
        // 尝试相对路径：从工作目录查找
        String userDir = System.getProperty("user.dir");
        return userDir + File.separator + scriptPath;
    }

    /**
     * 训练数据分组（内部类）
     */
    private static class TrainingGroup {
        String matchType;
        Integer matchLevel;
        Long styleRuleId;
        List<String> texts;
    }
}
