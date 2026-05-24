package com.template.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TrainTaskRunnerService {

    @Value("${template.train.python-path:python}")
    private String pythonPath;

    @Value("${template.train.timeout-minutes:30}")
    private int timeoutMinutes;

    @Value("${template.train.api-base-url:http://localhost:8080}")
    private String apiBaseUrl;

    @Value("${template.train.internal-token:train-internal-token}")
    private String internalToken;

    private final TrainTaskService trainTaskService;

    public TrainTaskRunnerService(@Lazy TrainTaskService trainTaskService) {
        this.trainTaskService = trainTaskService;
    }

    /**
     * 调用 Python doc-struct 引擎执行批量训练。
     * Java 仅传递 --train-id 和 --api-url，Python 自行获取文件列表和回存结果。
     */
    public void runTraining(Long trainId) {
        log.info("开始执行训练任务: trainId={}", trainId);

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonPath);
            cmd.add("-m");
            cmd.add("docstruct");
            cmd.add("--mode");
            cmd.add("train");
            cmd.add("--train-id");
            cmd.add(trainId.toString());
            cmd.add("--api-url");
            cmd.add(apiBaseUrl);
            cmd.add("--internal-token");
            cmd.add(internalToken);

            log.info("执行命令: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Map<String, String> env = pb.environment();
            String projectRoot = System.getProperty("user.dir");
            env.put("PYTHONPATH", projectRoot + "\\doc-struct\\src");
            Process process = pb.start();

            // 异步读取 stdout/stderr，防止管道缓冲区满导致死锁
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

            Thread stdoutReader = new Thread(() -> {
                try (InputStream is = process.getInputStream()) {
                    is.transferTo(stdoutBuffer);
                } catch (Exception e) {
                    log.warn("读取 stdout 异常", e);
                }
            });

            Thread stderrReader = new Thread(() -> {
                try (InputStream is = process.getErrorStream()) {
                    is.transferTo(stderrBuffer);
                } catch (Exception e) {
                    log.warn("读取 stderr 异常", e);
                }
            });

            stdoutReader.start();
            stderrReader.start();

            // 等待训练完成（超时控制）
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                stdoutReader.interrupt();
                stderrReader.interrupt();
                process.destroyForcibly();
                throw new RuntimeException("训练超时（" + timeoutMinutes + "分钟）");
            }

            stdoutReader.join(5000);
            stderrReader.join(5000);

            int exitCode = process.exitValue();
            String stdout = stdoutBuffer.toString(StandardCharsets.UTF_8);
            String stderr = stderrBuffer.toString(StandardCharsets.UTF_8);

            log.info("Python 引擎执行完成: trainId={}, exitCode={}", trainId, exitCode);

            if (exitCode != 0) {
                String errorMsg = !stderr.isEmpty() ? stderr : stdout;
                log.error("Python 引擎执行失败: trainId={}, error={}", trainId, errorMsg);
                // 通过状态回调更新任务为 FAILED
                com.template.dto.TrainTaskStatusDTO statusDTO = new com.template.dto.TrainTaskStatusDTO();
                statusDTO.setStatus("FAILED");
                statusDTO.setErrorMessage(errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
                trainTaskService.updateStatus(trainId, statusDTO);
            }
            // exitCode == 0 时，Python 引擎已经通过 API 回调更新了状态

        } catch (Exception e) {
            log.error("训练任务执行异常: trainId={}", trainId, e);
            com.template.dto.TrainTaskStatusDTO statusDTO = new com.template.dto.TrainTaskStatusDTO();
            statusDTO.setStatus("FAILED");
            statusDTO.setErrorMessage(e.getMessage() != null ? e.getMessage() : "训练执行异常");
            trainTaskService.updateStatus(trainId, statusDTO);
        }
    }

    /**
     * 异步执行训练任务
     */
    @Async("trainTaskExecutor")
    public void runTrainingAsync(Long trainId) {
        runTraining(trainId);
    }
}
