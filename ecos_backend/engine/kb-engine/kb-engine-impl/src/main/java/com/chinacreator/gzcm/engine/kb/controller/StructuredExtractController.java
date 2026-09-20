package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractJobDetailVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractJobVO;
import com.chinacreator.gzcm.engine.kb.dto.StructuredExtractRequest;
import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * 结构化（映射驱动）实例抽取控制器 — PMO 批次 K1。
 *
 * <p>端点语义（均属 kb 引擎自有范围，路径落在 {@code KbEntityInstanceExtractionService}
 * 之上；业务数据端点默认 DENY，不写 permitAll）：
 * <ul>
 *   <li><b>E3</b> {@code POST /api/v1/knowledge/extract/structured} — 触发一次结构化抽取，
 *       返回结构化报告（dry-run 预览不落图谱）。非法模式 / ontologyId 抛
 *       {@code ValidationException} 400 拒绝。</li>
 *   <li><b>E4</b> {@code GET /api/v1/knowledge/extract/structured/jobs} — 抽取作业列表
 *       （按 kb 自有水位表最近 {@code updated_at} 倒序分页，jobId 缺失时返回 null）。</li>
 *   <li><b>E5</b> {@code GET /api/v1/knowledge/extract/structured/jobs/{jobId}} — 作业详情
 *       （jobId 前缀应为 {@code KBK1S-}；反查 kg_sync_log，无记录返回 NOT_FOUND + 说明）。</li>
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

    private final KbEntityInstanceExtractionService extractionService;

    private final JdbcTemplate jdbc;

    public StructuredExtractController(KbEntityInstanceExtractionService extractionService,
                                       JdbcTemplate jdbc) {
        this.extractionService = extractionService;
        this.jdbc = jdbc;
    }

    /**
     * E3 — 触发一次结构化映射驱动实例抽取。
     *
     * <p>入参 {@code ontologyId} 可选（空 / ALL = 全量本体快照），{@code mode} 取
     * FULL / INCREMENTAL（默认 INCREMENTAL），{@code dryRun} = true 时只统计不落库。
     * mode / ontologyId 非法抛 {@code ValidationException} 400 拒绝。</p>
     */
    @PostMapping
    public ApiResponse<EntityInstanceExtractionReportVO> extract(
            @RequestBody(required = false) StructuredExtractRequest body) {
        StructuredExtractRequest req = body == null ? new StructuredExtractRequest() : body;
        String mode = normalizeMode(req.getMode());
        boolean incremental = MODE_INCREMENTAL.equals(mode);
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());
        String jobId = JOB_ID_PREFIX + System.currentTimeMillis();
        log.info("E3 触发结构化抽取: jobId={} mode={} dryRun={} ontologyId={}",
                jobId, mode, dryRun, req.getOntologyId());
        EntityInstanceExtractionReportVO report;
        try {
            report = extractionService.extract(req.getOntologyId(), jobId, incremental, dryRun);
        } catch (ValidationException e) {
            // 入参校验失败（ontologyId 等）→ 400，而非 500
            log.warn("E3 触发结构化抽取失败 jobId={}: {}", jobId, e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        }
        return ApiResponse.success(report);
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
