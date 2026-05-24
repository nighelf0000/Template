package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.ElementTypeCountVO;
import com.template.dto.ParseElementVO;
import com.template.service.ParseElementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/parse-element")
@RequiredArgsConstructor
public class ParseElementController {

    private final ParseElementService parseElementService;

    /**
     * 分页查询元素明细
     * GET /api/parse-element/list
     */
    @GetMapping("/list")
    public ApiResponse<Page<ParseElementVO>> list(
            @RequestParam Long recordId,
            @RequestParam(required = false) String elementType,
            @RequestParam(required = false) BigDecimal confidenceMin,
            @RequestParam(required = false) BigDecimal confidenceMax,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parentElementId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("查询元素明细: recordId={}, elementType={}, confidenceMin={}, confidenceMax={}, keyword={}, page={}, size={}",
                recordId, elementType, confidenceMin, confidenceMax, keyword, page, size);
        try {
            Page<ParseElementVO> result = parseElementService.list(
                    recordId, elementType, confidenceMin, confidenceMax,
                    keyword, parentElementId, page, size);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取元素类型统计
     * GET /api/parse-element/types
     */
    @GetMapping("/types")
    public ApiResponse<List<ElementTypeCountVO>> getTypes(@RequestParam Long recordId) {
        log.info("获取元素类型统计: recordId={}", recordId);
        try {
            List<ElementTypeCountVO> result = parseElementService.getTypeCounts(recordId);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }
}
