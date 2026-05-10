package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.EngineConfigDTO;
import com.template.dto.TemplateConfigDTO;
import com.template.dto.TemplateRuleDTO;
import com.template.entity.EngineConfig;
import com.template.entity.TemplateConfig;
import com.template.entity.TemplateRule;
import com.template.mapper.EngineConfigMapper;
import com.template.mapper.TemplateConfigMapper;
import com.template.mapper.TemplateRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TemplateServiceTest — P0/P1 Service 单元测试
 *
 * 使用 Mockito 模拟 Mapper 层，验证模板 CRUD、规则 CRUD、引擎配置 CRUD 业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateConfigMapper templateConfigMapper;

    @Mock
    private TemplateRuleMapper templateRuleMapper;

    @Mock
    private EngineConfigMapper engineConfigMapper;

    private TemplateService templateService;

    @Captor
    private ArgumentCaptor<TemplateConfig> templateConfigCaptor;

    @Captor
    private ArgumentCaptor<TemplateRule> templateRuleCaptor;

    @Captor
    private ArgumentCaptor<EngineConfig> engineConfigCaptor;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(templateConfigMapper, templateRuleMapper, engineConfigMapper);
    }

    // ==================== 模板 CRUD ====================

    @Test
    @DisplayName("创建模板成功，isActive 默认为 1")
    void testCreateTemplate() {
        String name = "测试模板";
        when(templateConfigMapper.insert(any(TemplateConfig.class))).thenReturn(1);

        TemplateConfig result = templateService.create(name);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getIsActive()).isEqualTo(1);

        verify(templateConfigMapper).insert(templateConfigCaptor.capture());
        TemplateConfig captured = templateConfigCaptor.getValue();
        assertThat(captured.getName()).isEqualTo(name);
        assertThat(captured.getIsActive()).isEqualTo(1);
    }

    @Test
    @DisplayName("查询模板详情 — 模板存在时返回含规则和引擎配置的 DTO")
    void testDetailTemplateExists() {
        Long templateId = 1L;
        TemplateConfig config = new TemplateConfig();
        config.setId(templateId);
        config.setName("测试模板");
        config.setIsActive(1);

        TemplateRule rule = new TemplateRule();
        rule.setId(10L);
        rule.setTemplateId(templateId);
        rule.setName("正文");
        rule.setFontName("宋体");

        EngineConfig engineConfig = new EngineConfig();
        engineConfig.setId(100L);
        engineConfig.setTemplateId(templateId);
        engineConfig.setConfigName("正文匹配");
        engineConfig.setPattern("^[^#@].*");
        engineConfig.setMatchType("BODY");
        engineConfig.setSortOrder(1);
        engineConfig.setIsActive(1);

        when(templateConfigMapper.selectById(templateId)).thenReturn(config);
        when(templateRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule));
        when(engineConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(engineConfig));

        TemplateConfigDTO dto = templateService.detail(templateId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(templateId);
        assertThat(dto.getName()).isEqualTo("测试模板");
        assertThat(dto.getIsActive()).isEqualTo(1);
        assertThat(dto.getRules()).hasSize(1);
        assertThat(dto.getRules().get(0).getName()).isEqualTo("正文");
        assertThat(dto.getEngineConfigs()).hasSize(1);
        assertThat(dto.getEngineConfigs().get(0).getPattern()).isEqualTo("^[^#@].*");
        assertThat(dto.getEngineConfigs().get(0).getMatchType()).isEqualTo("BODY");
    }

    @Test
    @DisplayName("查询模板详情 — 模板不存在时返回 null")
    void testDetailTemplateNotExists() {
        Long templateId = 999L;
        when(templateConfigMapper.selectById(templateId)).thenReturn(null);

        TemplateConfigDTO dto = templateService.detail(templateId);

        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("分页查询模板列表，按创建时间倒序")
    void testListTemplates() {
        int page = 1;
        int size = 10;

        TemplateConfig config = new TemplateConfig();
        config.setId(1L);
        config.setName("模板1");

        Page<TemplateConfig> mockPage = new Page<>(page, size);
        mockPage.setRecords(List.of(config));
        mockPage.setTotal(1);

        when(templateConfigMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<TemplateConfig> result = templateService.list(page, size);

        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("模板1");
        assertThat(result.getTotal()).isEqualTo(1);

        verify(templateConfigMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("更新模板成功")
    void testUpdateTemplateSuccess() {
        Long templateId = 1L;
        String newName = "新名称";

        TemplateConfig existing = new TemplateConfig();
        existing.setId(templateId);
        existing.setName("旧名称");
        existing.setIsActive(1);

        when(templateConfigMapper.selectById(templateId)).thenReturn(existing);
        when(templateConfigMapper.updateById(any(TemplateConfig.class))).thenReturn(1);

        TemplateConfig result = templateService.update(templateId, newName);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(newName);

        verify(templateConfigMapper).updateById(templateConfigCaptor.capture());
        assertThat(templateConfigCaptor.getValue().getName()).isEqualTo(newName);
    }

    @Test
    @DisplayName("更新模板 — 模板不存在时返回 null")
    void testUpdateTemplateNotExists() {
        Long templateId = 999L;
        when(templateConfigMapper.selectById(templateId)).thenReturn(null);

        TemplateConfig result = templateService.update(templateId, "新名称");

        assertThat(result).isNull();
        verify(templateConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("切换启用状态 1 -> 0 -> 1")
    void testToggleActiveState() {
        Long templateId = 1L;

        TemplateConfig config = new TemplateConfig();
        config.setId(templateId);
        config.setIsActive(1);

        when(templateConfigMapper.selectById(templateId)).thenReturn(config);

        // 第一次切换 1 -> 0
        templateService.toggle(templateId);
        verify(templateConfigMapper, times(1)).updateById(templateConfigCaptor.capture());
        assertThat(templateConfigCaptor.getValue().getIsActive()).isEqualTo(0);

        // 第二次切换 0 -> 1
        config.setIsActive(0);
        templateService.toggle(templateId);
        verify(templateConfigMapper, times(2)).updateById(templateConfigCaptor.capture());
        assertThat(templateConfigCaptor.getAllValues().get(1).getIsActive()).isEqualTo(1);
    }

    // ==================== 规则 CRUD ====================

    @Test
    @DisplayName("创建规则成功")
    void testCreateRule() {
        Long templateId = 1L;
        TemplateRuleDTO dto = new TemplateRuleDTO();
        dto.setName("正文样式");
        dto.setFontName("宋体");
        dto.setFontSize(12);
        dto.setFontBold(0);
        dto.setTextAlign("LEFT");
        dto.setTextIndent(new BigDecimal("2"));

        when(templateRuleMapper.insert(any(TemplateRule.class))).thenReturn(1);

        TemplateRule result = templateService.createRule(templateId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getTemplateId()).isEqualTo(templateId);
        assertThat(result.getName()).isEqualTo("正文样式");

        verify(templateRuleMapper).insert(templateRuleCaptor.capture());
        TemplateRule captured = templateRuleCaptor.getValue();
        assertThat(captured.getTemplateId()).isEqualTo(templateId);
        assertThat(captured.getName()).isEqualTo("正文样式");
        assertThat(captured.getFontName()).isEqualTo("宋体");
        assertThat(captured.getId()).isNull();
    }

    @Test
    @DisplayName("查询规则 — 存在时返回规则")
    void testGetRuleExists() {
        Long ruleId = 10L;
        TemplateRule rule = new TemplateRule();
        rule.setId(ruleId);
        rule.setName("标题样式");

        when(templateRuleMapper.selectById(ruleId)).thenReturn(rule);

        TemplateRule result = templateService.getRule(ruleId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("标题样式");
    }

    @Test
    @DisplayName("查询规则 — 不存在时返回 null")
    void testGetRuleNotExists() {
        Long ruleId = 999L;
        when(templateRuleMapper.selectById(ruleId)).thenReturn(null);

        TemplateRule result = templateService.getRule(ruleId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("获取规则列表返回所属模板的所有规则")
    void testListRules() {
        Long templateId = 1L;
        TemplateRule rule1 = new TemplateRule();
        rule1.setId(1L);
        rule1.setTemplateId(templateId);
        rule1.setName("正文");

        TemplateRule rule2 = new TemplateRule();
        rule2.setId(2L);
        rule2.setTemplateId(templateId);
        rule2.setName("标题");

        when(templateRuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rule1, rule2));

        List<TemplateRule> rules = templateService.listRules(templateId);

        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(TemplateRule::getName).containsExactly("正文", "标题");
    }

    @Test
    @DisplayName("更新规则成功")
    void testUpdateRuleSuccess() {
        Long ruleId = 10L;
        TemplateRuleDTO dto = new TemplateRuleDTO();
        dto.setName("新样式名称");
        dto.setFontSize(14);
        dto.setFontBold(1);

        TemplateRule existing = new TemplateRule();
        existing.setId(ruleId);
        existing.setName("旧样式");
        existing.setFontSize(12);

        when(templateRuleMapper.selectById(ruleId)).thenReturn(existing);
        when(templateRuleMapper.updateById(any(TemplateRule.class))).thenReturn(1);

        TemplateRule result = templateService.updateRule(ruleId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("新样式名称");
        assertThat(result.getFontSize()).isEqualTo(14);
        assertThat(result.getFontBold()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新规则 — 不存在时返回 null")
    void testUpdateRuleNotExists() {
        Long ruleId = 999L;
        when(templateRuleMapper.selectById(ruleId)).thenReturn(null);

        TemplateRule result = templateService.updateRule(ruleId, new TemplateRuleDTO());

        assertThat(result).isNull();
        verify(templateRuleMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除规则成功")
    void testDeleteRule() {
        Long ruleId = 10L;
        when(templateRuleMapper.deleteById(ruleId)).thenReturn(1);

        templateService.deleteRule(ruleId);

        verify(templateRuleMapper).deleteById(ruleId);
    }

    // ==================== 引擎配置 CRUD（新接口） ====================

    @Test
    @DisplayName("创建引擎配置成功")
    void testCreateEngineConfig() {
        Long templateId = 1L;
        EngineConfigDTO dto = new EngineConfigDTO();
        dto.setConfigName("封面匹配");
        dto.setPattern("^摘要$");
        dto.setMatchType("COVER");
        dto.setSortOrder(1);
        dto.setIsActive(1);

        when(engineConfigMapper.insert(any(EngineConfig.class))).thenReturn(1);

        EngineConfigDTO result = templateService.createEngineConfig(templateId, dto);

        assertThat(result).isNotNull();
        assertThat(result.getTemplateId()).isEqualTo(templateId);
        assertThat(result.getConfigName()).isEqualTo("封面匹配");
        assertThat(result.getPattern()).isEqualTo("^摘要$");
        assertThat(result.getMatchType()).isEqualTo("COVER");

        verify(engineConfigMapper).insert(engineConfigCaptor.capture());
        EngineConfig captured = engineConfigCaptor.getValue();
        assertThat(captured.getTemplateId()).isEqualTo(templateId);
        assertThat(captured.getConfigName()).isEqualTo("封面匹配");
        assertThat(captured.getPattern()).isEqualTo("^摘要$");
        assertThat(captured.getMatchType()).isEqualTo("COVER");
        assertThat(captured.getId()).isNull();
    }

    @Test
    @DisplayName("查询引擎配置列表 — 按 match_type + sort_order 排序")
    void testListEngineConfigs() {
        Long templateId = 1L;

        EngineConfig cfg1 = new EngineConfig();
        cfg1.setId(1L);
        cfg1.setTemplateId(templateId);
        cfg1.setConfigName("正文匹配");
        cfg1.setPattern(".*");
        cfg1.setMatchType("BODY");
        cfg1.setSortOrder(1);
        cfg1.setIsActive(1);

        EngineConfig cfg2 = new EngineConfig();
        cfg2.setId(2L);
        cfg2.setTemplateId(templateId);
        cfg2.setConfigName("封面匹配");
        cfg2.setPattern("^摘要$");
        cfg2.setMatchType("COVER");
        cfg2.setSortOrder(1);
        cfg2.setIsActive(1);

        when(engineConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cfg1, cfg2));

        Page<EngineConfigDTO> result = templateService.listEngineConfigs(templateId, 1, 10);

        // 应返回两条，按 COVER 在前，BODY 在后排序
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getMatchType()).isEqualTo("COVER");
        assertThat(result.getRecords().get(1).getMatchType()).isEqualTo("BODY");
    }

    @Test
    @DisplayName("查询引擎配置列表 — 返回空列表")
    void testListEngineConfigsEmpty() {
        Long templateId = 1L;
        when(engineConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Page<EngineConfigDTO> result = templateService.listEngineConfigs(templateId, 1, 10);

        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("查询单条引擎配置 — 存在时返回")
    void testGetEngineConfigExists() {
        Long id = 100L;
        EngineConfig config = new EngineConfig();
        config.setId(id);
        config.setTemplateId(1L);
        config.setConfigName("封面匹配");
        config.setPattern("^摘要$");
        config.setMatchType("COVER");
        config.setSortOrder(1);
        config.setIsActive(1);

        TemplateRule rule = new TemplateRule();
        rule.setId(1L);
        rule.setName("封面样式");

        config.setRuleId(1L);

        when(engineConfigMapper.selectById(id)).thenReturn(config);
        when(templateRuleMapper.selectById(1L)).thenReturn(rule);

        EngineConfigDTO result = templateService.getEngineConfig(id);

        assertThat(result).isNotNull();
        assertThat(result.getConfigName()).isEqualTo("封面匹配");
        assertThat(result.getPattern()).isEqualTo("^摘要$");
        assertThat(result.getMatchType()).isEqualTo("COVER");
        assertThat(result.getRuleName()).isEqualTo("封面样式");
    }

    @Test
    @DisplayName("查询单条引擎配置 — 不存在时返回 null")
    void testGetEngineConfigNotExists() {
        Long id = 999L;
        when(engineConfigMapper.selectById(id)).thenReturn(null);

        EngineConfigDTO result = templateService.getEngineConfig(id);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("更新引擎配置成功")
    void testUpdateEngineConfig() {
        Long id = 100L;
        EngineConfigDTO dto = new EngineConfigDTO();
        dto.setConfigName("封面匹配（更新版）");
        dto.setPattern("^（摘\\s*要）$");
        dto.setMatchType("COVER");
        dto.setSortOrder(2);
        dto.setIsActive(1);

        EngineConfig existing = new EngineConfig();
        existing.setId(id);
        existing.setTemplateId(1L);
        existing.setConfigName("封面匹配");
        existing.setPattern("^摘要$");
        existing.setMatchType("COVER");
        existing.setSortOrder(1);
        existing.setIsActive(1);

        when(engineConfigMapper.selectById(id)).thenReturn(existing);
        when(engineConfigMapper.updateById(any(EngineConfig.class))).thenReturn(1);

        EngineConfigDTO result = templateService.updateEngineConfig(id, dto);

        assertThat(result).isNotNull();
        assertThat(result.getConfigName()).isEqualTo("封面匹配（更新版）");
        assertThat(result.getPattern()).isEqualTo("^（摘\\s*要）$");
        assertThat(result.getSortOrder()).isEqualTo(2);

        verify(engineConfigMapper).updateById(engineConfigCaptor.capture());
        EngineConfig captured = engineConfigCaptor.getValue();
        assertThat(captured.getConfigName()).isEqualTo("封面匹配（更新版）");
        assertThat(captured.getPattern()).isEqualTo("^（摘\\s*要）$");
        assertThat(captured.getId()).isEqualTo(100L);
        assertThat(captured.getTemplateId()).isEqualTo(1L); // templateId 不应被覆盖
    }

    @Test
    @DisplayName("更新引擎配置 — 不存在时返回 null")
    void testUpdateEngineConfigNotExists() {
        Long id = 999L;
        when(engineConfigMapper.selectById(id)).thenReturn(null);

        EngineConfigDTO result = templateService.updateEngineConfig(id, new EngineConfigDTO());

        assertThat(result).isNull();
        verify(engineConfigMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("删除引擎配置成功")
    void testDeleteEngineConfig() {
        Long id = 100L;
        when(engineConfigMapper.deleteById(id)).thenReturn(1);

        templateService.deleteEngineConfig(id);

        verify(engineConfigMapper).deleteById(id);
    }
}
