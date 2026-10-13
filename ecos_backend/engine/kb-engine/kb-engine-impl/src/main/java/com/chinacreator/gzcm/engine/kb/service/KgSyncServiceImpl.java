package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import com.chinacreator.gzcm.engine.kb.dao.KgSyncLogDao;
import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.support.KbAuditEventPublisher;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * KG 图谱快照同步服务 — PMO-50 T2。
 *
 * <p>改造点（原实现为内存 ConcurrentLinkedQueue stub,重启即丢）：
 * <ul>
 *   <li>{@link #getSyncStatus()} 改读 PG {@code ecos_knowledge.graph_node} 按 domain 聚合；</li>
 *   <li>{@link #triggerFullSync} / {@link #triggerObjectSync}：写 {@code kg_sync_log} 行（V115__kg_sync_log.sql），
 *       随后通过 {@link KgMapperService#syncFromOntology(String, String)} 异步执行真实同步；</li>
 *   <li>{@link #getSyncLogs(int)} 读 {@code kg_sync_log} 最近 N 条；</li>
 *   <li>审计走 {@code KafkaTopics.AUDIT}（Kafka 不可用时 log 兜底，不阻塞主流程，铁律 §2.4 #5）。
 * </ul>
 */
@Service
public class KgSyncServiceImpl implements KgSyncService {

    private static final Logger log = LoggerFactory.getLogger(KgSyncServiceImpl.class);
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    /** 抽取模式：全量（读全部映射本体实例，忽略水位线）。 */
    private static final String MODE_FULL = "FULL";

    /** 抽取模式：增量（按持久化水位线续读，PMO-B3-2 / K4）。 */
    private static final String MODE_INCREMENTAL = "INCREMENTAL";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KgSyncLogDao kgSyncLogDao;
    private final KgMapperService kgMapper;

    /** 图谱实例抽取服务（PMO-B3-2：映射契约驱动 DW 层实例抽取）。 */
    private final KbEntityInstanceExtractionService instanceExtraction;

    /** 自身代理引用（@Lazy 破循环依赖）——使 trigger* 内部调用走 @Async 代理，避免长任务阻塞 HTTP 线程。 */
    private final KgSyncService self;

    public KgSyncServiceImpl(KgSyncLogDao kgSyncLogDao,
                            @Lazy KgMapperService kgMapper,
                            @Lazy KbEntityInstanceExtractionService instanceExtraction,
                            @Lazy KgSyncService self) {
        this.kgSyncLogDao = kgSyncLogDao;
        this.kgMapper = kgMapper;
        this.instanceExtraction = instanceExtraction;
        this.self = self;
    }

    private String nowIso() {
        return ISO_FMT.format(Instant.now());
    }

    /**
     * 真实聚合 knowledge_node 按 domain 类型同步状态（V115 kg_sync_log 提供 lastSyncTime）。
     */
    @Override
    public List<Map<String, Object>> getSyncStatus() {
        List<Map<String, Object>> buckets = new ArrayList<>();
        String[] typeOrder = {"Table", "Column", "Task", "Indicator"};
        Map<String, Map<String, Object>> bucket = new LinkedHashMap<>();
        for (String t : typeOrder) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("type", t);
            b.put("syncedCount", 0);
            b.put("totalCount", 0);
            b.put("lastSyncTime", null);
            bucket.put(t, b);
            buckets.add(b);
        }
        try {
            List<Map<String, Object>> rows = kgSyncLogDao.syncStatusByDomain();
            for (Map<String, Object> row : rows) {
                String t = row.get("type") == null ? "" : String.valueOf(row.get("type"));
                if (t.isBlank()) {
                    continue;
                }
                Map<String, Object> b = bucket.get(t);
                if (b == null) {
                    b = new LinkedHashMap<>();
                    b.put("type", t);
                    buckets.add(b);
                }
                b.put("syncedCount", ((Number) row.getOrDefault("syncedCount", 0L)).longValue());
                b.put("totalCount", b.get("syncedCount"));
                Object lts = row.get("lastSyncTime");
                b.put("lastSyncTime", lts == null ? null : lts.toString());
            }
        } catch (Exception e) {
            log.warn("getSyncStatus failed (table missing?): {}", e.getMessage());
        }
        // 全量统计行（type=ALL）
        try {
            List<Map<String, Object>> rows = kgSyncLogDao.syncStatusOverall();
            if (!rows.isEmpty()) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("type", "ALL");
                b.put("syncedCount", ((Number) rows.get(0).getOrDefault("syncedCount", 0L)).longValue());
                b.put("totalCount", b.get("syncedCount"));
                Object lts = rows.get(0).get("lastSyncTime");
                b.put("lastSyncTime", lts == null ? null : lts.toString());
                buckets.add(b);
            }
        } catch (Exception ignore) {
            // table missing 时保底不抛
        }
        return buckets;
    }

    @Override
    public String getOverallStatus() {
        try {
            Integer count = kgSyncLogDao.countSyncLog();
            return (count != null && count > 0) ? "ready" : "idle";
        } catch (Exception e) {
            log.warn("getOverallStatus failed: {}", e.getMessage());
            return "idle";
        }
    }

    @Override
    public void triggerFullSync(String syncId) {
        String jobId = "tg-full-" + System.currentTimeMillis();
        insertLog("ALL", "FULL_SYNC", jobId, STATUS_RUNNING, 0, 0, 0, null);
        emitAudit("kg_sync_full_submit", jobId);
        // 经 self 代理调用，走 @Async（同 Bean 直接自调用会绕过代理，长任务将阻塞 HTTP 线程）
        self.runKgMapper("ALL", jobId, MODE_FULL);
        log.info("Full kg sync submitted: jobId={}", jobId);
    }

    @Override
    public void triggerObjectSync(String syncId, String objectType) {
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("objectType required");
        }
        String jobId = "tg-obj-" + System.currentTimeMillis();
        insertLog(objectType, "OBJECT_SYNC", jobId, STATUS_RUNNING, 0, 0, 0, null);
        emitAudit("kg_sync_object_submit", jobId + ":" + objectType);
        // 单类型同步按增量语义（T2：triggerObjectSync → INCREMENTAL；无水位时自动退化为全量）
        self.runKgMapper(objectType, jobId, MODE_INCREMENTAL);
        log.info("Object kg sync submitted: object={}, jobId={}", objectType, jobId);
    }

    /**
     * 图谱构建（模式化提交，PMO-B3-2 T3）：写 RUNNING 台账后异步执行骨架对齐 + 实例抽取。
     *
     * <p>FULL 语义（不变）：读全部映射本体的实例，忽略水位线；
     * INCREMENTAL：按 {@code kb_extract_watermark} 水位线续读。
     */
    @Override
    public void triggerBuildSync(String jobId, String mode) {
        triggerBuildSync(jobId, mode, false);
    }

    /**
     * 图谱构建（模式化 + dry-run 提交，PMO-B3-2 T4）。
     *
     * @param jobId  任务 ID
     * @param mode   {@code FULL} / {@code INCREMENTAL}
     * @param dryRun {@code true} = 预览（不写图谱，仅回填结构化报告到 kg_sync_log.report）
     */
    @Override
    public void triggerBuildSync(String jobId, String mode, boolean dryRun) {
        String normalized = normalizeMode(mode);
        String effectiveJobId = (jobId == null || jobId.isBlank())
                ? "build-" + System.currentTimeMillis() : jobId;
        String op;
        if (dryRun) {
            op = "DRY_RUN";
        } else {
            op = MODE_INCREMENTAL.equals(normalized) ? "INCREMENTAL_SYNC" : "FULL_SYNC";
        }
        insertLog("ALL", op, effectiveJobId, STATUS_RUNNING, 0, 0, 0, null);
        emitAudit("kg_sync_build_submit", effectiveJobId + ":" + normalized + ":dryRun=" + dryRun);
        self.runKgMapper("ALL", effectiveJobId, normalized, dryRun);
        log.info("KG build submitted: jobId={}, mode={}, dryRun={}", effectiveJobId, normalized, dryRun);
    }

    /**
     * 将 ontologies 对象映射到 graph_node/graph_edge（异步，T2/T4 共用入口，默认 FULL 模式）。
     * 通过 Spring {@code @Async} 走默认 taskExecutor；铁律 §2.5 #3 不允许自建 ScheduledExecutorService。
     */
    @Override
    @Async
    public void runKgMapper(String objectType, String jobId) {
        runSync(objectType, jobId, MODE_FULL, false);
    }

    /**
     * 模式化映射入口（PMO-B3-2 T3）：{@code mode} 决定实例抽取是否按水位线增量。
     */
    @Override
    @Async
    public void runKgMapper(String objectType, String jobId, String mode) {
        runSync(objectType, jobId, normalizeMode(mode), false);
    }

    /**
     * 模式化 + dry-run 映射入口（PMO-B3-2 T4）。
     */
    @Override
    @Async
    public void runKgMapper(String objectType, String jobId, String mode, boolean dryRun) {
        runSync(objectType, jobId, normalizeMode(mode), dryRun);
    }

    /**
     * 同步执行体：骨架/版本对齐（B1）→ 契约驱动实例抽取（B3-2）→ 报告回填 {@code kg_sync_log}。
     *
     * @param objectType 本体 ID 或 {@code ALL}
     * @param jobId      任务 ID
     * @param mode       {@code FULL} / {@code INCREMENTAL}
     * @param dryRun     {@code true} = 预览（不写图谱/骨架，仅统计并回填报告）
     */
    private void runSync(String objectType, String jobId, String mode, boolean dryRun) {
        try {
            // 1. 骨架/版本对齐（B1，保留；只做 ontology_version 溯源列对齐，不物化类型节点）；dry-run 跳过写操作
            Map<String, Object> align = dryRun ? null : kgMapper.syncFromOntology(objectType, jobId);
            // 2. 实例抽取（B3-2：映射契约驱动，DW 层只读 → graph_node/graph_edge）
            EntityInstanceExtractionReportVO report =
                    instanceExtraction.extract(objectType, jobId, MODE_INCREMENTAL.equals(mode), dryRun);
            int nodes = report.getNodeCreated() + report.getNodeUpdated();
            int edges = report.getEdgeCreated();
            // dry-run 不产生真实写入，kg_sync_log 计数保持 0，预测值在 report JSON 内
            markLogSuccessWithReport(jobId, dryRun ? 0 : nodes, dryRun ? 0 : edges, toJson(report));
            log.info("KG sync done: object={}, mode={}, dryRun={}, alignedNodes={}, alignedEdges={}, "
                            + "nodesCreated={}, nodesUpdated={}, edges={}, skipped={}, invalidMappings={}, jobId={}",
                    objectType, mode, dryRun, align == null ? 0 : align.get("nodes"), align == null ? 0 : align.get("edges"),
                    report.getNodeCreated(), report.getNodeUpdated(), edges,
                    report.getNodeSkipped(), report.getInvalidMappings(), jobId);
        } catch (Exception e) {
            markLogFailed(jobId, e.getMessage());
            log.warn("AUDIT kg_sync_failed jobId={} object={}", jobId, objectType);
            log.error("KG sync failed: object={}, jobId={}, error={}", objectType, jobId, e.getMessage(), e);
        }
    }

    /** 模式归一化：非 {@code INCREMENTAL} 一律按 {@code FULL}（默认全量，语义不变）。 */
    private String normalizeMode(String mode) {
        return MODE_INCREMENTAL.equalsIgnoreCase(mode == null ? "" : mode.trim()) ? MODE_INCREMENTAL : MODE_FULL;
    }

    /** 抽取报告 → JSON 字符串（序列化失败返回 null，不阻断台账标记）。 */
    private String toJson(EntityInstanceExtractionReportVO report) {
        try {
            return MAPPER.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("抽取报告序列化失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getSyncLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try {
            List<Map<String, Object>> rows = kgSyncLogDao.getSyncLogs(safeLimit);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("syncId", row.getOrDefault("jobId", ""));
                entry.put("objectType", row.get("objectType"));
                entry.put("operation", row.getOrDefault("operation", ""));
                entry.put("status", coerceToUiStatus(row.get("status")));
                entry.put("timestamp", formatTs(row.get("timestamp")));
                entry.put("message", row.getOrDefault("message", ""));
                entry.put("jobId", row.getOrDefault("jobId", ""));
                entry.put("progress", row.getOrDefault("progress", 0));
                entry.put("nodes", row.getOrDefault("nodes", 0));
                entry.put("edges", row.getOrDefault("edges", 0));
                entry.put("finishedAt", formatTs(row.get("finishedAt")));
                out.add(entry);
            }
            return out;
        } catch (Exception e) {
            log.warn("getSyncLogs failed (table missing?): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 拉单个 job 维度的同步日志（PMO-50 T4 GraphSyncController /jobs/{jobId}/logs 复用）。
     */
    @Override
    public List<Map<String, Object>> getJobLogs(String jobId, int limit) {
        if (jobId == null || jobId.isBlank()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try {
            return kgSyncLogDao.getJobLogs(jobId, safeLimit);
        } catch (Exception e) {
            log.warn("getJobLogs failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按 job 维度列出同步任务（取每个 job 最新一行），供 PMO-54 GraphBuilderTab 任务列表消费。
     * 状态映射为前端 {@code GraphBuildJob.status} 枚举（PENDING/RUNNING/SUCCEEDED/FAILED）。
     */
    @Override
    public List<Map<String, Object>> listJobs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try {
            List<Map<String, Object>> rows = kgSyncLogDao.listJobs(safeLimit);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> job = new LinkedHashMap<>();
                job.put("jobId", row.getOrDefault("jobId", ""));
                job.put("type", coerceJobType(row.get("operation")));
                job.put("status", coerceJobStatus(row.get("status")));
                job.put("createdAt", formatTs(row.get("createdAt")));
                job.put("updatedAt", formatTs(row.get("finishedAt")));
                job.put("error", row.getOrDefault("error", ""));
                out.add(job);
            }
            return out;
        } catch (Exception e) {
            log.warn("listJobs failed (table missing?): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** kg_sync_log.status → 前端 GraphBuildJob.status 枚举。 */
    private String coerceJobStatus(Object raw) {
        if (raw == null) {
            return "PENDING";
        }
        switch (String.valueOf(raw).toUpperCase(Locale.ROOT)) {
            case STATUS_RUNNING: return "RUNNING";
            case STATUS_SUCCESS: return "SUCCEEDED";
            case STATUS_FAILED: return "FAILED";
            case "ROLLED_BACK": return "ROLLED_BACK";
            default: return "PENDING";
        }
    }

    /** kg_sync_log.op → 前端 GraphBuildJob.type 枚举（FULL/INCREMENTAL/DRY_RUN）。 */
    private String coerceJobType(Object raw) {
        String op = raw == null ? "" : String.valueOf(raw).toUpperCase(Locale.ROOT);
        if (op.contains("FULL") || op.contains("NEO4J")) {
            return "FULL";
        }
        if (op.contains("DRY")) {
            return "DRY_RUN";
        }
        return "INCREMENTAL";
    }

    // ── 私有辅助 ──────────────────────────────────────

    private void insertLog(String objectType, String op, String jobId, String status,
                           int progress, int nodes, int edges, String errorMessage) {
        kgSyncLogDao.insertLog(objectType, op, jobId, status, progress, nodes, edges, errorMessage);
    }

    private void markLogSuccess(String jobId, int nodes, int edges) {
        try {
            kgSyncLogDao.markLogSuccess(jobId, nodes, edges);
        } catch (Exception e) {
            log.warn("markLogSuccess failed: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    /**
     * 标记成功并回填实例抽取结构化报告（PMO-B3-2 T2）。
     *
     * <p>{@code report} 列（V136）缺失时回退 {@link #markLogSuccess}，
     * 保证台账状态/计数仍正确落地（报告为增量元数据，尽力而为）。
     */
    private void markLogSuccessWithReport(String jobId, int nodes, int edges, String reportJson) {
        if (reportJson == null) {
            markLogSuccess(jobId, nodes, edges);
            return;
        }
        try {
            kgSyncLogDao.markLogSuccessWithReport(jobId, nodes, edges, reportJson);
        } catch (Exception e) {
            log.warn("回填抽取报告失败（回退仅计数标记）: jobId={}, err={}", jobId, e.getMessage());
            markLogSuccess(jobId, nodes, edges);
        }
    }

    private void markLogFailed(String jobId, String err) {
        try {
            kgSyncLogDao.markLogFailed(jobId, err);
        } catch (Exception e) {
            log.warn("markLogFailed failed: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    private String coerceToUiStatus(Object raw) {
        if (raw == null) {
            return "pending";
        }
        switch (String.valueOf(raw).toUpperCase(Locale.ROOT)) {
            case STATUS_PENDING: return "pending";
            case STATUS_RUNNING: return "running";
            case STATUS_SUCCESS: return "success";
            case STATUS_FAILED: return "failed";
            default: return "pending";
        }
    }

    private String formatTs(Object v) {
        return v == null ? null : v.toString();
    }

    /** F04-15 真实审计发布器（可空；缺席时本地结构化 log 兜底，语义同 KnowledgeNavSecurityEngineClient 先例）。 */
    @Autowired(required = false)
    private KbAuditEventPublisher kbAuditEventPublisher;

    /**
     * 审计事件（F04-15 收口，2026-10-12 复盘订正）：经 {@link KbAuditEventPublisher} 走 EventBus
     * 发 Kafka {@code ecos.audit}（复刻 {@code KnowledgeNavSecurityEngineClient#audit} 先例，
     * eventId/timestamp/userId/tenantId 由发布器统一携带，替代原 payload.ts）；
     * 发布器未装配 → 本地 log 兜底；发布失败（ECOS-KB-022）→ 按先例调用方口径降级 log.error，
     * 不阻塞主链、绝不静默吞。
     */
    private void emitAudit(String action, String detail) {
        if (kbAuditEventPublisher == null) {
            log.warn("[AUDIT-LOCAL-FALLBACK] topic={} action={} detail={} ts={}",
                    KafkaTopics.AUDIT, action, sanitize(detail), nowIso());
            return;
        }
        try {
            kbAuditEventPublisher.audit(action, sanitize(detail), "kg-sync");
        } catch (KbErrorCodes.DomainException ex) {
            log.error("审计发布失败（写已落库，审计证据见本地兜底 log）: action={} {}",
                    action, ex.getMessage());
        }
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\\", "/");
    }
}
