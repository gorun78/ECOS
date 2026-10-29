package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.dto.KnowledgeDocIngestResultVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeDocIngestService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 文档接入解析任务执行器 — 实现 ITaskExecutor，把 {@code KnowledgeDocIngestService.runByDocId}
 * 阶段流水线接入 runtime-task 生命周期（KB_DOC_INGEST，#146z 附录A 第 11 条异步承流腿）。
 *
 * <p>执行流程：从 step.config（回退 plan.context）取 {@code docId} →
 * 按 kb_doc 行回读近源层原文 → 跑与存量 {@code /docs/ingest} 同一份阶段流水线（单源，
 * 禁在本类复刻编排）。原文已在受理腿写入近源层并登记 queued 行，本腿只做解析后续；
 * 阶段失败由服务侧落 {@code failed} 状态（含 error_message），本类回报任务失败。</p>
 *
 * <p>审计：阶段流水线成功即由服务侧 {@code emitAudit} 走 EventBus 发 {@code ecos.audit}
 * （铁律 §2.4-5 写操作审计腿在既有单源实现，本类不重复发，禁双写两份 audit 语义）。</p>
 *
 * @author ECOS KB Team
 */
@Component
public class KbDocIngestTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(KbDocIngestTaskExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KnowledgeDocIngestService docIngestService;

    public KbDocIngestTaskExecutor(KnowledgeDocIngestService docIngestService) {
        this.docIngestService = docIngestService;
    }

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) throws TaskExecutionException {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new TaskExecutionException("KB_DOC_INGEST execution plan has no steps");
        }
        TaskExecutionPlan.ExecutionStep step = plan.getSteps().get(0);
        String taskId = plan.getTaskId();
        String docId = readParam(step, plan, "docId");
        if (docId == null || docId.isBlank()) {
            throw new TaskExecutionException("KB_DOC_INGEST missing docId in plan config/context");
        }

        log.info("KbDocIngestTaskExecutor.start: taskId={} docId={}", taskId, docId);
        if (callback != null) {
            callback.onProgressUpdate(taskId, 10, "回读近源层原文并执行解析流水线: docId=" + docId);
        }

        try {
            KnowledgeDocIngestResultVO result = docIngestService.runByDocId(docId);
            if (callback != null) {
                callback.onProgressUpdate(taskId, 90, "解析流水线完成，正在回报结果");
                callback.onTaskComplete(taskId, true, toJson(result), null);
            }
            log.info("KbDocIngestTaskExecutor.done: taskId={} docId={} chunks={} vectorWritten={}",
                    taskId, docId, result.getChunkCount(), result.getVectorWritten());
            return toJson(result);
        } catch (Exception e) {
            log.error("KbDocIngestTaskExecutor.failed: taskId={} docId={} error={}",
                    taskId, docId, e.getMessage(), e);
            if (callback != null) {
                callback.onTaskComplete(taskId, false, null, e.getMessage());
                callback.onError(taskId, e.getMessage(), null);
            }
            throw new TaskExecutionException("KB_DOC_INGEST 解析流水线失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        log.info("KbDocIngestTaskExecutor.cancel: taskId={} (不支持运行中取消)", taskId);
        throw new TaskExecutionException("KB_DOC_INGEST 不支持运行中取消");
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        log.info("KbDocIngestTaskExecutor.pause: taskId={} (不支持暂停)", taskId);
        throw new TaskExecutionException("KB_DOC_INGEST 不支持暂停");
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        log.info("KbDocIngestTaskExecutor.resume: taskId={} (不支持恢复)", taskId);
        throw new TaskExecutionException("KB_DOC_INGEST 不支持恢复");
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }

    /** step.config 优先、plan.context 回退（与 KbImportTaskExecutor 同兼容读取形态）。 */
    private static String readParam(TaskExecutionPlan.ExecutionStep step, TaskExecutionPlan plan,
                                    String key) {
        if (step.getConfig() != null && step.getConfig().containsKey(key)) {
            return String.valueOf(step.getConfig().get(key));
        }
        if (plan.getContext() != null && plan.getContext().containsKey(key)) {
            return String.valueOf(plan.getContext().get(key));
        }
        return null;
    }

    private static String toJson(KnowledgeDocIngestResultVO result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("KB_DOC_INGEST 结果序列化失败，返回摘要 JSON: {}", e.getMessage());
            return "{\"docId\":\"" + result.getDocId() + "\",\"parseStatus\":\""
                    + result.getParseStatus() + "\"}";
        }
    }
}
