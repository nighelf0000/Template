package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.TrainFileVO;
import com.template.entity.TrainFile;
import com.template.entity.TrainTask;
import com.template.mapper.TemplateConfigMapper;
import com.template.mapper.TrainFileMapper;
import com.template.mapper.TrainTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainFileService {

    private final TrainFileMapper trainFileMapper;
    private final TemplateConfigMapper templateConfigMapper;
    private final TrainTaskMapper trainTaskMapper;

    /**
     * 上传训练文件
     */
    @Transactional
    public TrainFileVO upload(MultipartFile file, Long templateId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("仅支持 .docx 格式文件");
        }

        if (templateConfigMapper.selectById(templateId) == null) {
            throw new IllegalArgumentException("模板不存在或已停用");
        }

        TrainFile trainFile = new TrainFile();
        trainFile.setTemplateId(templateId);
        trainFile.setOriginalName(originalFilename);
        trainFile.setOriginalSize(file.getSize());
        trainFile.setOriginalContent(file.getBytes());
        trainFile.setStatus("UPLOADED");
        trainFileMapper.insert(trainFile);

        return toVO(trainFile);
    }

    /**
     * 查询指定模板的训练文件列表（分页）
     */
    public Page<TrainFileVO> list(Long templateId, int page, int size) {
        LambdaQueryWrapper<TrainFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainFile::getTemplateId, templateId)
                .eq(TrainFile::getStatus, "UPLOADED")
                .orderByDesc(TrainFile::getCreatedAt);
        Page<TrainFile> pageResult = trainFileMapper.selectPage(new Page<>(page, size), wrapper);

        Page<TrainFileVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 删除训练文件
     */
    @Transactional
    public void delete(Long id) {
        TrainFile trainFile = trainFileMapper.selectById(id);
        if (trainFile == null) {
            throw new IllegalArgumentException("文件不存在");
        }

        // 检查是否有 PENDING/RUNNING 的训练任务正在使用此模板的文件
        LambdaQueryWrapper<TrainTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(TrainTask::getTemplateId, trainFile.getTemplateId())
                .in(TrainTask::getStatus, "PENDING", "RUNNING");
        if (trainTaskMapper.selectCount(taskWrapper) > 0) {
            throw new IllegalStateException("文件正在训练任务中使用，无法删除");
        }

        trainFile.setStatus("DELETED");
        trainFileMapper.updateById(trainFile);
    }

    /**
     * 获取文件原始内容（供 Python 引擎下载）
     */
    public byte[] getRawContent(Long id) {
        TrainFile trainFile = trainFileMapper.selectById(id);
        if (trainFile == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        byte[] content = trainFile.getOriginalContent();
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容为空");
        }
        return content;
    }

    /**
     * 获取指定模板下 UPLOADED 状态的文件总数
     */
    public long countByTemplateId(Long templateId) {
        LambdaQueryWrapper<TrainFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainFile::getTemplateId, templateId)
                .eq(TrainFile::getStatus, "UPLOADED");
        return trainFileMapper.selectCount(wrapper);
    }

    /**
     * 将实体转换为 VO（不包含 originalContent）
     */
    private TrainFileVO toVO(TrainFile entity) {
        TrainFileVO vo = new TrainFileVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setOriginalName(entity.getOriginalName());
        vo.setOriginalSize(entity.getOriginalSize());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
