package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.dto.ElementTypeCountVO;
import com.template.dto.ParseElementVO;
import com.template.entity.ParseElement;
import com.template.entity.ParseRecord;
import com.template.mapper.ParseElementMapper;
import com.template.mapper.ParseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseElementService {

    private final ParseElementMapper parseElementMapper;
    private final ParseRecordMapper parseRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分页查询元素明细
     */
    public Page<ParseElementVO> list(Long recordId, String elementType,
                                     BigDecimal confidenceMin, BigDecimal confidenceMax,
                                     String keyword, String parentElementId,
                                     int page, int size) {
        // 校验 recordId 对应的记录是否存在
        ParseRecord record = parseRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("解析记录不存在: " + recordId);
        }

        LambdaQueryWrapper<ParseElement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseElement::getRecordId, recordId);

        if (elementType != null && !elementType.isEmpty()) {
            wrapper.eq(ParseElement::getElementType, elementType);
        }
        if (confidenceMin != null) {
            wrapper.ge(ParseElement::getConfidence, confidenceMin);
        }
        if (confidenceMax != null) {
            wrapper.le(ParseElement::getConfidence, confidenceMax);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ParseElement::getContentText, keyword);
        }
        if (parentElementId != null && !parentElementId.isEmpty()) {
            wrapper.eq(ParseElement::getParentElementId, parentElementId);
        }

        wrapper.orderByAsc(ParseElement::getSortOrder);

        Page<ParseElement> pageResult = parseElementMapper.selectPage(new Page<>(page, size), wrapper);

        Page<ParseElementVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 获取元素类型统计（按记录）
     */
    public List<ElementTypeCountVO> getTypeCounts(Long recordId) {
        ParseRecord record = parseRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("解析记录不存在: " + recordId);
        }

        LambdaQueryWrapper<ParseElement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseElement::getRecordId, recordId);
        List<ParseElement> elements = parseElementMapper.selectList(wrapper);

        Map<String, Long> countMap = elements.stream()
                .collect(Collectors.groupingBy(ParseElement::getElementType, Collectors.counting()));

        return countMap.entrySet().stream()
                .map(e -> new ElementTypeCountVO(e.getKey(), e.getValue().intValue()))
                .collect(Collectors.toList());
    }

    /**
     * 查询某个记录的所有元素（按 sort_order 排序），用于差异对比
     */
    public List<ParseElement> listByRecordId(Long recordId) {
        LambdaQueryWrapper<ParseElement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseElement::getRecordId, recordId)
                .orderByAsc(ParseElement::getSortOrder);
        return parseElementMapper.selectList(wrapper);
    }

    private ParseElementVO toVO(ParseElement entity) {
        ParseElementVO vo = new ParseElementVO();
        vo.setId(entity.getId());
        vo.setRecordId(entity.getRecordId());
        vo.setElementId(entity.getElementId());
        vo.setElementType(entity.getElementType());
        vo.setLevel(entity.getLevel());
        vo.setContentText(entity.getContentText());
        vo.setConfidence(entity.getConfidence());
        vo.setParentElementId(entity.getParentElementId());
        vo.setSortOrder(entity.getSortOrder());

        try {
            if (entity.getMetadata() != null && !entity.getMetadata().isEmpty()) {
                vo.setMetadata(objectMapper.readValue(entity.getMetadata(), Map.class));
            }
        } catch (Exception e) {
            log.warn("metadata JSON 解析失败: id={}, error={}", entity.getId(), e.getMessage());
            vo.setMetadata(entity.getMetadata());
        }

        vo.setStyleFeatures(entity.getStyleFeatures());
        return vo;
    }
}
