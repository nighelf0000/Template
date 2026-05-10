package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.SmartMatchRuleDTO;
import com.template.dto.SmartMatchTestResultDTO;
import com.template.entity.SmartMatchRule;
import com.template.entity.SmartMatchTask;
import com.template.service.SmartMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/smart-match")
@RequiredArgsConstructor
public class SmartMatchController {

    private final SmartMatchService smartMatchService;

    // ========== 训练任务 ==========

    /**
     * 触发智能匹配训练
     * POST /api/smart-match/{templateId}/train
     */
    @PostMapping("/{templateId}/train")
    public ApiResponse<SmartMatchTask> startTraining(
            @PathVariable Long templateId,
            @RequestParam(required = false) String taskName) {
        log.info("触发智能匹配训练: templateId={}, taskName={}", templateId, taskName);
        SmartMatchTask task = smartMatchService.startTraining(templateId, taskName);
        return ApiResponse.ok(task);
    }

    /**
     * 查询训练任务列表（分页）
     * GET /api/smart-match/{templateId}/tasks
     */
    @GetMapping("/{templateId}/tasks")
    public ApiResponse<Page<SmartMatchTask>> getTasks(
            @PathVariable Long templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询训练任务列表: templateId={}, page={}, size={}", templateId, page, size);
        Page<SmartMatchTask> result = smartMatchService.getTasks(templateId, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 查询训练任务详情
     * GET /api/smart-match/{templateId}/tasks/{taskId}
     */
    @GetMapping("/{templateId}/tasks/{taskId}")
    public ApiResponse<SmartMatchTask> getTask(
            @PathVariable Long templateId,
            @PathVariable Long taskId) {
        log.info("查询训练任务详情: templateId={}, taskId={}", templateId, taskId);
        SmartMatchTask task = smartMatchService.getTask(templateId, taskId);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }
        return ApiResponse.ok(task);
    }

    // ========== 规则管理 ==========

    /**
     * 查询智能匹配规则列表（分页）
     * GET /api/smart-match/{templateId}/rules
     */
    @GetMapping("/{templateId}/rules")
    public ApiResponse<Page<SmartMatchRule>> getRules(
            @PathVariable Long templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询智能匹配规则列表: templateId={}, page={}, size={}", templateId, page, size);
        Page<SmartMatchRule> result = smartMatchService.getRules(templateId, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 切换规则启用状态
     * PUT /api/smart-match/{templateId}/rules/{ruleId}/toggle
     */
    @PutMapping("/{templateId}/rules/{ruleId}/toggle")
    public ApiResponse<Void> toggleRule(
            @PathVariable Long templateId,
            @PathVariable Long ruleId) {
        log.info("切换规则启用状态: templateId={}, ruleId={}", templateId, ruleId);
        smartMatchService.toggleRule(templateId, ruleId);
        return ApiResponse.ok(null);
    }

    /**
     * 编辑智能匹配规则
     * PUT /api/smart-match/{templateId}/rules/{ruleId}
     */
    @PutMapping("/{templateId}/rules/{ruleId}")
    public ApiResponse<SmartMatchRule> updateRule(
            @PathVariable Long templateId,
            @PathVariable Long ruleId,
            @RequestBody SmartMatchRuleDTO dto) {
        log.info("编辑智能匹配规则: templateId={}, ruleId={}", templateId, ruleId);
        SmartMatchRule rule = smartMatchService.updateRule(templateId, ruleId, dto);
        return ApiResponse.ok(rule);
    }

    /**
     * 删除规则
     * DELETE /api/smart-match/{templateId}/rules/{ruleId}
     */
    @DeleteMapping("/{templateId}/rules/{ruleId}")
    public ApiResponse<Void> deleteRule(
            @PathVariable Long templateId,
            @PathVariable Long ruleId) {
        log.info("删除智能匹配规则: templateId={}, ruleId={}", templateId, ruleId);
        smartMatchService.deleteRule(templateId, ruleId);
        return ApiResponse.ok(null);
    }

    // ========== 智能匹配测试 ==========

    /**
     * 测试智能匹配
     * POST /api/smart-match/{templateId}/test
     */
    @PostMapping("/{templateId}/test")
    public ApiResponse<List<SmartMatchTestResultDTO>> testMatch(
            @PathVariable Long templateId,
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("测试智能匹配: templateId={}, filename={}", templateId, file.getOriginalFilename());
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件为空");
        }
        List<SmartMatchTestResultDTO> results = smartMatchService.testMatch(templateId, file);
        return ApiResponse.ok(results);
    }
}
