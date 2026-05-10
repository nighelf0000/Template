package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.AdjustRequestDTO;
import jakarta.validation.Valid;
import com.template.dto.ApiResponse;
import com.template.dto.ParagraphItemDTO;
import com.template.dto.PreviewResultDTO;
import com.template.entity.UploadFile;
import com.template.mapper.UploadFileMapper;
import com.template.service.WordParseService;
import com.template.service.pdf.PdfConversionException;
import com.template.service.pdf.PdfConversionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/word")
@RequiredArgsConstructor
public class WordController {

    private final WordParseService wordParseService;
    private final UploadFileMapper uploadFileMapper;
    private final PdfConversionService pdfConversionService;

    @GetMapping("/list")
    public ApiResponse<Page<UploadFile>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询文件列表: page={}, size={}", page, size);
        Page<UploadFile> result = wordParseService.list(page, size);
        return ApiResponse.ok(result);
    }

    @PostMapping("/upload")
    public ApiResponse<UploadFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "templateId", required = false) Long templateId) throws IOException {
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件为空");
        }
        String filename = file.getOriginalFilename();
        log.info("上传文件: filename={}", filename);
        if (filename == null || (!filename.endsWith(".docx") && !filename.endsWith(".doc"))) {
            return ApiResponse.fail(400, "仅支持 .docx 格式文件");
        }
        UploadFile uf = wordParseService.upload(file, templateId);
        return ApiResponse.ok(uf);
    }

    @PostMapping("/{id}/parse")
    public ApiResponse<UploadFile> parse(@PathVariable Long id) {
        log.info("解析文件: id={}", id);
        UploadFile uf = wordParseService.parse(id);
        return ApiResponse.ok(uf);
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<PreviewResultDTO> preview(@PathVariable Long id) {
        log.info("预览文件: id={}", id);
        PreviewResultDTO result = wordParseService.preview(id);
        return ApiResponse.ok(result);
    }

    /**
     * 获取 PDF 预览文件（基于 original_content 实时生成，含段落底色）。
     */
    @GetMapping("/{id}/preview/pdf")
    public ResponseEntity<byte[]> previewPdf(@PathVariable Long id) {
        log.info("预览 PDF: id={}", id);
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null || uf.getOriginalContent() == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            // 获取段落底色信息（使用轻量方法，避免 preview() 中 PDF 位置计算等额外开销）
            List<ParagraphItemDTO> paragraphs = wordParseService.getParagraphBackgrounds(id);

            // 调用带底色的 PDF 生成
            byte[] pdfBytes = pdfConversionService.convertToPdfWithBackground(
                    uf.getOriginalContent(), uf.getOriginalName(), paragraphs);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "inline; filename=\"preview.pdf\"")
                    .body(pdfBytes);
        } catch (PdfConversionException e) {
            log.error("PDF 转换失败: id={}, filename={}", id, uf.getOriginalName(), e);
            throw e;
        }
    }

    @PutMapping("/{id}/adjust")
    public ApiResponse<Void> adjust(@PathVariable Long id, @Valid @RequestBody AdjustRequestDTO dto) {
        log.info("调整文件: id={}", id);
        wordParseService.saveAdjust(id, dto.getAdjustJson());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/template")
    public ApiResponse<Void> updateTemplate(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long templateId = body.get("templateId");
        log.info("更新文件模板: id={}", id);
        wordParseService.updateTemplate(id, templateId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/export")
    public ApiResponse<UploadFile> export(@PathVariable Long id) {
        log.info("导出文件: id={}", id);
        UploadFile uf = wordParseService.export(id);
        return ApiResponse.ok(uf);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        log.info("下载文件: id={}", id);
        byte[] content = wordParseService.download(id);
        UploadFile uf = wordParseService.getFile(id);

        String filename = uf.getOutputName() != null ? uf.getOutputName() : "exported.docx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("删除文件: id={}", id);
        wordParseService.delete(id);
        return ApiResponse.ok(null);
    }
}
