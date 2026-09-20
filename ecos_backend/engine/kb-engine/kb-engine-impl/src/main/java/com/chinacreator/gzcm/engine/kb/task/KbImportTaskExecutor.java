package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * K1 结构化抽取任务执行器 — 实现 ITaskExecutor，将 KbEntityInstanceExtractionService.extract
 * 接入 runtime-task 生命周期（KB_IMPORT）。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>从 TaskExecutionPlan 第一步 config 取 {@code ontologyId}/{@code mode}/{@code dryRun}/{@code jobId}；</li>
 *   <li>通过 ITaskStatusCallback 回报进度（0% → 100%）；</li>
 *   <li>调用 {@code KbEntityInstanceExtractionService.extract(...)})；</li>
 *   <li>成功：callback.onTaskComplete(success=true, result=report JSON)；</li>
 *   <li>失败：callback.onTaskComplete(success=false) + 将错误写入 kb_extract_audit（try/catch，失败仅 log.warn）；</li>
 * </ol>
 *
 * @author ECOS KB Team
 */
@Component
public class KbImportTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(KbImportTaskExecutor.class);
    private static final String TASK_TYPE = "KB_IMPORT";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KbEntityInstanceExtractionService extractionService;
    private final JdbcTemplate jdbc;

    /** 部署环境 tier（standard/enterprise/ultimate），落 audit 记录。 */
    @Value("${ecos.architecture.tier:standard}")
    private String tier;

    public KbImportTaskExecutor(KbEntityInstanceExtractionService extractionService,
                                 JdbcTemplate jdbc) {
        this.extractionService = extractionService;
        this.jdbc = jdbc;
    }

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) throws TaskExecutionException {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new TaskExecutionException("KB_IMPORT execution plan has no steps");
        }

        // 从 step.config 或 plan.context 取参数（与 PipelineTaskExecutor 行为一致）
        TaskExecutionPlan.ExecutionStep step = plan.getSteps().get(0);
        String taskId = plan.getTaskId();
        String ontologyId = null;
        String mode = "INCREMENTAL";
        boolean dryRun = false;
        String jobId = null;

        if (step.getConfig() != null) {
            if (step.getConfig().containsKey("ontologyId")) {
                ontologyId = String.valueOf(step.getConfig().get("ontologyId"));
            }
            if (step.getConfig().containsKey("mode")) {
                mode = String.valueOf(step.getConfig().get("mode"));
            }
            if (step.getConfig().containsKey("dryRun")) {
                dryRun = Boolean.parseBoolean(String.valueOf(step.getConfig().get("dryRun")));
            }
            if (step.getConfig().containsKey("jobId")) {
                jobId = String.valueOf(step.getConfig().get("jobId"));
            }
        }
        // 回退到 context
        if (plan.getContext() != null) {
            if (ontologyId == null && plan.getContext().containsKey("ontologyId")) {
                ontologyId = String.valueOf(plan.getContext().get("ontologyId"));
            }
            if (mode.equals("INCREMENTAL") && plan.getContext().containsKey("mode")) {
                mode = String.valueOf(plan.getContext().get("mode"));
            }
            if (!dryRun && plan.getContext().containsKey("dryRun")) {
                dryRun = Boolean.parseBoolean(String.valueOf(plan.getContext().get("dryRun")));
            }
            if (jobId == null && plan.getContext().containsKey("jobId")) {
                jobId = String.valueOf(plan.getContext().get("jobId"));
            }
        }
        // jobId 为空时兜底生成
        if (jobId == null || jobId.isBlank()) {
            jobId = "KBK1S-" + System.currentTimeMillis();
        }
        boolean incremental = "INCREMENTAL".equalsIgnoreCase(mode);

        log.info("KbImportTaskExecutor.start: taskId={} jobId={} ontologyId={} mode={} dryRun={}",
                taskId, jobId, ontologyId, mode, dryRun);

        long startMs = System.currentTimeMillis();
        if (callback != null) {
            callback.onProgressUpdate(taskId, 0, "KB_IMPORT 开始执行: jobId=" + jobId);
        }

        try {
            // 回报进度 10%
            if (callback != null) {
                callback.onProgressUpdate(taskId, 10, "加载抽取快照与元数据");
            }
            EntityInstanceExtractionReportVO report = extractionService.extract(
                    ontologyId, jobId, incremental, dryRun);
            long durationMs = report.getDurationMs();
            log.info("KbImportTaskExecutor.done: taskId={} jobId={} nodeCreated={} nodeUpdated={} "
                            + "edges={} failed={} durationMs={}",
                    taskId, jobId, report.getNodeCreated(), report.getNodeUpdated(),
                    report.getEdgeCreated(), report.getInvalidMappings(), durationMs);

            // 回报完成
            if (callback != null) {
                callback.onProgressUpdate(taskId, 90, "完成抽取，正在落审计");
                String resultJson;
                try {
                    resultJson = MAPPER.writeValueAsString(report);
                } catch (Exception se) {
                    log.warn("抽取报告序列化失败，返回摘要 JSON: {}", se.getMessage());
                    resultJson = "{\"jobId\":\"" + jobId + "\",\"nodeCreated\":" + report.getNodeCreated() + "}";
                }
                callback.onTaskComplete(taskId, true, resultJson, null);
            }

            // 落 audit（T4）
            writeAudit(jobId, taskId, mode, "SUCCEEDED", durationMs,
                    report.getNodeCreated() + report.getNodeUpdated(),
                    report.getNodeSkipped() + report.getInvalidMappings(),
                    report.getNodeCreated() + report.getNodeUpdated(),
                    0L, null);

            // result 返回报告 JSON
            try {
                return MAPPER.writeValueAsString(report);
            } catch (Exception e) {
                return "{\"jobId\":\"" + jobId + "\",\"status\":\"SUCCEEDED\"}";
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("KbImportTaskExecutor.failed: taskId={} jobId={} error={}",
                    taskId, jobId, e.getMessage(), e);
            if (callback != null) {
                callback.onTaskComplete(taskId, false, null, e.getMessage());
                callback.onError(taskId, e.getMessage(), getStackTrace(e));
            }
            // 失败也落 audit（标记 FAILED）
            writeAudit(jobId, taskId, mode, "FAILED", durationMs, 0L, 0L, 0L, 0L, e.getMessage());
            throw new TaskExecutionException("KB_IMPORT 抽取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        log.info("KbImportTaskExecutor.cancel: taskId={} (不支持运行中取消)", taskId);
        throw new TaskExecutionException("KB_IMPORT 不支持运行中取消");
    }

    @Override
    public void pause(String taskId) throws TaskExecutionException {
        log.info("KbImportTaskExecutor.pause: taskId={} (不支持暂停)", taskId);
        throw new TaskExecutionException("KB_IMPORT 不支持暂停");
    }

    @Override
    public void resume(String taskId) throws TaskExecutionException {
        log.info("KbImportTaskExecutor.resume: taskId={} (不支持恢复)", taskId);
        throw new TaskExecutionException("KB_IMPORT 不支持恢复");
    }

    @Override
    public TaskStatus getStatus(String taskId) throws TaskExecutionException {
        // 状态由 runtime-task 管理，此处返回 RUNNING 占位
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }

    /**
     * 写入 kb_extract_audit 行（尽力而为：失败仅 log.warn，不阻断业务）。
     *
     * @param jobId       抽取 jobId
     * @param taskId      runtime-task 任务 ID
     * @param mode        抽取模式
     * @param status      SUCCEEDED / FAILED
     * @param durationMs  耗时（ms）
     * @param rowsTotal   本批复处理行数（nodeCreated+nodeUpdated）
     * @param rowsOk      正常落库行数（nodeCreated+nodeUpdated）
     * @param rowsFailed  失败行（nodeSkipped+invalidMappings）
     * @param mismatched  对照失败（本批次固定 0）
     * @param errorMessage 失败原因（成功时为 null）
     */
    private void writeAudit(String jobId, String taskId, String mode, String status,
                            long durationMs, long rowsTotal, long rowsOk,
                            long rowsFailed, long mismatched, String errorMessage) {
        try {
            jdbc.update(
                    "INSERT INTO ecos_knowledge.kb_extract_audit "
                            + "(job_id, task_id, tier, mode, status, duration_ms, rows_total, rows_ok, rows_failed, mismatched, error_message) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    jobId, taskId, tier, mode, status,
                    durationMs, rowsTotal, rowsOk, rowsFailed, mismatched, errorMessage);
            log.info("KB_IMPORT audit 写入成功: jobId={} task={} status={}", jobId, taskId, status);
        } catch (Exception e) {
            log.warn("KB_IMPORT audit 写入失败（不阻断业务）: jobId={} task={} err={}", jobId, taskId, e.getMessage());
        }
    }

    /** 获取堆栈跟踪（用于 error callback）。 */
    private String getStackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
