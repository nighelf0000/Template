package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.AdjustRequestDTO;
import com.template.dto.ApiResponse;
import com.template.dto.PreviewResultDTO;
import com.template.entity.UploadFile;
import com.template.mapper.UploadFileMapper;
import com.template.service.WordParseService;
import com.template.service.pdf.PdfConversionService;
import com.template.service.pdf.PdfConversionException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WordControllerTest — P0 Controller 层测试
 *
 * 覆盖 TC-020 ~ TC-036（文件上传、解析、预览、调整、导出、下载）。
 * 使用 Apache POI 动态生成合法 .docx 文件作为 MockMultipartFile。
 */
@WebMvcTest(WordController.class)
@ActiveProfiles("test")
class WordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WordParseService wordParseService;

    @MockBean
    private UploadFileMapper uploadFileMapper;

    @MockBean
    private PdfConversionService pdfConversionService;

    private UploadFile sampleFile;
    private byte[] validDocxBytes;

    @BeforeEach
    void setUp() throws Exception {
        // 生成合法的 .docx 文件内容
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("测试文档内容");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        doc.close();
        validDocxBytes = baos.toByteArray();

        sampleFile = new UploadFile();
        sampleFile.setId(1L);
        sampleFile.setTemplateId(1L);
        sampleFile.setOriginalName("test.docx");
        sampleFile.setOriginalSize((long) validDocxBytes.length);
        sampleFile.setOriginalContent(validDocxBytes);
        sampleFile.setStatus("UPLOADED");
        sampleFile.setCreatedAt(LocalDateTime.now());
    }

    // ==================== 文件列表（TC-020）====================

    @Test
    @DisplayName("TC-020: GET /api/word/list — 文件列表查询")
    void testListFiles() throws Exception {
        Page<UploadFile> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleFile));
        page.setTotal(1);
        when(wordParseService.list(1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/word/list")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].originalName").value("test.docx"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ==================== 文件上传（TC-021 ~ TC-023）====================

    @Test
    @DisplayName("TC-021: POST /api/word/upload — 上传 .docx 文件成功")
    void testUploadDocxSuccess() throws Exception {
        UploadFile uploaded = new UploadFile();
        uploaded.setId(2L);
        uploaded.setOriginalName("upload.docx");
        uploaded.setOriginalSize((long) validDocxBytes.length);
        uploaded.setStatus("UPLOADED");
        uploaded.setTemplateId(1L);

        when(wordParseService.upload(any(), eq(1L))).thenReturn(uploaded);

        MockMultipartFile file = new MockMultipartFile(
                "file", "upload.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                validDocxBytes);

        mockMvc.perform(multipart("/api/word/upload")
                        .file(file)
                        .param("templateId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.originalName").value("upload.docx"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"));

        verify(wordParseService).upload(any(), eq(1L));
    }

    @Test
    @DisplayName("TC-022: POST /api/word/upload — 上传空文件返回 400")
    void testUploadEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[0]);

        mockMvc.perform(multipart("/api/word/upload")
                        .file(emptyFile)
                        .param("templateId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件为空"));

        verify(wordParseService, never()).upload(any(), any());
    }

    @Test
    @DisplayName("TC-023: POST /api/word/upload — 上传非 .docx 文件返回 400")
    void testUploadNonDocxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "text content".getBytes());

        mockMvc.perform(multipart("/api/word/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 .docx 格式文件"));

        verify(wordParseService, never()).upload(any(), any());
    }

    // ==================== 文件解析（TC-024 ~ TC-026）====================

    @Test
    @DisplayName("TC-024: POST /api/word/{id}/parse — 解析文件成功")
    void testParseSuccess() throws Exception {
        UploadFile parsed = new UploadFile();
        parsed.setId(1L);
        parsed.setStatus("PARSED");
        parsed.setParsedJson("[]");
        parsed.setParsedAt(LocalDateTime.now());

        when(wordParseService.parse(1L)).thenReturn(parsed);

        mockMvc.perform(post("/api/word/1/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PARSED"));
    }

    @Test
    @DisplayName("TC-025: POST /api/word/{id}/parse — 解析文件不存在由 service 抛出异常")
    void testParseFileNotExists() throws Exception {
        when(wordParseService.parse(999L)).thenThrow(new RuntimeException("文件不存在"));

        mockMvc.perform(post("/api/word/999/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文件不存在"));
    }

    @Test
    @DisplayName("TC-026: POST /api/word/{id}/parse — 解析过程抛出异常")
    void testParseException() throws Exception {
        when(wordParseService.parse(1L)).thenThrow(new RuntimeException("解析失败: 文件格式错误"));

        mockMvc.perform(post("/api/word/1/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("解析失败: 文件格式错误"));
    }

    // ==================== 预览（TC-027 ~ TC-029）====================

    @Test
    @DisplayName("TC-027: GET /api/word/{id}/preview — 预览文件成功")
    void testPreviewSuccess() throws Exception {
        PreviewResultDTO previewResult = new PreviewResultDTO();
        previewResult.setTemplateId(1L);
        previewResult.setTemplateName("测试模板");
        previewResult.setPdfUrl("/api/word/1/preview/pdf");
        when(wordParseService.preview(1L)).thenReturn(previewResult);

        mockMvc.perform(get("/api/word/1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.templateId").value(1))
                .andExpect(jsonPath("$.data.templateName").value("测试模板"))
                .andExpect(jsonPath("$.data.pdfUrl").value("/api/word/1/preview/pdf"));
    }

    @Test
    @DisplayName("TC-028: GET /api/word/{id}/preview — 预览文件不存在")
    void testPreviewNotExists() throws Exception {
        when(wordParseService.preview(999L)).thenThrow(new RuntimeException("文件不存在"));

        mockMvc.perform(get("/api/word/999/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文件不存在"));
    }

    @Test
    @DisplayName("TC-029: GET /api/word/{id}/preview — 预览过程抛出异常")
    void testPreviewException() throws Exception {
        when(wordParseService.preview(1L)).thenThrow(new RuntimeException("预览生成失败"));

        mockMvc.perform(get("/api/word/1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("预览生成失败"));
    }

    // ==================== 调整（TC-030 ~ TC-032）====================

    @Test
    @DisplayName("TC-030: PUT /api/word/{id}/adjust — 调整保存成功")
    void testAdjustSuccess() throws Exception {
        doNothing().when(wordParseService).saveAdjust(eq(1L), anyString());

        String adjustJson = "[{\"index\":0,\"text\":\"调整后\",\"ruleId\":10}]";
        AdjustRequestDTO dto = new AdjustRequestDTO();
        dto.setAdjustJson(adjustJson);
        String requestBody = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/api/word/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("TC-031: PUT /api/word/{id}/adjust — 调整保存文件不存在")
    void testAdjustNotExists() throws Exception {
        doThrow(new RuntimeException("文件不存在")).when(wordParseService).saveAdjust(eq(999L), anyString());

        AdjustRequestDTO dto = new AdjustRequestDTO();
        dto.setAdjustJson("[]");
        String requestBody = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/api/word/999/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文件不存在"));
    }

    @Test
    @DisplayName("TC-032: PUT /api/word/{id}/adjust — 调整保存抛出异常")
    void testAdjustException() throws Exception {
        doThrow(new RuntimeException("调整保存失败")).when(wordParseService).saveAdjust(eq(1L), anyString());

        AdjustRequestDTO dto = new AdjustRequestDTO();
        dto.setAdjustJson("{\"invalid\": true}");
        String requestBody = objectMapper.writeValueAsString(dto);

        mockMvc.perform(put("/api/word/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("调整保存失败"));
    }

    // ==================== 导出（TC-033 ~ TC-034）====================

    @Test
    @DisplayName("TC-033: POST /api/word/{id}/export — 导出成功")
    void testExportSuccess() throws Exception {
        UploadFile exported = new UploadFile();
        exported.setId(1L);
        exported.setStatus("EXPORTED");
        exported.setOutputName("test_格式化.docx");
        exported.setExportedAt(LocalDateTime.now());

        when(wordParseService.export(1L)).thenReturn(exported);

        mockMvc.perform(post("/api/word/1/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("EXPORTED"))
                .andExpect(jsonPath("$.data.outputName").value("test_格式化.docx"));
    }

    @Test
    @DisplayName("TC-034: POST /api/word/{id}/export — 导出抛出异常")
    void testExportException() throws Exception {
        when(wordParseService.export(1L)).thenThrow(new RuntimeException("导出失败: 文件损坏"));

        mockMvc.perform(post("/api/word/1/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("导出失败: 文件损坏"));
    }

    // ==================== 下载（TC-035 ~ TC-036）====================

    @Test
    @DisplayName("TC-035: GET /api/word/{id}/download — 下载成功返回文件内容")
    void testDownloadSuccess() throws Exception {
        byte[] outputContent = "fake-binary-content".getBytes();
        UploadFile uf = new UploadFile();
        uf.setId(1L);
        uf.setOutputName("test_格式化.docx");

        when(wordParseService.download(1L)).thenReturn(outputContent);
        when(wordParseService.getFile(1L)).thenReturn(uf);

        mockMvc.perform(get("/api/word/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''test_%E6%A0%BC%E5%BC%8F%E5%8C%96.docx"))
                // Spring MVC 默认在 ContentType 后追加 ;charset=UTF-8，使用兼容匹配
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(outputContent));
    }

    @Test
    @DisplayName("TC-036: GET /api/word/{id}/download — 下载文件不存在返回业务错误")
    void testDownloadNotExists() throws Exception {
        when(wordParseService.download(999L)).thenThrow(new RuntimeException("导出文件不存在"));

        mockMvc.perform(get("/api/word/999/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("导出文件不存在"));
    }
}
