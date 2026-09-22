package com.chinacreator.gzcm.engine.kb.task;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 *   <li>成功：callback.onTaskComplete(success=true, result=report JSON) + audit 双走（JDBC + Kafka）；</li>
 *   <li>失败：callback.onTaskComplete(success=false) + audit 双走。</li>
 * </ol>
 *
 * <p>审计策略（架构铁律 §2.4-5）：JDBC {@code kb_extract_audit} 落本地 + Kafka {@code ecoss.audit}
 * 双走（Kafka 反射注入双态，缺失时 fallback 仅 JDBC）。</p>
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

    /** 可选 — 容器中存在 KafkaTemplate Bean 时注入（反射访问容忍 classpath 差异，对齐 SecurityEngineClient 双态）。
     *     架构铁律 §2.4-5：写操作必发 Kafka ecos.audit；缺失时 fallback 仅 log + JDBC，不阻断业务。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

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
        // jobId 为空时兜底生成（铁律 §1.6-1：jobId = taskId，统一标识；plan 自挂时也会前置对齐）
        if (jobId == null || jobId.isBlank()) {
            jobId = taskId != null && !taskId.isBlank() ? taskId : "KBK1S-" + System.currentTimeMillis();
        }
        boolean incremental = "INCREMENTAL".equalsIgnoreCase(mode);

        log.info("KbImportTaskExecutor.start: taskId={} jobId={} ontologyId={} mode={} dryRun={}",
                taskId, jobId, ontologyId, mode, dryRun);

        long startMs = System.currentTimeMillis();
        if (callback != null) {
            callback.onProgressUpdate(taskId, 0, "KB_IMPORT 开始执行: jobId=" + jobId);
        }

        try {
            if (callback != null) {
                callback.onProgressUpdate(taskId, 10, "加载抽取快照与元数据");
            }
            EntityInstanceExtractionReportVO report = extractionService.extract(
                    ontologyId, jobId, incremental, dryRun);
            long durationMs = report.getDurationMs();
            long rowsTotal = (long) report.getNodeCreated() + report.getNodeUpdated();
            long rowsOk = rowsTotal;
            long rowsFailed = (long) report.getNodeSkipped() + report.getInvalidMappings();

            log.info("KbImportTaskExecutor.done: taskId={} jobId={} nodeCreated={} nodeUpdated={} "
                            + "edges={} failed={} durationMs={}",
                    taskId, jobId, report.getNodeCreated(), report.getNodeUpdated(),
                    report.getEdgeCreated(), report.getInvalidMappings(), durationMs);

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

            // 落 audit（铁律 §2.4-5）：JDBC 本地 + Kafka 双走
            writeAudit(jobId, taskId, mode, "SUCCEEDED", durationMs,
                    rowsTotal, rowsOk, rowsFailed,
                    0L, null);

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
            writeAudit(jobId, taskId, mode, "FAILED", durationMs,
                    0L, 0L, 0L, 0L, e.getMessage());
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
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(TaskStatus.Status.RUNNING);
        return status;
    }

    /**
     * 写入 kb_extract_audit 行 + Kafka ecos.audit（铁律 §2.4-5）：
     * <ul>
     *   <li>JDBC：本地台账（删 audit 不阻断业务）</li>
     *   <li>Kafka：平台统一审计流；缺失降级仅 JDBC + log，**不抛**</li>
     * </ul>
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
            log.info("KB_IMPORT audit JDBC 写入: jobId={} task={} status={}", jobId, taskId, status);
        } catch (Exception e) {
            log.warn("KB_IMPORT audit JDBC 写入失败（不阻断业务）: jobId={} task={} err={}", jobId, taskId, e.getMessage());
        }
        // Kafka 双走（铁律 §2.4-5）
        publishAuditEvent(jobId, taskId, status, mode, durationMs, rowsTotal, rowsOk, rowsFailed, errorMessage);
    }

    /**
     * Kafka 审计事件发送（铁律 §2.4-5）：
     * 通过反射调用 {@code KafkaTemplate.send(String, Object)} 容忍 classpath 缺失（与
     * {@code SecurityEngineClient.audit} 同波形）；Kafka 缺失时 warn 不抛。
     */
    private void publishAuditEvent(String jobId, String taskId, String status, String mode,
                                   long durationMs, long rowsTotal, long rowsOk,
                                   long rowsFailed, String errorMessage) {
        String payload = String.format(
                "{\"action\":\"kg_instance_extract\",\"jobId\":\"%s\",\"taskId\":\"%s\",\"tier\":\"%s\","
                        + "\"mode\":\"%s\",\"status\":\"%s\",\"durationMs\":%d,\"rowsTotal\":%d,"
                        + "\"rowsOk\":%d,\"rowsFailed\":%d,\"kt\":\"knowledge\",\"ts\":%d}",
                jobId, taskId, tier, mode, status, durationMs, rowsTotal, rowsOk, rowsFailed,
                System.currentTimeMillis());
        if (kafkaTemplate == null) {
            log.warn("KbImportTaskExecutor.audit: KafkaTemplate 不可用，审计事件仅记 JDBC: jobId={} status={}",
                    jobId, status);
            return;
        }
        try {
            kafkaTemplate.send(KafkaTopics.AUDIT, payload);
            log.info("KB_IMPORT audit Kafka 已发: topic={} jobId={}", KafkaTopics.AUDIT, jobId);
        } catch (Exception e) {
            log.warn("KB_IMPORT audit Kafka 发送失败（不阻断业务）: jobId={} err={}", jobId, e.getMessage());
        }
    }

    /** 获取堆栈跟踪（用于 error callback）。 */
    private String getStackTrace(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
