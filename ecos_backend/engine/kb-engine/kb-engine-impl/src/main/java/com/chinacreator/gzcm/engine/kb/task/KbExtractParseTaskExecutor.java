package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractionService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识抽取"文档解析+LLM抽取"任务执行器（F04-14 runtime-task 收口，K-38/K-39）。
 *
 * <p>取代旧 {@code KnowledgeExtractionService.upload()} 里的
 * {@code Executors.newSingleThreadExecutor().submit(...)}：现在上传只提交
 * {@code KB_EXTRACT_PARSE} 任务，本执行器由 runtime-task 生命周期驱动，
 * 调 {@link KnowledgeExtractionService#parseById(String)} 完成解析+抽取。
 *
 * <p>禁自建线程（铁律 §2.5-3）：本执行器本身由 runtime-task 驱动。
 *
 * @author ECOS KB Team
 */
@Component
public class KbExtractParseTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(KbExtractParseTaskExecutor.class);
    private static final String TASK_TYPE = KnowledgeExtractionService.TASK_TYPE_EXTRACT_PARSE;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeExtractionService extractionService;

    public KbExtractParseTaskExecutor(KnowledgeExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) throws TaskExecutionException {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new TaskExecutionException(TASK_TYPE + " execution plan has no steps");
        }
        String taskId = plan.getTaskId();
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (plan.getSteps().get(0).getConfig() != null) {
            cfg.putAll(plan.getSteps().get(0).getConfig());
        }
        String extractionId = cfg.get("extractionId") == null ? null : String.valueOf(cfg.get("extractionId"));
        if (extractionId == null || extractionId.isBlank()) {
            throw new TaskExecutionException(TASK_TYPE + " 缺少 extractionId 参数");
        }
        if (callback != null) {
            callback.onProgressUpdate(taskId, 10, "解析+抽取开始: extractionId=" + extractionId);
        }
        try {
            extractionService.parseById(extractionId);
            if (callback != null) {
                callback.onProgressUpdate(taskId, 100, "解析+抽取完成");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("extractionId", extractionId);
            String json = MAPPER.writeValueAsString(result);
            if (callback != null) {
                callback.onTaskComplete(taskId, true, json, null);
            }
            log.info("KbExtractParseTaskExecutor.done: extractionId={}", extractionId);
            return json;
        } catch (Exception e) {
            log.error("KbExtractParseTaskExecutor.failed: extractionId={} err={}", extractionId, e.getMessage(), e);
            if (callback != null) {
                callback.onTaskComplete(taskId, false, null, e.getMessage());
                callback.onError(taskId, e.getMessage(), stackTraceAsString(e));
            }
            throw new TaskExecutionException(TASK_TYPE + " 解析+抽取失败: " + e.getMessage(), e);
        }
    }

    private static String stackTraceAsString(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持运行中取消");
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持暂停");
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        throw new TaskExecutionException(TASK_TYPE + " 不支持恢复");
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }
}
