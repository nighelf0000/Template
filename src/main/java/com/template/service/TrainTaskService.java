package com.template.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.template.dto.ParseRecordSimpleVO;
import com.template.dto.TrainTaskProgressDTO;
import com.template.dto.TrainTaskStatusDTO;
import com.template.dto.TrainTaskVO;
import com.template.entity.TrainTask;
import com.template.mapper.ParseRecordMapper;
import com.template.mapper.TemplateConfigMapper;
import com.template.mapper.TrainTaskMapper;
import com.template.entity.ParseRecord;
import com.template.entity.TemplateConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainTaskService {

    private final TrainTaskMapper trainTaskMapper;
    private final TrainFileService trainFileService;
    private final TrainTaskRunnerService trainTaskRunnerService;
    private final ParseRecordMapper parseRecordMapper;
    private final TemplateConfigMapper templateConfigMapper;

    /**
     * 服务启动时重置中间状态（PENDING/RUNNING）的训练任务为 FAILED。
     * 避免因服务重启导致任务永远卡在中间状态。
     */
    @PostConstruct
    public void initResetPendingTasks() {
        log.info("服务启动 - 检查并重置中间状态的训练任务");

        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TrainTask::getStatus, "PENDING", "RUNNING");

        List<TrainTask> pendingTasks = trainTaskMapper.selectList(wrapper);
        if (pendingTasks.isEmpty()) {
            log.info("无中间状态的训练任务需要重置");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (TrainTask task : pendingTasks) {
            task.setStatus("FAILED");
            task.setErrorMessage("服务重启，训练中断");
            task.setCompletedAt(now);
            task.setUpdatedAt(now);
            trainTaskMapper.updateById(task);
            log.info("重置训练任务: taskId={}", task.getId());
        }

        log.info("已重置 {} 个中间状态的训练任务", pendingTasks.size());
    }

    /**
     * 创建并启动训练任务
     */
    @Transactional
    public TrainTaskVO start(Long templateId, String taskName) {
        // 1. 检查同模板是否有 PENDING/RUNNING 的任务
        LambdaQueryWrapper<TrainTask> runningCheck = new LambdaQueryWrapper<>();
        runningCheck.eq(TrainTask::getTemplateId, templateId)
                .in(TrainTask::getStatus, "PENDING", "RUNNING");
        long runningCount = trainTaskMapper.selectCount(runningCheck);
        if (runningCount > 0) {
            throw new IllegalStateException("该模板已有正在执行的训练任务，请等待完成");
        }

        // 2. 查询模板下 UPLOADED 的文件
        long fileCount = trainFileService.countByTemplateId(templateId);
        if (fileCount == 0) {
            throw new IllegalStateException("没有可用的训练文件，请先上传文件");
        }

        // 3. 创建 train_task 记录
        TrainTask task = new TrainTask();
        task.setTemplateId(templateId);
        task.setTaskName(taskName != null ? taskName : "训练任务-" + LocalDateTime.now().toString().substring(0, 19));
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setTotalFiles((int) fileCount);
        task.setFileCount(0);
        trainTaskMapper.insert(task);

        Long taskId = task.getId();
        log.info("创建训练任务: taskId={}, templateId={}, fileCount={}", taskId, templateId, fileCount);

        // 4. 更新状态为 RUNNING
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        trainTaskMapper.updateById(task);

        // 5. 异步执行 Python 训练
        trainTaskRunnerService.runTrainingAsync(taskId);

        return toVO(task);
    }

    /**
     * 分页查询训练任务列表
     */
    public Page<TrainTaskVO> page(Long templateId, String status, String keyword, int page, int size) {
        LambdaQueryWrapper<TrainTask> wrapper = new LambdaQueryWrapper<>();
        if (templateId != null) {
            wrapper.eq(TrainTask::getTemplateId, templateId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(TrainTask::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(TrainTask::getTaskName, keyword);
        }
        wrapper.orderByDesc(TrainTask::getCreatedAt);

        Page<TrainTask> pageResult = trainTaskMapper.selectPage(new Page<>(page, size), wrapper);
        Page<TrainTaskVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 查询训练任务详情（含关联的解析记录）
     */
    public TrainTaskVO detail(Long id) {
        TrainTask task = trainTaskMapper.selectById(id);
        if (task == null) {
            return null;
        }
        TrainTaskVO vo = toVO(task);

        // 查询关联的解析记录
        LambdaQueryWrapper<ParseRecord> prWrapper = new LambdaQueryWrapper<>();
        prWrapper.eq(ParseRecord::getTemplateId, task.getTemplateId())
                .orderByDesc(ParseRecord::getParsedAt);
        List<ParseRecord> records = parseRecordMapper.selectList(prWrapper);
        vo.setParseRecords(records.stream().map(r -> {
            ParseRecordSimpleVO svo = new ParseRecordSimpleVO();
            svo.setId(r.getId());
            svo.setSourceFile(r.getSourceFile());
            svo.setStatus(r.getStatus());
            svo.setParsedAt(r.getParsedAt());
            return svo;
        }).collect(Collectors.toList()));

        return vo;
    }

    /**
     * 更新训练任务进度（供 Python 引擎回调）
     */
    @Transactional
    public void updateProgress(Long id, TrainTaskProgressDTO dto) {
        TrainTask task = trainTaskMapper.selectById(id);
        if (task == null) {
            log.warn("训练任务不存在，进度更新跳过: taskId={}", id);
            return;
        }
        task.setProgress(dto.getProgress());
        task.setFileCount(dto.getCurrentFile() != null ? dto.getCurrentFile() : task.getFileCount());
        task.setTotalFiles(dto.getTotalFiles() != null ? dto.getTotalFiles() : task.getTotalFiles());
        task.setUpdatedAt(LocalDateTime.now());
        trainTaskMapper.updateById(task);
        log.info("训练任务进度更新: taskId={}, progress={}%, message={}", id, dto.getProgress(), dto.getMessage());
    }

    /**
     * 更新训练任务状态（供 Python 引擎回调）
     */
    @Transactional
    public void updateStatus(Long id, TrainTaskStatusDTO dto) {
        TrainTask task = trainTaskMapper.selectById(id);
        if (task == null) {
            log.warn("训练任务不存在，状态更新跳过: taskId={}", id);
            return;
        }
        task.setStatus(dto.getStatus());
        task.setErrorMessage(dto.getErrorMessage());
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        if ("SUCCESS".equals(dto.getStatus())) {
            task.setProgress(100);
        }
        trainTaskMapper.updateById(task);
        log.info("训练任务状态更新: taskId={}, status={}, errorMessage={}", id, dto.getStatus(), dto.getErrorMessage());
    }

    /**
     * 将实体转换为 VO
     */
    private TrainTaskVO toVO(TrainTask entity) {
        TrainTaskVO vo = new TrainTaskVO();
        vo.setId(entity.getId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setTaskName(entity.getTaskName());
        vo.setStatus(entity.getStatus());
        vo.setProgress(entity.getProgress());
        vo.setTotalFiles(entity.getTotalFiles());
        vo.setFileCount(entity.getFileCount());
        vo.setParseRecordCount(entity.getParseRecordCount());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartedAt(entity.getStartedAt());
        vo.setCompletedAt(entity.getCompletedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());

        TemplateConfig template = templateConfigMapper.selectById(entity.getTemplateId());
        if (template != null) {
            vo.setTemplateName(template.getName());
        }

        return vo;
    }
}
