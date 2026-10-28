package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunReportVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractRunSubmitVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractWatermarkListVO;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeExtractWatermarkVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeExtractRunService;
import com.chinacreator.gzcm.engine.kb.service.StructuredExtractQueryService;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService.TaskManagementException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识抽取运行族 Controller — 详设-07 §7.3.2 端点总表第 12/13 行
 * （台账《从零实施版》缺失清单第 12/13 条）。
 *
 * <p>四端点（路径/入参/出参逐字承契约）：</p>
 * <ul>
 *   <li><b>{@code POST /api/v1/knowledge/extract/runs}</b>（档 D）→ 202
 *       {@code {taskId,status:"SUBMITTED",traceId}}，异步派 runtime-task {@code KB_IMPORT}；</li>
 *   <li><b>{@code GET /api/v1/knowledge/extract/runs/{taskId}}</b>（档 B）→
 *       {@code {created,updated,issues[],c3Skipped,vectorDegraded,watermarkAdvanced}}；</li>
 *   <li><b>{@code POST /api/v1/knowledge/extract/dry-run}</b>（档 D）→ 预演别名，同步出报告、
 *       水位线不推进，带 {@code Deprecation}/{@code Sunset}/{@code Link: rel="successor-version"}；</li>
 *   <li><b>{@code GET /api/v1/knowledge/extract/watermarks}</b>（档 B）→
 *       {@code {items,total,page,size}}（V179 {@code ecos_kb_extract_watermark} 活行）。</li>
 * </ul>
 *
 * <p><b>状态行保真（D-B4-59 同族纪律）</b>：四条腿的状态行各有落法但互不同形——
 * 400 入参／404 未知 taskId 经 {@code ResponseEntity} 显式承载；409 快照缺失与 503 任务底座
 * 不可用改抛 {@link KbErrorCodes} 携带状态的载体，由 {@code KbErrorAdvice} 单源渲染
 * （同域其余端点同源，禁本类自造第二套 503 落法）；500 表缺失由 {@code DataAccessException}
 * 上浮到同一 advice。拒绝信封 {@code code} 与状态行同号，机读码一律取 {@link KbErrorCodes} 单源。</p>
 *
 * <p><b>与 E3 的单腿关系</b>：提交与预演的编排腿在 {@link KnowledgeExtractRunService}，
 * 本类与 {@link StructuredExtractController} 共用同一份 {@code TaskDescription} 参数键面
 * （{@code ontologyId/mode/dryRun/jobId/biz_kind}），避免两入口各写一遍后静默漂移
 * （如 dryRun 丢失 → 预演真写库）。</p>
 *
 * <p><b>如实声明（禁假值）</b>：</p>
 * <ul>
 *   <li>{@code vectorDegraded} —— 抽取链（映射驱动实例化）不触碰向量索引，<b>无该信号源</b>，
 *       出 {@code null} 并以 {@code vectorDegradedApplicable=false} 明示不适用，不写死 false；</li>
 *   <li>{@code sourceRef} / {@code fieldMappings} —— 无载体（映射契约由本体映射台账承载），
 *       非空即 400，不静默忽略；</li>
 *   <li>{@code sourceType} —— 词表当前仅 {@code ONTOLOGY_MAPPING}，其他取值 400；
 *       水位表无 {@code source_type} 列，故该值在 responses 中只做口径回显，不作 SQL 谓词；</li>
 *   <li>契约列的 {@code 012}（C1/C2 硬失败 422）与 {@code 021}（任务底座 503）——
 *       {@code 021} 在提交腿可达；读取腿不跑闸链故不可达 {@code 012}，不凑一条假路径。</li>
 * </ul>
 *
 * @group EXTRACT
 */
@RestController
@RequestMapping("/api/v1/knowledge/extract")
public class KnowledgeExtractRunsController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractRunsController.class);

    private static final ObjectMapper MAPPER =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** 分页默认值（§7.3.1：默认 50 上限 500）。 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** 分页上限（§7.3.1）。 */
    private static final int MAX_PAGE_SIZE = 500;

    /** 预演别名的弃用头取值（§7.3.1 演进约定三件套）。 */
    private static final String SUNSET_AT = "Thu, 28 Oct 2027 00:00:00 GMT";

    /** 后继版本指针：{@code POST /runs} + {@code dryRun=true} 为取代形态。 */
    private static final String SUCCESSOR_LINK =
            "</api/v1/knowledge/extract/runs>; rel=\"successor-version\"";

    private final KnowledgeExtractRunService runService;

    private final StructuredExtractQueryService queryService;

    private final ITaskManagementService taskManagement;

    public KnowledgeExtractRunsController(KnowledgeExtractRunService runService,
                                          StructuredExtractQueryService queryService,
                                          ITaskManagementService taskManagement) {
        this.runService = runService;
        this.queryService = queryService;
        this.taskManagement = taskManagement;
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /runs — 提交抽取（202）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 提交一次抽取运行（档 D）。{@code dryRun=true} 时仍走 runtime-task（真执行腿同参），
     * {@code dryRun=false} 常规；两者语义差异由执行腿承载，入口不另立分支。
     *
     * <p>准入顺序：① 入参无载体/词表校验（400）→ ② 本体快照前置闸（409 {@code ECOS-KB-011}，
     * <b>在 try 之外</b>，禁被下面的 {@code RuntimeException} 腿折成 503）→ ③ 任务底座
     * （503 {@code ECOS-KB-021}，禁降级为本地线程同步计算）。</p>
     */
    @PostMapping("/runs")
    @RequirePermission(value = "提交知识抽取", permission = "knowledge:extract:submit")
    public ResponseEntity<ApiResponse<KnowledgeExtractRunSubmitVO>> submitRun(
            @RequestBody(required = false) KnowledgeExtractRunRequest body) {
        KnowledgeExtractRunRequest req = body == null ? new KnowledgeExtractRunRequest() : body;
        String invalid = validateRequest(req);
        if (invalid != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeExtractRunSubmitVO>badRequest(invalid));
        }
        String scope = req.getOntologySnapshotId();
        runService.requireSnapshot(scope);   // 409 ECOS-KB-011：不得落进下面的 503 腿

        String jobId = runService.newJobId();
        KnowledgeExtractRunService.SubmitOutcome outcome;
        try {
            outcome = runService.submit(scope, KnowledgeExtractRunService.MODE_INCREMENTAL,
                    Boolean.TRUE.equals(req.getDryRun()), jobId);
        } catch (TaskManagementException | RuntimeException e) {
            log.error("submitKnowledgeExtraction 提交失败 jobId={}: {}", jobId, e.getMessage(), e);
            // 改抛本域携带 503 的载体：状态行与机读码由 KbErrorAdvice 单源渲染（同域其余端点同源），
            // 控制器侧不再自造信封——否则「同一个 503」在本域出现两套落法（D-B4-67 同族）。
            throw KbErrorCodes.TASK_BASE_UNAVAILABLE.exception(
                    "runtime-task 不可用，禁降级为本地线程同步计算: " + e.getMessage());
        }

        String traceId = currentTraceId();
        KnowledgeExtractRunSubmitVO vo = new KnowledgeExtractRunSubmitVO();
        vo.setTaskId(outcome.taskId());
        vo.setJobId(outcome.jobId());
        vo.setStatus("SUBMITTED");
        vo.setTraceId(traceId);
        log.info("submitKnowledgeExtraction 已受理 taskId={} jobId={} ontologySnapshotId={} dryRun={}",
                outcome.taskId(), outcome.jobId(), scope, req.getDryRun());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /runs/{taskId} — 抽取报告与进度
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 读取一次抽取的报告与进度（档 B）。
     *
     * <p>两源合流：runtime-task 任务状态（进度/起止/result JSON）为主，
     * {@code ecos_kb_extract_audit.task_id} 为辅（任务存储已清或重启后的存在性与 jobId/mode 兜底）。
     * <b>两源皆无 ⇒ 404</b>（未知 taskId 不得伪装成空报告）；<b>审计表读故障且任务状态缺失 ⇒
     * 原样上浮</b>（由 advice 落 500 {@code ECOS-KB-050}），禁折成 404。</p>
     */
    @GetMapping("/runs/{taskId}")
    @RequirePermission(value = "读取知识抽取报告", permission = "knowledge:extract:read")
    public ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> getRun(@PathVariable String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeExtractRunReportVO>badRequest("taskId 不能为空"));
        }

        TaskStatus status = null;
        try {
            status = taskManagement.getTaskStatus(taskId);
        } catch (Exception e) {
            log.debug("getKnowledgeExtractionReport 任务状态不可读 taskId={}: {}", taskId, e.getMessage());
        }

        List<Map<String, Object>> auditRows;
        try {
            auditRows = queryService.findLatestAuditByTaskId(taskId);
        } catch (DataAccessException e) {
            if (status == null) {
                throw e;   // 无法判定存在性 ⇒ 500 ECOS-KB-050，禁折成 404
            }
            log.warn("getKnowledgeExtractionReport 审计反查失败（不阻断，任务状态已就绪）taskId={}: {}",
                    taskId, e.getMessage());
            auditRows = List.of();
        }
        Map<String, Object> audit = auditRows.isEmpty() ? null : auditRows.get(0);

        if (status == null && audit == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<KnowledgeExtractRunReportVO>notFound(
                            "未找到抽取任务: taskId=" + taskId));
        }

        KnowledgeExtractRunReportVO vo = new KnowledgeExtractRunReportVO();
        vo.setTaskId(taskId);
        vo.setVectorDegraded(null);
        vo.setVectorDegradedApplicable(Boolean.FALSE);

        if (status != null) {
            vo.setStatus(status.getStatus() != null ? status.getStatus().name() : "UNKNOWN");
            vo.setProgress(status.getProgress());
            vo.setStatusMessage(status.getStatusMessage());
            vo.setErrorMessage(status.getErrorMessage());
            vo.setStartedAt(status.getStartTime() != null ? status.getStartTime().getTime() : null);
            vo.setFinishedAt(status.getEndTime() != null ? status.getEndTime().getTime() : null);
            applyReportJson(vo, parseJson(status.getResult()));
        } else {
            vo.setStatus(audit.get("status") == null
                    ? "UNKNOWN" : String.valueOf(audit.get("status")).toUpperCase(Locale.ROOT));
            vo.setErrorMessage(textOrNull(audit.get("error_message")));
            vo.setReportAvailable(Boolean.FALSE);
        }

        // 审计行兜底补齐：报告 JSON 不带 jobId/mode 时以台账为准（无则保持 null，不合成）
        if (vo.getJobId() == null && audit != null) {
            vo.setJobId(textOrNull(audit.get("job_id")));
        }
        if (vo.getMode() == null && audit != null) {
            vo.setMode(textOrNull(audit.get("mode")));
        }
        if (vo.getDurationMs() == null && audit != null) {
            vo.setDurationMs(longOrNull(audit.get("duration_ms")));
        }
        return ResponseEntity.ok(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /dry-run — 预演别名（弃用头三件套）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 预演（档 D）：同步返回报告，<b>不落库、水位线不推进</b>。
     *
     * <p>弃用形态：{@code Deprecation} + {@code Sunset} + {@code Link: rel="successor-version"}
     * 指向 {@code POST /runs}（{@code dryRun=true} 即等价），按 §7.3.1「旧别名保留 ≥2 迭代」。
     * 入参校验与快照闸与 {@code POST /runs} 同源（同一 {@link #validateRequest} 与
     * {@link KnowledgeExtractRunService#requireSnapshot}）。</p>
     */
    @PostMapping("/dry-run")
    @RequirePermission(value = "预演知识抽取", permission = "knowledge:extract:preview")
    public ResponseEntity<ApiResponse<KnowledgeExtractRunReportVO>> dryRun(
            @RequestBody(required = false) KnowledgeExtractRunRequest body) {
        KnowledgeExtractRunRequest req = body == null ? new KnowledgeExtractRunRequest() : body;
        String invalid = validateRequest(req);
        if (invalid != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeExtractRunReportVO>badRequest(invalid));
        }
        String scope = req.getOntologySnapshotId();
        runService.requireSnapshot(scope);   // 409 ECOS-KB-011

        String jobId = runService.newJobId();
        EntityInstanceExtractionReportVO report =
                runService.preview(scope, jobId, true);   // 真跑一遍抽取，dryRun=true 内部不写库
        KnowledgeExtractRunReportVO vo = fromReport(report);
        vo.setJobId(jobId);
        vo.setStatus("SUCCEEDED");   // 同步腿已返回 ⇒ 本次预演完成；失败以异常上浮，无半成品
        log.info("previewKnowledgeExtraction 完成 jobId={} ontologySnapshotId={} created={} updated={}",
                jobId, scope, vo.getCreated(), vo.getUpdated());

        HttpHeaders headers = new HttpHeaders();
        // Spring 6.1 的 HttpHeaders 无 DEPRECATION 常量，按同仓 KnowledgeWorkbenchController 先例写字面名
        headers.set("Deprecation", "true");
        headers.set("Sunset", SUNSET_AT);
        headers.set(HttpHeaders.LINK, SUCCESSOR_LINK);
        return ResponseEntity.ok().headers(headers)
                .body(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /watermarks — 水位线（V179 活行）
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 水位线列表（档 B）：{@code ecos_kb_extract_watermark} 活行（{@code is_deleted=0}），
     * 按 {@code update_time} 倒序分页。
     *
     * <p>{@code sourceType} 为口径回显：V179 无 {@code source_type} 列，全部行均属
     * {@link KnowledgeExtractRunService#SOURCE_TYPE_ONTOLOGY_MAPPING}；传其他取值 400 显式拒绝，
     * <b>不做「声明了过滤但 SQL 无谓词」的假过滤</b>。表缺失/不可访问 → 500 {@code ECOS-KB-050}
     * （由 {@code KbErrorAdvice} 落，禁伪 404）。</p>
     */
    @GetMapping("/watermarks")
    @RequirePermission(value = "读取抽取水位线", permission = "knowledge:extract:read")
    public ResponseEntity<ApiResponse<KnowledgeExtractWatermarkListVO>> listWatermarks(
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        String sourceTypeError = sourceTypeError(sourceType);
        if (sourceTypeError != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<KnowledgeExtractWatermarkListVO>badRequest(sourceTypeError));
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> rows = queryService.listWatermarkItems(safeSize, offset);
        long total = queryService.countWatermarkItems();

        List<KnowledgeExtractWatermarkVO> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            KnowledgeExtractWatermarkVO item = new KnowledgeExtractWatermarkVO();
            item.setOntologyId(textOrNull(row.get("ontology_id")));
            item.setEntityCode(textOrNull(row.get("entity_code")));
            item.setResourceId(textOrNull(row.get("resource_id")));
            item.setWatermark(textOrNull(row.get("watermark")));
            item.setTraceId(textOrNull(row.get("trace_id")));
            item.setDomain(textOrNull(row.get("domain")));
            item.setVersionNo(textOrNull(row.get("version_no")));
            item.setUpdatedAt(formatTs(row.get("updated_at")));
            items.add(item);
        }

        KnowledgeExtractWatermarkListVO vo = new KnowledgeExtractWatermarkListVO();
        vo.setItems(items);
        vo.setTotal(total);
        vo.setPage(safePage);
        vo.setSize(safeSize);
        vo.setSourceType(KnowledgeExtractRunService.SOURCE_TYPE_ONTOLOGY_MAPPING);
        return ResponseEntity.ok(ApiResponse.success(vo).withTraceIdFromMdc());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 入参校验 / 报告投影 / 小工具
    // ─────────────────────────────────────────────────────────────────────

    /** 提交/预演共用入参闸：返回错误文案，{@code null} = 通过。 */
    private static String validateRequest(KnowledgeExtractRunRequest req) {
        String sourceTypeError = sourceTypeError(req.getSourceType());
        if (sourceTypeError != null) {
            return sourceTypeError;
        }
        if (req.getSourceRef() != null && !req.getSourceRef().isBlank()) {
            return "sourceRef 无载体：抽取源由本体映射台账决定（不接受请求指定来源引用）";
        }
        if (req.getFieldMappings() != null && !req.getFieldMappings().isEmpty()) {
            return "fieldMappings 无载体：字段映射由本体映射台账承载（禁请求体内联映射绕过台账校验）";
        }
        return null;
    }

    /** {@code sourceType} 词表闸：缺省同 {@code ONTOLOGY_MAPPING}，其余取值拒绝。 */
    private static String sourceTypeError(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        if (KnowledgeExtractRunService.SOURCE_TYPE_ONTOLOGY_MAPPING.equalsIgnoreCase(sourceType.trim())) {
            return null;
        }
        return "sourceType 不支持: " + sourceType + "（当前仅 "
                + KnowledgeExtractRunService.SOURCE_TYPE_ONTOLOGY_MAPPING + "）";
    }

    /** 把同步抽取报告映射为出参（契约键 created/updated 取 nodeCreated/nodeUpdated 单源）。 */
    private static KnowledgeExtractRunReportVO fromReport(EntityInstanceExtractionReportVO report) {
        KnowledgeExtractRunReportVO vo = new KnowledgeExtractRunReportVO();
        vo.setCreated(report.getNodeCreated());
        vo.setUpdated(report.getNodeUpdated());
        vo.setC3Skipped(report.isC3Skipped());
        vo.setVectorDegraded(null);
        vo.setVectorDegradedApplicable(Boolean.FALSE);
        vo.setNextWatermark(report.getNextWatermark());
        vo.setWatermarkAdvanced(report.getNextWatermark() != null && !report.getNextWatermark().isBlank());
        vo.setMode(report.getMode());
        vo.setDryRun(report.isDryRun());
        vo.setDurationMs(report.getDurationMs());
        vo.setOntologies(new ArrayList<>(report.getOntologies()));
        vo.setOntologyCount(report.getOntologyCount());
        vo.setEntityCount(report.getEntityCount());
        vo.setNodeSkipped(report.getNodeSkipped());
        vo.setEdgeCreated(report.getEdgeCreated());
        vo.setInvalidMappings(report.getInvalidMappings());
        vo.setTruncated(report.isTruncated());
        vo.setRowsRead(report.getRowsRead());
        vo.setReportAvailable(Boolean.TRUE);
        if (report.getIssues() != null) {
            for (EntityInstanceExtractionReportVO.IssueVO issue : report.getIssues()) {
                KnowledgeExtractRunReportVO.IssueVO out = new KnowledgeExtractRunReportVO.IssueVO();
                out.setEntityCode(issue.getEntityCode());
                out.setCode(issue.getCode());
                out.setMessage(issue.getMessage());
                vo.getIssues().add(out);
            }
        }
        return vo;
    }

    /**
     * 把 runtime-task {@code result} 的报告 JSON 投影进出参。
     *
     * <p>{@code result} 由 {@code KbImportTaskExecutor} 写入（{@code EntityInstanceExtractionReportVO}
     * 序列化，另一个形状是序列化失败时的极简兜底 JSON）；两者均可解析为对象即视为「报告可读」。
     * 不可解析（null/空串/非对象）⇒ {@code reportAvailable=false} 且不填任何计数
     * （禁把「读不到」渲染成「0 条」）。</p>
     */
    private static void applyReportJson(KnowledgeExtractRunReportVO vo, JsonNode node) {
        if (node == null || !node.isObject()) {
            vo.setReportAvailable(Boolean.FALSE);
            return;
        }
        vo.setReportAvailable(Boolean.TRUE);
        vo.setJobId(textOrNull(node, "jobId"));
        vo.setCreated(intOrNull(node, "nodeCreated"));
        vo.setUpdated(intOrNull(node, "nodeUpdated"));
        vo.setC3Skipped(boolOrNull(node, "c3Skipped"));
        vo.setNextWatermark(textOrNull(node, "nextWatermark"));
        String next = vo.getNextWatermark();
        vo.setWatermarkAdvanced(next != null && !next.isBlank());
        vo.setMode(textOrNull(node, "mode"));
        vo.setDryRun(boolOrNull(node, "dryRun"));
        vo.setDurationMs(longOrNull(node, "durationMs"));
        vo.setOntologyCount(intOrNull(node, "ontologyCount"));
        vo.setEntityCount(intOrNull(node, "entityCount"));
        vo.setNodeSkipped(intOrNull(node, "nodeSkipped"));
        vo.setEdgeCreated(intOrNull(node, "edgeCreated"));
        vo.setInvalidMappings(intOrNull(node, "invalidMappings"));
        vo.setTruncated(boolOrNull(node, "truncated"));
        vo.setRowsRead(intOrNull(node, "rowsRead"));
        JsonNode ontologies = node.get("ontologies");
        if (ontologies != null && ontologies.isArray()) {
            List<String> list = new ArrayList<>(ontologies.size());
            for (JsonNode item : ontologies) {
                if (item != null && !item.isNull()) {
                    list.add(item.asText());
                }
            }
            vo.setOntologies(list);
        }
        JsonNode issues = node.get("issues");
        if (issues != null && issues.isArray()) {
            for (JsonNode item : issues) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                KnowledgeExtractRunReportVO.IssueVO out = new KnowledgeExtractRunReportVO.IssueVO();
                out.setEntityCode(textOrNull(item, "entityCode"));
                out.setCode(textOrNull(item, "code"));
                out.setMessage(textOrNull(item, "message"));
                vo.getIssues().add(out);
            }
        }
    }

    /** {@code result} 文本 → JsonNode；不可解析时返回 {@code null}（由调用方置 reportAvailable=false）。 */
    private static JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("抽取报告 JSON 解析失败（按不可读处理）: {}", e.getMessage());
            return null;
        }
    }

    /** 当前请求的 traceId（信封与提交应答同值；异步/无 MDC 时为 null）。 */
    private static String currentTraceId() {
        try {
            String tid = MDC.get("traceId");
            return tid == null || tid.isBlank() ? null : tid;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? null : v.asInt();
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isNumber() ? null : v.asLong();
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || !v.isBoolean() ? null : v.asBoolean();
    }

    /** DB 列取值 → 文本（null 保持 null，不把 null 渲染成 "null"）。 */
    private static String textOrNull(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    /** DB 列取值 → Long（PG BIGINT/NUMERIC 驱动返回 Number；非数值保持 null）。 */
    private static Long longOrNull(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Timestamp → ISO-8601（与 E4/E5 同口径，null 安全，异常降级为原始 toString）。 */
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
