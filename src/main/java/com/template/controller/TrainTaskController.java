package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.TrainTaskProgressDTO;
import com.template.dto.TrainTaskStatusDTO;
import com.template.dto.TrainTaskVO;
import com.template.service.TrainTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/train-task")
@RequiredArgsConstructor
public class TrainTaskController {

    private final TrainTaskService trainTaskService;

    @Value("${template.train.internal-token:train-internal-token}")
    private String internalToken;

    /**
     * 创建并启动训练任务
     * POST /api/train-task/start
     */
    @PostMapping("/start")
    public ApiResponse<TrainTaskVO> start(@RequestBody Map<String, Object> body) {
        Long templateId = body.get("templateId") instanceof Number
                ? ((Number) body.get("templateId")).longValue() : null;
        String taskName = (String) body.get("taskName");

        if (templateId == null) {
            return ApiResponse.fail(400, "templateId 不能为空");
        }
        log.info("创建训练任务: templateId={}, taskName={}", templateId, taskName);

        try {
            TrainTaskVO vo = trainTaskService.start(templateId, taskName);
            return ApiResponse.ok(vo);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 分页查询训练任务列表
     * GET /api/train-task/page
     */
    @GetMapping("/page")
    public ApiResponse<Page<TrainTaskVO>> page(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询训练任务列表: templateId={}, status={}, keyword={}, page={}, size={}",
                templateId, status, keyword, page, size);
        Page<TrainTaskVO> result = trainTaskService.page(templateId, status, keyword, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 查询训练任务详情
     * GET /api/train-task/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<TrainTaskVO> detail(@PathVariable Long id) {
        log.info("查询训练任务详情: id={}", id);
        TrainTaskVO vo = trainTaskService.detail(id);
        if (vo == null) {
            return ApiResponse.fail(404, "任务不存在");
        }
        return ApiResponse.ok(vo);
    }

    /**
     * 更新训练任务进度（供 Python 引擎回调）
     * PUT /api/train-task/{id}/progress
     */
    @PutMapping("/{id}/progress")
    public ApiResponse<Void> updateProgress(
            @PathVariable Long id,
            @RequestBody TrainTaskProgressDTO dto,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalToken.equals(token)) {
            return ApiResponse.fail(401, "内部认证失败");
        }
        log.info("更新训练任务进度: id={}, progress={}", id, dto.getProgress());
        trainTaskService.updateProgress(id, dto);
        return ApiResponse.ok(null);
    }

    /**
     * 更新训练任务状态（供 Python 引擎回调）
     * PUT /api/train-task/{id}/status
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody TrainTaskStatusDTO dto,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalToken.equals(token)) {
            return ApiResponse.fail(401, "内部认证失败");
        }
        log.info("更新训练任务状态: id={}, status={}", id, dto.getStatus());
        trainTaskService.updateStatus(id, dto);
        return ApiResponse.ok(null);
    }
}
