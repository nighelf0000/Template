package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.ParseStandardAnswerVO;
import com.template.service.ParseStandardAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/parse-standard-answer")
@RequiredArgsConstructor
public class ParseStandardAnswerController {

    private final ParseStandardAnswerService parseStandardAnswerService;

    /**
     * 上传标准答案 JSON
     * POST /api/parse-standard-answer/upload
     */
    @PostMapping("/upload")
    public ApiResponse<ParseStandardAnswerVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") Long templateId,
            @RequestParam("sourceFile") String sourceFile,
            @RequestParam("answerName") String answerName,
            @RequestParam(required = false) String description) {

        log.info("上传标准答案: answerName={}, templateId={}, sourceFile={}", answerName, templateId, sourceFile);

        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件为空");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ParseStandardAnswerVO result = parseStandardAnswerService.upload(
                    templateId, sourceFile, answerName, description, content);
            return ApiResponse.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("标准答案上传失败", e);
            return ApiResponse.fail(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询标准答案列表
     * GET /api/parse-standard-answer/list
     */
    @GetMapping("/list")
    public ApiResponse<Page<ParseStandardAnswerVO>> list(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String sourceFile,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询标准答案列表: templateId={}, sourceFile={}, page={}, size={}",
                templateId, sourceFile, page, size);
        Page<ParseStandardAnswerVO> result = parseStandardAnswerService.list(templateId, sourceFile, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 删除标准答案
     * DELETE /api/parse-standard-answer/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("删除标准答案: id={}", id);
        try {
            parseStandardAnswerService.delete(id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        }
    }
}
