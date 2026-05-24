package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.ParseRecordCreateDTO;
import com.template.dto.ParseRecordDetailVO;
import com.template.dto.ParseRecordVO;
import com.template.service.ParseRecordService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/parse-record")
@RequiredArgsConstructor
public class ParseRecordController {

    private final ParseRecordService parseRecordService;

    /**
     * 保存解析结果（供 Python 引擎调用）
     * POST /api/parse-record
     */
    @PostMapping
    public ApiResponse<Long> save(@RequestBody ParseRecordCreateDTO dto) {
        log.info("保存解析结果: sourceFile={}, templateId={}", dto.getSourceFile(), dto.getTemplateId());
        try {
            Long id = parseRecordService.save(dto);
            return ApiResponse.ok(id);
        } catch (Exception e) {
            log.error("保存解析结果失败", e);
            return ApiResponse.fail(500, "保存失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询
     * GET /api/parse-record/page
     */
    @GetMapping("/page")
    public ApiResponse<Page<ParseRecordVO>> page(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String rulesetName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询解析记录: templateId={}, status={}, keyword={}, page={}, size={}",
                templateId, status, keyword, page, size);
        Page<ParseRecordVO> result = parseRecordService.page(
                templateId, status, keyword, startTime, endTime, rulesetName, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 获取详情
     * GET /api/parse-record/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<ParseRecordVO> detail(@PathVariable Long id) {
        log.info("查询解析记录详情: id={}", id);
        ParseRecordVO vo = parseRecordService.detail(id);
        if (vo == null) {
            return ApiResponse.fail(404, "记录不存在");
        }
        return ApiResponse.ok(vo);
    }

    /**
     * 获取解析记录完整详情（含结构树、文档元数据、元素统计）
     * GET /api/parse-record/{id}/detail
     */
    @GetMapping("/{id}/detail")
    public ApiResponse<ParseRecordDetailVO> getDetail(@PathVariable Long id) {
        log.info("获取解析记录完整详情: id={}", id);
        ParseRecordDetailVO vo = parseRecordService.getDetail(id);
        if (vo == null) {
            return ApiResponse.fail(404, "记录不存在");
        }
        return ApiResponse.ok(vo);
    }

    /**
     * 更新解析记录的结构树（供 Python 引擎在元素拆解后更新使用）
     * PUT /api/parse-record/{id}/structure-tree
     */
    @PutMapping("/{id}/structure-tree")
    public ApiResponse<Void> updateStructureTree(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        log.info("更新结构树: id={}", id);
        try {
            parseRecordService.updateStructureTree(id, body.get("structureTree"));
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }

    /**
     * 获取结构树 JSON
     * GET /api/parse-record/{id}/json
     */
    @GetMapping("/{id}/json")
    public ApiResponse<String> getStructureTree(@PathVariable Long id) {
        log.info("获取结构树 JSON: id={}", id);
        String json = parseRecordService.getStructureTree(id);
        if (json == null) {
            return ApiResponse.fail(404, "记录不存在");
        }
        return ApiResponse.ok(json);
    }

    /**
     * 删除解析记录
     * DELETE /api/parse-record/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("删除解析记录: id={}", id);
        try {
            parseRecordService.delete(id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }
}
