package com.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ApiResponse;
import com.template.dto.EngineConfigDTO;
import com.template.dto.TemplateConfigDTO;
import com.template.dto.TemplateRuleDTO;
import com.template.entity.TemplateConfig;
import com.template.entity.TemplateRule;
import com.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    // ========== 模板 CRUD ==========

    @PostMapping
    public ApiResponse<TemplateConfig> create(@RequestBody Map<String, String> body) {
        log.info("创建模板: name={}", body.get("name"));
        TemplateConfig config = templateService.create(body.get("name"));
        return ApiResponse.ok(config);
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateConfigDTO> detail(@PathVariable Long id) {
        log.info("查询模板详情: id={}", id);
        TemplateConfigDTO dto = templateService.detail(id);
        if (dto == null) {
            return ApiResponse.fail(404, "模板不存在");
        }
        return ApiResponse.ok(dto);
    }

    @GetMapping("/list")
    public ApiResponse<Page<TemplateConfig>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询模板列表: page={}, size={}", page, size);
        Page<TemplateConfig> result = templateService.list(page, size);
        return ApiResponse.ok(result);
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateConfig> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        log.info("更新模板: id={}", id);
        TemplateConfig config = templateService.update(id, body.get("name"));
        if (config == null) {
            return ApiResponse.fail(404, "模板不存在");
        }
        return ApiResponse.ok(config);
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        log.info("切换模板状态: id={}", id);
        templateService.toggle(id);
        return ApiResponse.ok(null);
    }

    // ========== 规则 CRUD ==========

    @PostMapping("/{templateId}/rule")
    public ApiResponse<TemplateRule> createRule(@PathVariable Long templateId,
                                                 @RequestBody TemplateRuleDTO dto) {
        log.info("创建规则: templateId={}", templateId);
        TemplateRule rule = templateService.createRule(templateId, dto);
        return ApiResponse.ok(rule);
    }

    @GetMapping("/{templateId}/rule/{id}")
    public ApiResponse<TemplateRule> getRule(@PathVariable Long templateId, @PathVariable Long id) {
        log.info("查询规则: id={}, templateId={}", id, templateId);
        TemplateRule rule = templateService.getRule(id);
        if (rule == null) {
            return ApiResponse.fail(404, "规则不存在");
        }
        return ApiResponse.ok(rule);
    }

    @GetMapping("/{templateId}/rule/list")
    public ApiResponse<List<TemplateRule>> listRules(@PathVariable Long templateId) {
        log.info("查询规则列表: templateId={}", templateId);
        List<TemplateRule> rules = templateService.listRules(templateId);
        return ApiResponse.ok(rules);
    }

    @PutMapping("/{templateId}/rule/{id}")
    public ApiResponse<TemplateRule> updateRule(@PathVariable Long templateId,
                                                 @PathVariable Long id,
                                                 @RequestBody TemplateRuleDTO dto) {
        log.info("更新规则: id={}, templateId={}", id, templateId);
        TemplateRule rule = templateService.updateRule(id, dto);
        if (rule == null) {
            return ApiResponse.fail(404, "规则不存在");
        }
        return ApiResponse.ok(rule);
    }

    @DeleteMapping("/{templateId}/rule/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long templateId, @PathVariable Long id) {
        log.info("删除规则: id={}, templateId={}", id, templateId);
        templateService.deleteRule(id);
        return ApiResponse.ok(null);
    }

    // ========== 引擎配置 CRUD（新接口，engine-config 风格） ==========

    @PostMapping("/{templateId}/engine-config")
    public ApiResponse<EngineConfigDTO> createEngineConfig(@PathVariable Long templateId,
                                                            @RequestBody EngineConfigDTO dto) {
        log.info("新增引擎配置: templateId={}, configName={}", templateId, dto.getConfigName());
        EngineConfigDTO result = templateService.createEngineConfig(templateId, dto);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{templateId}/engine-config/list")
    public ApiResponse<Page<EngineConfigDTO>> listEngineConfigs(
            @PathVariable Long templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("查询引擎配置列表: templateId={}, page={}, size={}", templateId, page, size);
        Page<EngineConfigDTO> result = templateService.listEngineConfigs(templateId, page, size);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{templateId}/engine-config/{id}")
    public ApiResponse<EngineConfigDTO> getEngineConfig(@PathVariable Long templateId,
                                                         @PathVariable Long id) {
        log.info("查询引擎配置: id={}, templateId={}", id, templateId);
        EngineConfigDTO dto = templateService.getEngineConfig(id);
        if (dto == null) {
            return ApiResponse.fail(404, "引擎配置不存在");
        }
        return ApiResponse.ok(dto);
    }

    @PutMapping("/{templateId}/engine-config/{id}")
    public ApiResponse<EngineConfigDTO> updateEngineConfig(@PathVariable Long templateId,
                                                            @PathVariable Long id,
                                                            @RequestBody EngineConfigDTO dto) {
        log.info("更新引擎配置: id={}, templateId={}", id, templateId);
        EngineConfigDTO result = templateService.updateEngineConfig(id, dto);
        if (result == null) {
            return ApiResponse.fail(404, "引擎配置不存在");
        }
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{templateId}/engine-config/{id}")
    public ApiResponse<Void> deleteEngineConfig(@PathVariable Long templateId,
                                                 @PathVariable Long id) {
        log.info("删除引擎配置: id={}, templateId={}", id, templateId);
        templateService.deleteEngineConfig(id);
        return ApiResponse.ok(null);
    }

    // ========== 智能匹配规则查询 ==========

    /**
     * 查询模板的智能匹配规则列表（不分页）
     * GET /api/template/{templateId}/smart-rules
     */
    @GetMapping("/{templateId}/smart-rules")
    public ApiResponse<List<com.template.entity.SmartMatchRule>> listSmartRules(@PathVariable Long templateId) {
        log.info("查询智能匹配规则列表: templateId={}", templateId);
        List<com.template.entity.SmartMatchRule> rules = templateService.listSmartRules(templateId);
        return ApiResponse.ok(rules);
    }
}
