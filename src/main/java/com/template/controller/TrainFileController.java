package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.TrainFileVO;
import com.template.service.TrainFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/train-file")
@RequiredArgsConstructor
public class TrainFileController {

    private final TrainFileService trainFileService;

    /**
     * 上传训练文件
     * POST /api/train-file/upload
     */
    @PostMapping("/upload")
    public ApiResponse<TrainFileVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") Long templateId) {
        log.info("上传训练文件: filename={}, templateId={}", file.getOriginalFilename(), templateId);
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件为空");
        }
        try {
            TrainFileVO vo = trainFileService.upload(file, templateId);
            return ApiResponse.ok(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(400, e.getMessage());
        } catch (IOException e) {
            log.error("文件上传失败: templateId={}", templateId, e);
            return ApiResponse.fail(500, "文件上传失败");
        }
    }

    /**
     * 查询训练文件列表（分页）
     * GET /api/train-file/list
     */
    @GetMapping("/list")
    public ApiResponse<Page<TrainFileVO>> list(
            @RequestParam("templateId") Long templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询训练文件列表: templateId={}, page={}, size={}", templateId, page, size);
        Page<TrainFileVO> result = trainFileService.list(templateId, page, size);
        return ApiResponse.ok(result);
    }

    /**
     * 删除训练文件
     * DELETE /api/train-file/{id}
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("删除训练文件: id={}", id);
        try {
            trainFileService.delete(id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.fail(400, e.getMessage());
        }
    }

    /**
     * 获取文件原始内容（供 Python 引擎调用）
     * GET /api/train-file/{id}/raw
     */
    @GetMapping("/{id}/raw")
    public ResponseEntity<byte[]> getRawContent(@PathVariable Long id) {
        log.info("获取训练文件原始内容: id={}", id);
        try {
            byte[] content = trainFileService.getRawContent(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
