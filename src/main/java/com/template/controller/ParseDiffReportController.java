package com.template.controller;

import com.template.dto.ApiResponse;
import com.template.dto.ParseDiffCompareRequest;
import com.template.dto.ParseDiffReportVO;
import com.template.service.ParseDiffReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/parse-diff")
@RequiredArgsConstructor
public class ParseDiffReportController {

    private final ParseDiffReportService parseDiffReportService;

    /**
     * 任务间差异对比（方案A）
     * POST /api/parse-diff/compare/task
     */
    @PostMapping("/compare/task")
    public ApiResponse<ParseDiffReportVO> compareTask(@RequestBody ParseDiffCompareRequest request) {
        log.info("任务间差异对比: recordIdA={}, recordIdB={}", request.getRecordIdA(), request.getRecordIdB());
        try {
            ParseDiffReportVO result = parseDiffReportService.compareTask(request);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 与标准答案对比（方案B）
     * POST /api/parse-diff/compare/standard
     */
    @PostMapping("/compare/standard")
    public ApiResponse<ParseDiffReportVO> compareStandard(@RequestBody ParseDiffCompareRequest request) {
        log.info("标准答案对比: recordId={}", request.getRecordIdA());
        // This endpoint will be fully implemented when standard answer service is completed
        return ApiResponse.fail(501, "标准答案对比功能待实现");
    }

    /**
     * 获取差异对比报告详情
     * GET /api/parse-diff/report/{id}
     */
    @GetMapping("/report/{id}")
    public ApiResponse<ParseDiffReportVO> getReport(@PathVariable Long id) {
        log.info("获取差异报告: id={}", id);
        try {
            ParseDiffReportVO result = parseDiffReportService.getReport(id);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    /**
     * 删除差异对比报告
     * DELETE /api/parse-diff/report/{id}
     */
    @DeleteMapping("/report/{id}")
    public ApiResponse<Void> deleteReport(@PathVariable Long id) {
        log.info("删除差异报告: id={}", id);
        try {
            parseDiffReportService.deleteReport(id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }
}
