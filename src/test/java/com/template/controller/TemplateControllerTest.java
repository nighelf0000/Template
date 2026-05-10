package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.ApiResponse;
import com.template.dto.EngineConfigDTO;
import com.template.dto.TemplateConfigDTO;
import com.template.dto.TemplateRuleDTO;
import com.template.entity.EngineConfig;
import com.template.entity.TemplateConfig;
import com.template.entity.TemplateRule;
import com.template.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TemplateControllerTest — P0 Controller 层测试
 *
 * 覆盖 TC-001 ~ TC-019（模板 CRUD、规则 CRUD、引擎配置 CRUD）。
 * 使用 MockMvc 模拟 HTTP 请求，Mockito 模拟 TemplateService。
 */
@WebMvcTest(TemplateController.class)
@ActiveProfiles("test")
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TemplateService templateService;

    private TemplateConfig sampleConfig;
    private TemplateConfigDTO sampleConfigDTO;
    private TemplateRule sampleRule;
    private EngineConfigDTO sampleEngineDTO;

    @BeforeEach
    void setUp() {
        sampleConfig = new TemplateConfig();
        sampleConfig.setId(1L);
        sampleConfig.setName("测试模板");
        sampleConfig.setIsActive(1);
        sampleConfig.setCreatedAt(LocalDateTime.now());
        sampleConfig.setUpdatedAt(LocalDateTime.now());

        sampleRule = new TemplateRule();
        sampleRule.setId(10L);
        sampleRule.setTemplateId(1L);
        sampleRule.setName("正文样式");
        sampleRule.setFontName("宋体");
        sampleRule.setFontSize(12);

        sampleEngineDTO = new EngineConfigDTO();
        sampleEngineDTO.setId(100L);
        sampleEngineDTO.setTemplateId(1L);
        sampleEngineDTO.setConfigName("封面匹配");
        sampleEngineDTO.setPattern("^摘要$");
        sampleEngineDTO.setMatchType("COVER");
        sampleEngineDTO.setSortOrder(1);
        sampleEngineDTO.setIsActive(1);

        sampleConfigDTO = new TemplateConfigDTO();
        sampleConfigDTO.setId(1L);
        sampleConfigDTO.setName("测试模板");
        sampleConfigDTO.setIsActive(1);
    }

    // ==================== 模板 CRUD（TC-001 ~ TC-007, TC-018 ~ TC-019）====================

    @Test
    @DisplayName("TC-001: POST /api/template — 创建模板成功")
    void testCreateTemplate() throws Exception {
        when(templateService.create("新模板")).thenReturn(sampleConfig);

        mockMvc.perform(post("/api/template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新模板\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试模板"))
                .andExpect(jsonPath("$.data.isActive").value(1));

        verify(templateService).create("新模板");
    }

    @Test
    @DisplayName("TC-002: GET /api/template/{id} — 查询模板详情（存在）")
    void testDetailTemplateExists() throws Exception {
        sampleConfigDTO.setRules(List.of());
        sampleConfigDTO.setEngineConfigs(Collections.emptyList());
        when(templateService.detail(1L)).thenReturn(sampleConfigDTO);

        mockMvc.perform(get("/api/template/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试模板"));
    }

    @Test
    @DisplayName("TC-003: GET /api/template/{id} — 查询模板详情（不存在返回 404）")
    void testDetailTemplateNotExists() throws Exception {
        when(templateService.detail(999L)).thenReturn(null);

        mockMvc.perform(get("/api/template/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("模板不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("TC-004: GET /api/template/list — 分页查询模板列表")
    void testListTemplates() throws Exception {
        Page<TemplateConfig> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleConfig));
        page.setTotal(1);
        when(templateService.list(1, 10)).thenReturn(page);

        mockMvc.perform(get("/api/template/list")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("TC-005: PUT /api/template/{id} — 更新模板成功")
    void testUpdateTemplateSuccess() throws Exception {
        TemplateConfig updated = new TemplateConfig();
        updated.setId(1L);
        updated.setName("更新后的名称");
        updated.setIsActive(1);
        when(templateService.update(eq(1L), eq("更新后的名称"))).thenReturn(updated);

        mockMvc.perform(put("/api/template/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新后的名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("更新后的名称"));
    }

    @Test
    @DisplayName("TC-006: PUT /api/template/{id} — 更新模板不存在返回 404")
    void testUpdateTemplateNotExists() throws Exception {
        when(templateService.update(eq(999L), anyString())).thenReturn(null);

        mockMvc.perform(put("/api/template/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("模板不存在"));
    }

    @Test
    @DisplayName("TC-007: PUT /api/template/{id}/toggle — 切换启用状态")
    void testToggleTemplate() throws Exception {
        doNothing().when(templateService).toggle(1L);

        mockMvc.perform(put("/api/template/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).toggle(1L);
    }

    @Test
    @DisplayName("TC-018: POST /api/template — 创建模板名称为空字符串")
    void testCreateTemplateEmptyName() throws Exception {
        when(templateService.create("")).thenReturn(sampleConfig);

        mockMvc.perform(post("/api/template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).create("");
    }

    @Test
    @DisplayName("TC-019: GET /api/template/list — 分页查询使用默认参数")
    void testListTemplatesDefaultParams() throws Exception {
        Page<TemplateConfig> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(templateService.list(1, 10)).thenReturn(emptyPage);

        mockMvc.perform(get("/api/template/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).list(1, 10);
    }

    // ==================== 规则 CRUD（TC-008 ~ TC-014）====================

    @Test
    @DisplayName("TC-008: POST /api/template/{tid}/rule — 创建规则成功")
    void testCreateRule() throws Exception {
        when(templateService.createRule(eq(1L), any(TemplateRuleDTO.class))).thenReturn(sampleRule);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "name", "正文样式",
                "fontName", "宋体",
                "fontSize", 12
        ));

        mockMvc.perform(post("/api/template/1/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("正文样式"));

        verify(templateService).createRule(eq(1L), any(TemplateRuleDTO.class));
    }

    @Test
    @DisplayName("TC-009: GET /api/template/{tid}/rule/{id} — 查询规则存在")
    void testGetRuleExists() throws Exception {
        when(templateService.getRule(10L)).thenReturn(sampleRule);

        mockMvc.perform(get("/api/template/1/rule/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.name").value("正文样式"));
    }

    @Test
    @DisplayName("TC-010: GET /api/template/{tid}/rule/{id} — 查询规则不存在返回 404")
    void testGetRuleNotExists() throws Exception {
        when(templateService.getRule(999L)).thenReturn(null);

        mockMvc.perform(get("/api/template/1/rule/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("规则不存在"));
    }

    @Test
    @DisplayName("TC-011: GET /api/template/{tid}/rule/list — 获取规则列表")
    void testListRules() throws Exception {
        TemplateRule rule2 = new TemplateRule();
        rule2.setId(11L);
        rule2.setTemplateId(1L);
        rule2.setName("标题样式");
        rule2.setFontSize(16);

        when(templateService.listRules(1L)).thenReturn(List.of(sampleRule, rule2));

        mockMvc.perform(get("/api/template/1/rule/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("正文样式"))
                .andExpect(jsonPath("$.data[1].name").value("标题样式"));
    }

    @Test
    @DisplayName("TC-012: PUT /api/template/{tid}/rule/{id} — 更新规则成功")
    void testUpdateRuleSuccess() throws Exception {
        TemplateRule updated = new TemplateRule();
        updated.setId(10L);
        updated.setTemplateId(1L);
        updated.setName("更新后的规则");
        updated.setFontSize(14);

        when(templateService.updateRule(eq(10L), any(TemplateRuleDTO.class))).thenReturn(updated);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "name", "更新后的规则",
                "fontSize", 14
        ));

        mockMvc.perform(put("/api/template/1/rule/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("更新后的规则"));
    }

    @Test
    @DisplayName("TC-013: PUT /api/template/{tid}/rule/{id} — 更新规则不存在返回 404")
    void testUpdateRuleNotExists() throws Exception {
        when(templateService.updateRule(eq(999L), any(TemplateRuleDTO.class))).thenReturn(null);

        mockMvc.perform(put("/api/template/1/rule/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("规则不存在"));
    }

    @Test
    @DisplayName("TC-014: DELETE /api/template/{tid}/rule/{id} — 删除规则成功")
    void testDeleteRule() throws Exception {
        doNothing().when(templateService).deleteRule(10L);

        mockMvc.perform(delete("/api/template/1/rule/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).deleteRule(10L);
    }

    // ==================== 引擎配置 CRUD（TC-015 ~ TC-017 + 新增测试） ====================

    @Test
    @DisplayName("TC-015: POST /api/template/{tid}/engine-config — 新增引擎配置成功")
    void testCreateEngineConfig() throws Exception {
        when(templateService.createEngineConfig(eq(1L), any(EngineConfigDTO.class))).thenReturn(sampleEngineDTO);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "configName", "封面匹配",
                "pattern", "^摘要$",
                "matchType", "COVER",
                "sortOrder", 1,
                "isActive", 1
        ));

        mockMvc.perform(post("/api/template/1/engine-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.configName").value("封面匹配"))
                .andExpect(jsonPath("$.data.matchType").value("COVER"))
                .andExpect(jsonPath("$.data.pattern").value("^摘要$"));

        verify(templateService).createEngineConfig(eq(1L), any(EngineConfigDTO.class));
    }

    @Test
    @DisplayName("TC-016: GET /api/template/{tid}/engine-config/list — 查询引擎配置列表")
    void testListEngineConfigs() throws Exception {
        Page<EngineConfigDTO> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleEngineDTO));
        page.setTotal(1);
        when(templateService.listEngineConfigs(eq(1L), eq(1), eq(10))).thenReturn(page);

        mockMvc.perform(get("/api/template/1/engine-config/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(100))
                .andExpect(jsonPath("$.data.records[0].configName").value("封面匹配"))
                .andExpect(jsonPath("$.data.records[0].matchType").value("COVER"));
    }

    @Test
    @DisplayName("TC-017: GET /api/template/{tid}/engine-config/{id} — 查询单条引擎配置（存在）")
    void testGetEngineConfigExists() throws Exception {
        when(templateService.getEngineConfig(100L)).thenReturn(sampleEngineDTO);

        mockMvc.perform(get("/api/template/1/engine-config/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.configName").value("封面匹配"))
                .andExpect(jsonPath("$.data.matchType").value("COVER"));
    }

    @Test
    @DisplayName("TC-017b: GET /api/template/{tid}/engine-config/{id} — 查询引擎配置不存在返回 404")
    void testGetEngineConfigNotExists() throws Exception {
        when(templateService.getEngineConfig(999L)).thenReturn(null);

        mockMvc.perform(get("/api/template/1/engine-config/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("引擎配置不存在"));
    }

    @Test
    @DisplayName("TC-018b: PUT /api/template/{tid}/engine-config/{id} — 更新引擎配置成功")
    void testUpdateEngineConfig() throws Exception {
        EngineConfigDTO updated = new EngineConfigDTO();
        updated.setId(100L);
        updated.setTemplateId(1L);
        updated.setConfigName("封面匹配（更新版）");
        updated.setPattern("^（摘\\s*要）$");
        updated.setMatchType("COVER");
        updated.setSortOrder(2);
        updated.setIsActive(1);

        when(templateService.updateEngineConfig(eq(100L), any(EngineConfigDTO.class))).thenReturn(updated);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "configName", "封面匹配（更新版）",
                "pattern", "^（摘\\s*要）$",
                "matchType", "COVER",
                "sortOrder", 2,
                "isActive", 1
        ));

        mockMvc.perform(put("/api/template/1/engine-config/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.configName").value("封面匹配（更新版）"))
                .andExpect(jsonPath("$.data.pattern").value("^（摘\\s*要）$"));

        verify(templateService).updateEngineConfig(eq(100L), any(EngineConfigDTO.class));
    }

    @Test
    @DisplayName("TC-018c: PUT /api/template/{tid}/engine-config/{id} — 更新引擎配置不存在返回 404")
    void testUpdateEngineConfigNotExists() throws Exception {
        when(templateService.updateEngineConfig(eq(999L), any(EngineConfigDTO.class))).thenReturn(null);

        mockMvc.perform(put("/api/template/1/engine-config/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configName\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("引擎配置不存在"));
    }

    @Test
    @DisplayName("TC-019b: DELETE /api/template/{tid}/engine-config/{id} — 删除引擎配置成功")
    void testDeleteEngineConfig() throws Exception {
        doNothing().when(templateService).deleteEngineConfig(100L);

        mockMvc.perform(delete("/api/template/1/engine-config/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(templateService).deleteEngineConfig(100L);
    }
}
