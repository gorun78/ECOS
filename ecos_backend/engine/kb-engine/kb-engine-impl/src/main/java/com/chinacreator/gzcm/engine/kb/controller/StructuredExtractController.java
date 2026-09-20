package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.dto.ExportLogRequest;
import com.chinacreator.gzcm.engine.kb.dto.ExtractLogEntry;
import com.chinacreator.gzcm.engine.kb.dto.KbImportTaskStatusVO;
import com.chinacreator.gzcm.engine.kb.dto.KbImportTriggerVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractJobDetailVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractJobVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractRequest;
import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 结构化（映射驱动）实例抽取控制器 — PMO 批次 K1。
 *
 * <p>端点语义（均属 kb 引擎自有范围，路径落在 {@code KbEntityInstanceExtractionService}
 * 之上；业务数据端点默认 DENY，不写 permitAll）：
 * <ul>
 *   <li><b>E3/N1</b> {@code POST /api/v1/knowledge/extract/structured} — 触发一次结构化抽取：
 *       <ul>
 *         <li>{@code dryRun=true} — 同步执行，直接返回 {@link EntityInstanceExtractionReportVO}（保留既有契约）；</li>
 *         <li>{@code dryRun=false} — 异步路径：提交 runtime-task（KB_IMPORT），返回 {@link KbImportTriggerVO}{taskId,jobId}。</li>
 *       </ul>
 *       非法模式 / ontologyId 抛 {@code ValidationException} 400 拒绝。</li>
 *   <li><b>N2</b> {@code GET /api/v1/knowledge/extract/structured/status/{taskId}} —
 *       轮询异步任务状态（{@link KbImportTaskStatusVO}）。</li>
 *   <li><b>E4</b> {@code GET /api/v1/knowledge/extract/structured/jobs} — 抽取作业列表
 *       （按 kb 自有水位表最近 {@code updated_at} 倒序分页，jobId 缺失时返回 null）。</li>
 *   <li><b>E5</b> {@code GET /api/v1/knowledge/extract/structured/jobs/{jobId}} — 作业详情
 *       （jobId 前缀应为 {@code KBK1S-}；反查 kg_sync_log，无记录返回 NOT_FOUND + 说明）。</li>
 *   <li><b>T6-1</b> {@code GET /api/v1/knowledge/extract/structured/logs/{jobId}} —
 *       查询该 jobId 最近 100 行抽取审计日志（{@link ExtractLogEntry} 列表）；
 *       无 audit 记录时返回 {@code [FAIL_TO_RETRO]} 占位条目，不抛 500。</li>
 *   <li><b>T6-2</b> {@code POST /api/v1/knowledge/extract/structured/export-log} —
 *       导出抽取日志（入参 {@link ExportLogRequest}{jobId}），返回
 *       {@code ResponseEntity<byte[]>} {@code text/plain; charset=UTF-8}
 *       {@code Content-Disposition: attachment; filename="kb-extract-{jobId}.log"}；
 *       无 audit 记录时 404 "no audit records"。</li>
 * </ul>
 *
 * <p>不实现周期调度（{@code extract.periodic_enabled} 仅置配置开关，运行时调度留给 K4 批次，
 * 铁律 §2.5-3 禁止自建 {@code ScheduledExecutorService}）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract/structured")
public class StructuredExtractController {

    private static final Logger log = LoggerFactory.getLogger(StructuredExtractController.class);

    /** 模式：全量（忽略水位线，重读全部实例行）。 */
    private static final String MODE_FULL = "FULL";

    /** 模式：增量（按持久化水位线续读），默认。 */
    private static final String MODE_INCREMENTAL = "INCREMENTAL";

    /** jobId 前缀（E4/E5 反查 job 时按此前缀约束，避免误匹配其他 sync job）。 */
    private static final String JOB_ID_PREFIX = "KBK1S-";

    /** 异步任务类型（runtime-task 注册名，与 KbImportTaskRegistrar 保持一致）。 */
    private static final String KB_IMPORT_TASK_TYPE = "KB_IMPORT";

    /** 部署环境 tier（standard/enterprise/ultimate），export-log 头部落盘。 */
    private final String tier;

    private final KbEntityInstanceExtractionService extractionService;

    private final JdbcTemplate jdbc;

    private final ITaskManagementService taskManagementService;

    public StructuredExtractController(KbEntityInstanceExtractionService extractionService,
                                       JdbcTemplate jdbc,
                                       ITaskManagementService taskManagementService,
                                       @Value("${ecos.architecture.tier:standard}") String tier) {
        this.extractionService = extractionService;
        this.jdbc = jdbc;
        this.taskManagementService = taskManagementService;
        this.tier = tier;
    }

    /**
     * E3/N1 — 触发一次结构化映射驱动实例抽取。
     *
     * <p>入参 {@code ontologyId} 可选（空 / ALL = 全量本体快照），{@code mode} 取
     * FULL / INCREMENTAL（默认 INCREMENTAL），{@code dryRun} 控制同步/异步路径：
     * <ul>
     *   <li>{@code dryRun=true} — 同步执行，直接返回 {@link EntityInstanceExtractionReportVO}；</li>
     *   <li>{@code dryRun=false} — 异步路径：生成 jobId，构造 {@code TaskDescription}，
     *       经 {@code ITaskManagementService.submitTask} 提交，返回 {@link KbImportTriggerVO}。</li>
     * </ul>
     *
     * <p>说明：dryRun=false 时不会立即看到抽取结果，需通过 N2（{@code /status/{taskId}}）轮询；
     * 任务执行失败（如 ontology-engine 不可达）在 kb_extract_audit 标记 FAILED。</p>
     */
    @PostMapping
    public ApiResponse<?> extract(@RequestBody(required = false) StructuredExtractRequest body) {
        StructuredExtractRequest req = body == null ? new StructuredExtractRequest() : body;
        String mode = normalizeMode(req.getMode());
        boolean incremental = MODE_INCREMENTAL.equals(mode);
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());
        String jobId = JOB_ID_PREFIX + System.currentTimeMillis();
        log.info("E3 触发结构化抽取: jobId={} mode={} dryRun={} ontologyId={}",
                jobId, mode, dryRun, req.getOntologyId());

        // ── 同步路径：dryRun=true，保留既有契约 ──────────────────────────────────────────
        if (dryRun) {
            EntityInstanceExtractionReportVO report;
            try {
                report = extractionService.extract(req.getOntologyId(), jobId, incremental, true);
            } catch (ValidationException e) {
                log.warn("E3 触发结构化抽取失败 jobId={}: {}", jobId, e.getMessage());
                return ApiResponse.badRequest(e.getMessage());
            }
            return ApiResponse.success(report);
        }

        // ── 异步路径：dryRun=false，走 runtime-task ────────────────────────────────────
        TaskDescription desc = new TaskDescription();
        desc.setTaskName("结构化映射实例抽取: " + jobId);
        desc.setTaskType(KB_IMPORT_TASK_TYPE);
        desc.setDescription("异步执行结构化映射驱动实例抽取 jobId=" + jobId);
        Map<String, Object> params = new HashMap<>();
        params.put("ontologyId", req.getOntologyId());
        params.put("mode", mode);
        params.put("dryRun", false);
        params.put("jobId", jobId);
        desc.setParameters(params);
        desc.setAsync(true);

        String taskId;
        try {
            taskId = taskManagementService.submitTask(desc);
            // parseTask 同步完成（纯注册执行计划，无 IO）
            taskManagementService.parseTask(taskId);
        } catch (ValidationException e) {
            log.warn("E3 异步提交失败 jobId={}: {}", jobId, e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("E3 异步提交失败 jobId={}: {}", jobId, e.getMessage(), e);
            return ApiResponse.badRequest("异步任务提交失败: " + e.getMessage());
        }

        // 启动后台执行（executeTask 在线程池异步跑）
        final String tid = taskId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                taskManagementService.executeTask(tid);
            } catch (Exception e) {
                log.error("KB_IMPORT 后台执行失败 taskId={} jobId={}: {}", tid, jobId, e.getMessage(), e);
            }
        });

        log.info("E3 异步任务已提交: taskId={} jobId={} mode={} ontologyId={}",
                tid, jobId, mode, req.getOntologyId());
        KbImportTriggerVO vo = new KbImportTriggerVO();
        vo.setTaskId(tid);
        vo.setJobId(jobId);
        return ApiResponse.success(vo);
    }

    /**
     * N2 — 轮询异步抽取任务状态。
     *
     * <p>调 {@code ITaskManagementService.getTaskStatus(taskId)}，将 {@code TaskStatus}
     * 映射为强类型 {@link KbImportTaskStatusVO}；未找到任务返回 404。</p>
     */
    @GetMapping("/status/{taskId}")
    public ApiResponse<KbImportTaskStatusVO> getTaskStatus(@PathVariable String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.badRequest("taskId 不能为空");
        }
        try {
            TaskStatus status = taskManagementService.getTaskStatus(taskId);
            KbImportTaskStatusVO vo = new KbImportTaskStatusVO();
            vo.setTaskId(taskId);
            vo.setStatus(status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
            vo.setProgress(status.getProgress());
            vo.setStatusMessage(status.getStatusMessage());
            vo.setStartTime(status.getStartTime() != null ? status.getStartTime().getTime() : null);
            vo.setEndTime(status.getEndTime() != null ? status.getEndTime().getTime() : null);
            vo.setResult(status.getResult());
            vo.setErrorMessage(status.getErrorMessage());
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.warn("N2 查询任务状态失败 taskId={}: {}", taskId, e.getMessage());
            return ApiResponse.notFound("未找到任务状态: taskId=" + taskId);
        }
    }

    /**
     * E4 — 结构化抽取作业列表（按水位表最近更新倒序，分页）。
     *
     * <p>数据源：{@code ecos_knowledge.kb_extract_watermark}。水位表不冗余本次 mode / jobId，
     * 故 jobId 在列表侧保持 null（对应 E3 触发的 jobId 未在水位表落盘，溯源在 E5）；
     * status 对水位落库即为 SUCCESS；表未建 / 查询异常 → 返回空列表并 warn，不抛 500。</p>
     */
    @GetMapping("/jobs")
    public ApiResponse<List<StructuredExtractJobVO>> listJobs(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePageNum - 1) * safePageSize;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    "SELECT ontology_id, entity_code, resource_id, watermark, updated_at "
                            + "FROM ecos_knowledge.kb_extract_watermark "
                            + "ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    safePageSize, offset);
        } catch (DataAccessException e) {
            log.warn("E4 查询抽取水位失败（表未建或不可访问）: {}", e.getMessage());
            return ApiResponse.success(new ArrayList<>());
        }
        List<StructuredExtractJobVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            StructuredExtractJobVO vo = new StructuredExtractJobVO();
            vo.setJobId(null);
            vo.setMode(MODE_INCREMENTAL);
            vo.setStatus("SUCCESS");
            vo.setStartedAt(formatTs(row.get("updated_at")));
            vo.setDurationMs(null);
            out.add(vo);
        }
        return ApiResponse.success(out);
    }

    /**
     * E5 — 结构化抽取作业详情（按 jobId 反查 {@code kg_sync_log}）。
     *
     * <p>jobId 必须是 E3 生成的 {@code KBK1S-} 前缀格式；当前批次的抽取作业直接落图谱
     * 与水位（{@link KbEntityInstanceExtractionService#extract} 内不写 kg_sync_log，
     * 避免与 {@code KgSyncServiceImpl} 的 op 状态机混用），故通常 {@code status} 为
     * NOT_FOUND + detail 说明原因；若 kg_sync_log 中确有同前缀记录（未来批次或 Sync 侧
     * 落地），则返回对应 status + op / errorMessage / report 摘要。</p>
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<StructuredExtractJobDetailVO> getJob(@PathVariable String jobId) {
        if (jobId == null || !jobId.startsWith(JOB_ID_PREFIX)) {
            return ApiResponse.notFound("jobId 非法（需以 KBK1S- 开头）: " + jobId);
        }
        StructuredExtractJobDetailVO vo = new StructuredExtractJobDetailVO();
        vo.setJobId(jobId);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT status, op, report, error_message, created_at, finished_at "
                            + "FROM ecos_knowledge.kg_sync_log WHERE job_id = ? "
                            + "ORDER BY created_at DESC LIMIT 1",
                    jobId);
            if (rows.isEmpty()) {
                vo.setStatus("NOT_FOUND");
                vo.setDetail("kg_sync_log 中无此 jobId 记录（结构化抽取作业未回填 kg_sync_log，"
                        + "详细抽取结果见 E3 返回体 report）");
                return ApiResponse.success(vo);
            }
            Map<String, Object> row = rows.get(0);
            String status = row.get("status") == null ? "PENDING" : String.valueOf(row.get("status"));
            vo.setStatus(status.toUpperCase(Locale.ROOT));
            StringBuilder detail = new StringBuilder();
            Object op = row.get("op");
            if (op != null) {
                detail.append("op=").append(op).append("; ");
            }
            Object message = row.get("error_message");
            if (message != null && !String.valueOf(message).isBlank()) {
                detail.append("error=").append(message).append("; ");
            }
            detail.append("created_at=").append(formatTs(row.get("created_at")));
            Object finishedAt = row.get("finished_at");
            if (finishedAt != null) {
                detail.append(", finished_at=").append(formatTs(finishedAt));
            }
            Object report = row.get("report");
            if (report != null) {
                detail.append(", report=").append(report);
            }
            vo.setDetail(detail.toString());
            return ApiResponse.success(vo);
        } catch (DataAccessException e) {
            log.error("E5 查询作业详情失败 jobId={}: {}", jobId, e.getMessage(), e);
            return ApiResponse.badRequest("查询作业详情失败: " + e.getMessage());
        }
    }

    /**
     * T6 — 抽取日志查询：按 jobId 查 {@code kb_extract_audit} 最近 100 行。
     *
     * <p>每条 audit 行转 {@link ExtractLogEntry}{ts,level,message}，按 status 推断 level
     * （SUCCEEDED / RUNNING → INFO；FAILED / 其他 → ERROR）；无 audit 记录时
     * 返回占位条目 {@code [FAIL_TO_RETRO]}（不抛 500）。
     * 注：审计表为综合日志承载（与 {@code KbImportTaskExecutor} L209 同源）。
     */
    @GetMapping("/logs/{jobId}")
    public ApiResponse<List<ExtractLogEntry>> getLogs(@PathVariable String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return ApiResponse.badRequest("jobId 不能为空");
        }
        List<Map<String, Object>> rows = queryAudit(jobId);
        List<ExtractLogEntry> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(toLogEntry(row));
        }
        if (out.isEmpty()) {
            // 任务尚未执行 → 占位条目，让前端 UI 知道"无日志"而非"无错误"
            ExtractLogEntry warn = new ExtractLogEntry();
            warn.setTs(formatTs(new Timestamp(System.currentTimeMillis())));
            warn.setLevel("INFO");
            warn.setMessage("[FAIL_TO_RETRO]");
            out.add(warn);
        }
        return ApiResponse.success(out);
    }

    /**
     * T6 — 抽取日志导出（POST，JSON body = {@link ExportLogRequest}{jobId}）：
     * <ol>
     *   <li>复用 T6 同表查询 audit；</li>
     *   <li>拼装文本：{@code # 头部落盘 job / 导出时刻 / tier / status / duration / row 计数}
     *       + 每行 {@code "[{level}] {message}"}；</li>
     *   <li>输出 {@code ResponseEntity<byte[]>}：{@code Content-Type: text/plain; charset=UTF-8}；
     *       {@code Content-Disposition: attachment; filename="kb-extract-{jobId}.log"}。
     *       jobId 经 sanitize（非 [A-Za-z0-9_-] 全部替换为 "_"）。</li>
     *   <li>无 audit 记录 → 404 + 文本 "no audit records"。</li>
     * </ol>
     */
    @PostMapping("/export-log")
    public ResponseEntity<byte[]> exportLog(@RequestBody ExportLogRequest req) {
        if (req == null || req.getJobId() == null || req.getJobId().isBlank()) {
            return ResponseEntity.status(400).body("jobId is required".getBytes(StandardCharsets.UTF_8));
        }
        String jobId = req.getJobId();
        List<Map<String, Object>> rows = queryAudit(jobId);
        if (rows.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                    .body("no audit records".getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Knowledge Extraction Log ").append('—').append(' ').append(jobId).append('\n');
        sb.append("# Exported: ").append(Instant.now().toString()).append('\n');
        sb.append("# Tier: ").append(tier == null ? "standard" : tier.toLowerCase(Locale.ROOT)).append('\n');
        // 头部落盘首行口径（status / durationMs / rows_ok / rows_failed 取最新一条 audit）
        Map<String, Object> top = rows.get(0);
        String status = top.get("status") == null ? "PENDING" : String.valueOf(top.get("status"));
        Object durationMs = top.get("duration_ms");
        Object rowsOk = top.get("rows_ok");
        Object rowsFailed = top.get("rows_failed");
        sb.append("# Status: ").append(status).append('\n');
        sb.append("# Duration: ")
                .append(durationMs == null ? "null" : String.valueOf(durationMs))
                .append("ms | Rows OK: ")
                .append(rowsOk == null ? "null" : String.valueOf(rowsOk))
                .append(" | Failed: ")
                .append(rowsFailed == null ? "null" : String.valueOf(rowsFailed))
                .append('\n');
        sb.append('\n');
        for (Map<String, Object> row : rows) {
            ExtractLogEntry entry = toLogEntry(row);
            sb.append("[").append(entry.getTs()).append("] [").append(entry.getLevel()).append("] ")
                    .append(nvl(entry.getMessage())).append('\n');
        }
        String safeName = sanitizeFilename(jobId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/plain; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"kb-extract-" + safeName + ".log\"");
        return ResponseEntity.ok().headers(headers).body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 查 audit 表（jobId → 最近 100 行，created_at DESC）；查不到返回空列表，不抛异常。 */
    private List<Map<String, Object>> queryAudit(String jobId) {
        try {
            return jdbc.queryForList(
                    "SELECT id, job_id, task_id, tier, mode, status, duration_ms, "
                            + "rows_total, rows_ok, rows_failed, mismatched, error_message, created_at "
                            + "FROM ecos_knowledge.kb_extract_audit WHERE job_id = ? "
                            + "ORDER BY created_at DESC LIMIT 100",
                    jobId);
        } catch (DataAccessException e) {
            log.warn("T6 查询 audit 失败 jobId={}: {}", jobId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** audit 行 → ExtractLogEntry（status 推断 level：SUCCEEDED/RUNNING→INFO，其余→ERROR）。 */
    private static ExtractLogEntry toLogEntry(Map<String, Object> row) {
        ExtractLogEntry entry = new ExtractLogEntry();
        entry.setTs(formatTs(row.get("created_at")));
        Object statusObj = row.get("status");
        String status = statusObj == null ? "" : String.valueOf(statusObj);
        String level = (status.equalsIgnoreCase("SUCCEEDED") || status.equalsIgnoreCase("RUNNING"))
                ? "INFO" : "ERROR";
        entry.setLevel(level);
        StringBuilder sb = new StringBuilder();
        sb.append("job=").append(row.get("job_id"));
        if (statusObj != null) sb.append(" status=").append(status);
        Object durationMs = row.get("duration_ms");
        if (durationMs != null) sb.append(" durationMs=").append(durationMs);
        Object rowsOk = row.get("rows_ok");
        if (rowsOk != null) sb.append(" rowsOk=").append(rowsOk);
        Object rowsFailed = row.get("rows_failed");
        if (rowsFailed != null) sb.append(" rowsFailed=").append(rowsFailed);
        Object errorMessage = row.get("error_message");
        if (errorMessage != null && !String.valueOf(errorMessage).isBlank()) {
            sb.append(" error=").append(errorMessage);
        }
        entry.setMessage(sb.toString());
        return entry;
    }

    /** filename sanitize：保留 [A-Za-z0-9_-]，其他替换为 '_'。 */
    private static String sanitizeFilename(String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** null 安全 toString。 */
    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 模式归一化：null/空白 / INCREMENTAL → INCREMENTAL；FULL → FULL；其他抛 {@code ValidationException}。 */
    private static String normalizeMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODE_INCREMENTAL;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (MODE_FULL.equals(value)) {
            return MODE_FULL;
        }
        if (MODE_INCREMENTAL.equals(value)) {
            return MODE_INCREMENTAL;
        }
        throw new ValidationException(
                "抽取模式非法: mode=" + raw + "（允许 FULL / INCREMENTAL）");
    }

    /** Timestamp → ISO-8601（null 安全，异常降级为原始 toString）。 */
    private static String formatTs(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return String.valueOf(raw);
    }
}
