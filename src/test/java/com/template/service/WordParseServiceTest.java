package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.entity.*;
import com.template.mapper.*;
import com.template.service.pdf.PdfConversionService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WordParseServiceTest — P0/P1 Service 单元测试
 *
 * 使用 Mockito 模拟 Mapper 层和 RecognitionEngine。
 * 通过手动初始化 TableInfoHelper 支持 LambdaQueryWrapper.select(Class, Predicate)。
 */
@ExtendWith(MockitoExtension.class)
class WordParseServiceTest {

    @Mock
    private UploadFileMapper uploadFileMapper;

    @Mock
    private TemplateConfigMapper templateConfigMapper;

    @Mock
    private TemplateRuleMapper templateRuleMapper;

    @Mock
    private EngineConfigMapper engineConfigMapper;

    @Mock
    private RecognitionEngine recognitionEngine;

    @Mock
    private PdfConversionService pdfConversionService;

    private ObjectMapper objectMapper;

    private WordParseService wordParseService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        wordParseService = new WordParseService(
                uploadFileMapper, templateConfigMapper, templateRuleMapper,
                engineConfigMapper, recognitionEngine, objectMapper, pdfConversionService);
    }

    // ========== 辅助方法 ==========

    private byte[] createMinimalDocx() throws IOException {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("测试内容");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        doc.close();
        return baos.toByteArray();
    }

    // ========== 文件列表 ==========

    @Test
    @DisplayName("文件列表分页查询成功")
    void testListFiles() {
        int page = 1;
        int size = 10;

        UploadFile uf = new UploadFile();
        uf.setId(1L);
        uf.setOriginalName("test.docx");
        uf.setStatus("UPLOADED");

        Page<UploadFile> mockPage = new Page<>(page, size);
        mockPage.setRecords(List.of(uf));
        mockPage.setTotal(1);

        when(uploadFileMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        // 使用 Mockito mockStatic 模拟 TableInfoHelper，
        // 避免 LambdaQueryWrapper.select(Class, Predicate) 中 TableInfoHelper.getTableInfo() NPE
        try (MockedStatic<com.baomidou.mybatisplus.core.metadata.TableInfoHelper> mockedHelper = mockStatic(com.baomidou.mybatisplus.core.metadata.TableInfoHelper.class)) {
            TableInfo mockTableInfo = mock(TableInfo.class);
            when(mockTableInfo.chooseSelect(any())).thenReturn("*");
            mockedHelper.when(() -> com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(UploadFile.class))
                    .thenReturn(mockTableInfo);

            Page<UploadFile> result = wordParseService.list(page, size);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getOriginalName()).isEqualTo("test.docx");
        }
    }

    // ========== 文件上传 ==========

    @Test
    @DisplayName("上传 .docx 文件成功")
    void testUploadSuccess() throws IOException {
        byte[] content = createMinimalDocx();
        MultipartFile file = new MockMultipartFile("file", "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);

        Long templateId = 1L;
        when(uploadFileMapper.insert(any(UploadFile.class))).thenReturn(1);

        UploadFile result = wordParseService.upload(file, templateId);

        assertThat(result).isNotNull();
        assertThat(result.getTemplateId()).isEqualTo(templateId);
        assertThat(result.getOriginalName()).isEqualTo("test.docx");
        assertThat(result.getOriginalSize()).isEqualTo(content.length);
        assertThat(result.getOriginalContent()).isEqualTo(content);
        assertThat(result.getStatus()).isEqualTo("UPLOADED");

        ArgumentCaptor<UploadFile> captor = ArgumentCaptor.forClass(UploadFile.class);
        verify(uploadFileMapper).insert(captor.capture());
        UploadFile captured = captor.getValue();
        assertThat(captured.getTemplateId()).isEqualTo(templateId);
        assertThat(captured.getOriginalName()).isEqualTo("test.docx");
        assertThat(captured.getStatus()).isEqualTo("UPLOADED");
    }

    @Test
    @DisplayName("上传文件不带 templateId 时 templateId 为 null")
    void testUploadWithoutTemplateId() throws IOException {
        byte[] content = createMinimalDocx();
        MultipartFile file = new MockMultipartFile("file", "test.docx", "application/octet-stream", content);

        when(uploadFileMapper.insert(any(UploadFile.class))).thenReturn(1);

        UploadFile result = wordParseService.upload(file, null);

        assertThat(result).isNotNull();
        assertThat(result.getTemplateId()).isNull();
    }

    // ========== 文件解析 ==========

    @Test
    @DisplayName("解析文件成功，状态变更为 PARSED")
    void testParseSuccess() throws IOException {
        Long fileId = 1L;
        byte[] content = createMinimalDocx();

        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setTemplateId(1L);
        uf.setOriginalContent(content);
        uf.setStatus("UPLOADED");

        TemplateConfig config = new TemplateConfig();
        config.setId(1L);
        config.setName("测试模板");
        config.setIsActive(1);

        TemplateRule rule = new TemplateRule();
        rule.setId(10L);
        rule.setTemplateId(1L);
        rule.setName("正文");

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);
        when(templateConfigMapper.selectById(1L)).thenReturn(config);
        when(templateRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));
        when(recognitionEngine.recognize(any(), any(), anyList())).thenReturn(List.of());

        UploadFile result = wordParseService.parse(fileId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PARSED");
        assertThat(result.getParsedJson()).isNotNull();
        assertThat(result.getParsedAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();

        verify(uploadFileMapper, atLeastOnce()).updateById(any(UploadFile.class));
    }

    @Test
    @DisplayName("解析文件 — 文件不存在时抛出异常")
    void testParseFileNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.parse(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件不存在");
    }

    @Test
    @DisplayName("解析文件 — 未指定模板时抛出异常")
    void testParseNoTemplate() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setTemplateId(null);

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);

        assertThatThrownBy(() -> wordParseService.parse(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("未指定解析模板");
    }

    @Test
    @DisplayName("解析文件 — 模板停用时抛出异常")
    void testParseTemplateInactive() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setTemplateId(1L);

        TemplateConfig config = new TemplateConfig();
        config.setId(1L);
        config.setIsActive(0);

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);
        when(templateConfigMapper.selectById(1L)).thenReturn(config);

        assertThatThrownBy(() -> wordParseService.parse(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("模板不存在或已停用");
    }

    // ========== 预览 & 调整 ==========

    @Test
    @DisplayName("获取预览 JSON 成功")
    void testGetPreviewJsonSuccess() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setParsedJson("[{\"index\":0,\"text\":\"测试\",\"matchedType\":\"BODY\"}]");

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);

        String json = wordParseService.getPreviewJson(fileId);

        assertThat(json).isEqualTo(uf.getParsedJson());
    }

    @Test
    @DisplayName("获取预览 JSON — 文件不存在时抛出异常")
    void testGetPreviewJsonNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.getPreviewJson(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件不存在");
    }

    @Test
    @DisplayName("保存调整 JSON 成功，状态变更为 ADJUSTED")
    void testSaveAdjustSuccess() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setStatus("PARSED");
        uf.setTemplateId(1L);

        String adjustJson = "[{\"index\":0,\"text\":\"调整后内容\"}]";

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);
        when(templateRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        wordParseService.saveAdjust(fileId, adjustJson);

        ArgumentCaptor<UploadFile> captor = ArgumentCaptor.forClass(UploadFile.class);
        verify(uploadFileMapper).updateById(captor.capture());
        UploadFile captured = captor.getValue();
        // Jackson 默认会序列化所有字段，包括 null 字段
        assertThat(captured.getParsedJson()).contains("\"index\":0")
                .contains("\"text\":\"调整后内容\"");
        assertThat(captured.getStatus()).isEqualTo("ADJUSTED");
        assertThat(captured.getAdjustedAt()).isNotNull();
    }

    @Test
    @DisplayName("保存调整 — 文件不存在时抛出异常")
    void testSaveAdjustNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.saveAdjust(fileId, "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件不存在");
    }

    // ========== 导出 ==========

    @Test
    @DisplayName("导出文件成功，状态变更为 EXPORTED")
    void testExportSuccess() throws IOException {
        Long fileId = 1L;
        byte[] content = createMinimalDocx();

        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setTemplateId(1L);
        uf.setOriginalName("test.docx");
        uf.setOriginalContent(content);
        uf.setParsedJson("[]");
        uf.setStatus("PARSED");

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);
        when(templateRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        UploadFile result = wordParseService.export(fileId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("EXPORTED");
        assertThat(result.getOutputName()).endsWith("_格式化.docx");
        assertThat(result.getOutputContent()).isNotNull();
        assertThat(result.getExportedAt()).isNotNull();

        verify(uploadFileMapper, atLeastOnce()).updateById(any(UploadFile.class));
    }

    @Test
    @DisplayName("导出文件 — 文件不存在时抛出异常")
    void testExportNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.export(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件不存在");
    }

    // ========== 下载 ==========

    @Test
    @DisplayName("下载成功返回文件内容")
    void testDownloadSuccess() {
        Long fileId = 1L;
        byte[] content = new byte[]{1, 2, 3};
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setOutputContent(content);

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);

        byte[] result = wordParseService.download(fileId);

        assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("下载 — 文件不存在时抛出异常")
    void testDownloadNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.download(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("导出文件不存在");
    }

    @Test
    @DisplayName("下载 — 未导出时抛出异常")
    void testDownloadNotExported() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setOutputContent(null);

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);

        assertThatThrownBy(() -> wordParseService.download(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("导出文件不存在");
    }

    // ========== 文件删除 ==========

    @Test
    @DisplayName("删除文件成功")
    void testDeleteSuccess() {
        Long fileId = 1L;
        UploadFile uf = new UploadFile();
        uf.setId(fileId);
        uf.setOriginalName("test.docx");

        when(uploadFileMapper.selectById(fileId)).thenReturn(uf);

        wordParseService.delete(fileId);

        verify(uploadFileMapper).selectById(fileId);
        verify(uploadFileMapper).deleteById(fileId);
    }

    @Test
    @DisplayName("删除文件 — 文件不存在时抛出异常")
    void testDeleteNotExists() {
        Long fileId = 999L;
        when(uploadFileMapper.selectById(fileId)).thenReturn(null);

        assertThatThrownBy(() -> wordParseService.delete(fileId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文件不存在");

        verify(uploadFileMapper, never()).deleteById(anyLong());
    }
}
