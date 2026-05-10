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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateConfigMapper templateConfigMapper;
    private final TemplateRuleMapper templateRuleMapper;
    private final EngineConfigMapper engineConfigMapper;

    public Page<TemplateConfig> list(int page, int size) {
        LambdaQueryWrapper<TemplateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TemplateConfig::getCreatedAt);
        return templateConfigMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public TemplateConfigDTO detail(Long id) {
        TemplateConfig config = templateConfigMapper.selectById(id);
        if (config == null) {
            log.error("模板不存在: id={}", id);
            return null;
        }
        TemplateConfigDTO dto = new TemplateConfigDTO();
        dto.setId(config.getId());
        dto.setName(config.getName());
        dto.setIsActive(config.getIsActive());

        // 查询关联的规则列表
        LambdaQueryWrapper<TemplateRule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(TemplateRule::getTemplateId, id);
        List<TemplateRule> rules = templateRuleMapper.selectList(ruleWrapper);
        List<TemplateRuleDTO> ruleDTOs = rules.stream().map(rule -> {
            TemplateRuleDTO rd = new TemplateRuleDTO();
            BeanUtils.copyProperties(rule, rd);
            return rd;
        }).collect(Collectors.toList());
        dto.setRules(ruleDTOs);

        // 查询关联的引擎配置列表（按 match_type + sort_order 排序）
        LambdaQueryWrapper<EngineConfig> engineWrapper = new LambdaQueryWrapper<>();
        engineWrapper.eq(EngineConfig::getTemplateId, id);
        List<EngineConfig> engineConfigs = engineConfigMapper.selectList(engineWrapper);
        if (engineConfigs != null && !engineConfigs.isEmpty()) {
            List<EngineConfigDTO> edList = engineConfigs.stream().map(ec -> {
                EngineConfigDTO ed = new EngineConfigDTO();
                BeanUtils.copyProperties(ec, ed);
                // 填充规则名称
                if (ec.getRuleId() != null) {
                    TemplateRule rule = templateRuleMapper.selectById(ec.getRuleId());
                    if (rule != null) {
                        ed.setRuleName(rule.getName());
                    }
                }
                return ed;
            }).collect(Collectors.toList());
            // 排序：SPECIAL(0) < COVER(1) < TOC(2) < TITLE(3) < BODY(4)，同类型内按 sort_order 升序
            edList.sort(Comparator.comparingInt((EngineConfigDTO e) -> {
                switch (e.getMatchType()) {
                    case "SPECIAL": return 0;
                    case "COVER":  return 1;
                    case "TOC":    return 2;
                    case "TITLE":  return 3;
                    case "BODY":   return 4;
                    default:       return 5;
                }
            }).thenComparingInt(e -> e.getSortOrder() != null ? e.getSortOrder() : 0));
            dto.setEngineConfigs(edList);
        } else {
            dto.setEngineConfigs(Collections.emptyList());
        }

        return dto;
    }

    @Transactional
    public TemplateConfig create(String name) {
        TemplateConfig config = new TemplateConfig();
        config.setName(name);
        config.setIsActive(1);
        templateConfigMapper.insert(config);
        return config;
    }

    @Transactional
    public TemplateConfig update(Long id, String name) {
        TemplateConfig config = templateConfigMapper.selectById(id);
        if (config != null) {
            config.setName(name);
            templateConfigMapper.updateById(config);
        } else {
            log.error("模板不存在: id={}", id);
        }
        return config;
    }

    @Transactional
    public void toggle(Long id) {
        TemplateConfig config = templateConfigMapper.selectById(id);
        if (config != null) {
            config.setIsActive(config.getIsActive() == 1 ? 0 : 1);
            templateConfigMapper.updateById(config);
        } else {
            log.error("模板不存在: id={}", id);
        }
    }

    // ---- 规则操作 ----

    @Transactional
    public TemplateRule createRule(Long templateId, TemplateRuleDTO dto) {
        TemplateRule rule = new TemplateRule();
        BeanUtils.copyProperties(dto, rule);
        rule.setId(null);
        rule.setTemplateId(templateId);
        templateRuleMapper.insert(rule);
        return rule;
    }

    public TemplateRule getRule(Long id) {
        return templateRuleMapper.selectById(id);
    }

    public List<TemplateRule> listRules(Long templateId) {
        LambdaQueryWrapper<TemplateRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TemplateRule::getTemplateId, templateId);
        return templateRuleMapper.selectList(wrapper);
    }

    @Transactional
    public TemplateRule updateRule(Long id, TemplateRuleDTO dto) {
        TemplateRule rule = templateRuleMapper.selectById(id);
        if (rule == null) {
            log.error("规则不存在: id={}", id);
            return null;
        }
        // 仅当 DTO 字段非空时才更新，防止 null 覆盖已有值
        if (dto.getName() != null)            rule.setName(dto.getName());
        if (dto.getFontName() != null)        rule.setFontName(dto.getFontName());
        if (dto.getFontSize() != null)        rule.setFontSize(dto.getFontSize());
        if (dto.getFontBold() != null)        rule.setFontBold(dto.getFontBold());
        if (dto.getFontItalic() != null)      rule.setFontItalic(dto.getFontItalic());
        if (dto.getFontUnderline() != null)   rule.setFontUnderline(dto.getFontUnderline());
        if (dto.getFontStrike() != null)      rule.setFontStrike(dto.getFontStrike());
        if (dto.getFontColor() != null)       rule.setFontColor(dto.getFontColor());
        if (dto.getTextAlign() != null)       rule.setTextAlign(dto.getTextAlign());
        if (dto.getTextIndent() != null)      rule.setTextIndent(dto.getTextIndent());
        if (dto.getLineSpacing() != null)     rule.setLineSpacing(dto.getLineSpacing());
        if (dto.getSpaceBefore() != null)     rule.setSpaceBefore(dto.getSpaceBefore());
        if (dto.getSpaceAfter() != null)      rule.setSpaceAfter(dto.getSpaceAfter());
        if (dto.getHighlightColor() != null)  rule.setHighlightColor(dto.getHighlightColor());

        templateRuleMapper.updateById(rule);
        return rule;
    }

    @Transactional
    public void deleteRule(Long id) {
        templateRuleMapper.deleteById(id);
    }

    // ---- 引擎配置 CRUD ----

    @Transactional
    public EngineConfigDTO createEngineConfig(Long templateId, EngineConfigDTO dto) {
        EngineConfig config = new EngineConfig();
        BeanUtils.copyProperties(dto, config);
        config.setId(null);
        config.setTemplateId(templateId);
        engineConfigMapper.insert(config);

        EngineConfigDTO result = new EngineConfigDTO();
        BeanUtils.copyProperties(config, result);
        return result;
    }

    public Page<EngineConfigDTO> listEngineConfigs(Long templateId, int page, int size) {
        LambdaQueryWrapper<EngineConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineConfig::getTemplateId, templateId);
        List<EngineConfig> list = engineConfigMapper.selectList(wrapper);

        // 收集所有 ruleId，批量查询规则名称
        Set<Long> ruleIds = list.stream()
                .map(EngineConfig::getRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> ruleNameMap = new HashMap<>();
        if (!ruleIds.isEmpty()) {
            List<TemplateRule> rules = templateRuleMapper.selectBatchIds(ruleIds);
            for (TemplateRule rule : rules) {
                ruleNameMap.put(rule.getId(), rule.getName());
            }
        }

        List<EngineConfigDTO> dtoList = list.stream().map(ec -> {
            EngineConfigDTO ed = new EngineConfigDTO();
            BeanUtils.copyProperties(ec, ed);
            ed.setRuleName(ruleNameMap.get(ec.getRuleId()));
            return ed;
        }).collect(Collectors.toList());

        // 排序：SPECIAL(0) < COVER(1) < TOC(2) < TITLE(3) < BODY(4)，同类型内按 sort_order 升序
        dtoList.sort(Comparator.comparingInt((EngineConfigDTO e) -> {
            switch (e.getMatchType()) {
                case "SPECIAL": return 0;
                case "COVER":  return 1;
                case "TOC":    return 2;
                case "TITLE":  return 3;
                case "BODY":   return 4;
                default:       return 5;
            }
        }).thenComparingInt(e -> e.getSortOrder() != null ? e.getSortOrder() : 0));

        // 手动分页
        int total = dtoList.size();
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, total);

        List<EngineConfigDTO> pageList;
        if (fromIndex >= total) {
            pageList = Collections.emptyList();
        } else {
            pageList = dtoList.subList(fromIndex, toIndex);
        }

        Page<EngineConfigDTO> pageResult = new Page<>(page, size, total);
        pageResult.setRecords(pageList);
        return pageResult;
    }

    public EngineConfigDTO getEngineConfig(Long id) {
        EngineConfig config = engineConfigMapper.selectById(id);
        if (config == null) {
            return null;
        }
        EngineConfigDTO dto = new EngineConfigDTO();
        BeanUtils.copyProperties(config, dto);
        // 填充规则名称
        if (config.getRuleId() != null) {
            TemplateRule rule = templateRuleMapper.selectById(config.getRuleId());
            if (rule != null) {
                dto.setRuleName(rule.getName());
            }
        }
        return dto;
    }

    @Transactional
    public EngineConfigDTO updateEngineConfig(Long id, EngineConfigDTO dto) {
        EngineConfig config = engineConfigMapper.selectById(id);
        if (config == null) {
            log.error("引擎配置不存在: id={}", id);
            return null;
        }
        BeanUtils.copyProperties(dto, config, "id", "templateId", "createdAt", "updatedAt");
        engineConfigMapper.updateById(config);

        EngineConfigDTO result = new EngineConfigDTO();
        BeanUtils.copyProperties(config, result);
        // 填充规则名称
        if (config.getRuleId() != null) {
            TemplateRule rule = templateRuleMapper.selectById(config.getRuleId());
            if (rule != null) {
                result.setRuleName(rule.getName());
            }
        }
        return result;
    }

    @Transactional
    public void deleteEngineConfig(Long id) {
        engineConfigMapper.deleteById(id);
    }
}
