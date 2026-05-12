package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ParseRecordCreateDTO;
import com.template.dto.ParseRecordVO;
import com.template.entity.ParseRecord;
import com.template.mapper.ParseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseRecordService {

    private final ParseRecordMapper parseRecordMapper;

    /**
     * 保存解析结果（供 Python 引擎调用）
     */
    @Transactional
    public Long save(ParseRecordCreateDTO dto) {
        ParseRecord record = new ParseRecord();
        record.setTemplateId(dto.getTemplateId());
        record.setUploadFileId(dto.getUploadFileId());
        record.setSourceFile(dto.getSourceFile());
        record.setSourceChecksum(dto.getSourceChecksum());
        record.setFileSize(dto.getFileSize());
        record.setParsedAt(LocalDateTime.now());
        record.setEngineVersion(dto.getEngineVersion() != null ? dto.getEngineVersion() : "1.0.0");
        record.setRulesetName(dto.getRulesetName() != null ? dto.getRulesetName() : "default");
        record.setRulesetVersion(dto.getRulesetVersion() != null ? dto.getRulesetVersion() : "1.0");
        record.setProcessingTimeMs(dto.getProcessingTimeMs() != null ? dto.getProcessingTimeMs() : 0);
        record.setStatus(dto.getStatus() != null ? dto.getStatus() : "success");
        record.setErrorMessage(dto.getErrorMessage());
        record.setStructureTree(dto.getStructureTree());
        record.setDocumentMeta(dto.getDocumentMeta());
        record.setElementSummary(dto.getElementSummary());
        record.setTags(dto.getTags());

        parseRecordMapper.insert(record);
        log.info("解析结果已保存: id={}, sourceFile={}", record.getId(), record.getSourceFile());
        return record.getId();
    }

    /**
     * 分页查询
     */
    public Page<ParseRecordVO> page(Long templateId, String status, String keyword,
                                     String startTime, String endTime,
                                     String rulesetName, int page, int size) {
        LambdaQueryWrapper<ParseRecord> wrapper = new LambdaQueryWrapper<>();

        if (templateId != null) {
            wrapper.eq(ParseRecord::getTemplateId, templateId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ParseRecord::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ParseRecord::getSourceFile, keyword);
        }
        if (rulesetName != null && !rulesetName.isEmpty()) {
            wrapper.eq(ParseRecord::getRulesetName, rulesetName);
        }
        if (startTime != null && !startTime.isEmpty()) {
            wrapper.ge(ParseRecord::getParsedAt, startTime + " 00:00:00");
        }
        if (endTime != null && !endTime.isEmpty()) {
            wrapper.le(ParseRecord::getParsedAt, endTime + " 23:59:59");
        }

        wrapper.orderByDesc(ParseRecord::getParsedAt);

        Page<ParseRecord> pageResult = parseRecordMapper.selectPage(new Page<>(page, size), wrapper);

        Page<ParseRecordVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 获取详情
     */
    public ParseRecordVO detail(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        return toVO(record);
    }

    /**
     * 获取结构树 JSON
     */
    public String getStructureTree(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        return record.getStructureTree();
    }

    /**
     * 删除解析记录
     */
    @Transactional
    public void delete(Long id) {
        ParseRecord record = parseRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        parseRecordMapper.deleteById(id);
        log.info("解析记录已删除: id={}", id);
    }

    private ParseRecordVO toVO(ParseRecord entity) {
        ParseRecordVO vo = new ParseRecordVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setUploadFileId(entity.getUploadFileId());
        vo.setSourceFile(entity.getSourceFile());
        vo.setSourceChecksum(entity.getSourceChecksum());
        vo.setFileSize(entity.getFileSize());
        vo.setParsedAt(entity.getParsedAt());
        vo.setEngineVersion(entity.getEngineVersion());
        vo.setRulesetName(entity.getRulesetName());
        vo.setRulesetVersion(entity.getRulesetVersion());
        vo.setProcessingTimeMs(entity.getProcessingTimeMs());
        vo.setStatus(entity.getStatus());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setTags(entity.getTags());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
