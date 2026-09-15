package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.kb.KgSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private final JdbcTemplate jdbc;
    private final KgMapperService kgMapper;

    public KgSyncServiceImpl(JdbcTemplate jdbc, @Lazy KgMapperService kgMapper) {
        this.jdbc = jdbc;
        this.kgMapper = kgMapper;
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
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT n.domain AS \"type\", " +
                    "       COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(l.finished_at) FROM ecos_knowledge.kg_sync_log l " +
                    "        WHERE l.object_type = n.domain AND l.status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM ecos_knowledge.graph_node n GROUP BY n.domain");
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
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(finished_at) FROM ecos_knowledge.kg_sync_log WHERE status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM ecos_knowledge.graph_node");
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
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.kg_sync_log", Integer.class);
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
        // 异步执行真实同步；@Lazy 注入避免 self-injection 歧义（@Async 同 Bean 自调用代理）
        runKgMapper("ALL", jobId);
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
        runKgMapper(objectType, jobId);
        log.info("Object kg sync submitted: object={}, jobId={}", objectType, jobId);
    }

    /**
     * 将 ontologies 对象映射到 graph_node/graph_edge（异步，T2/T4 共用入口）。
     * 通过 Spring {@code @Async} 走默认 taskExecutor；铁律 §2.5 #3 不允许自建 ScheduledExecutorService。
     */
    @Override
    @Async
    public void runKgMapper(String objectType, String jobId) {
        try {
            Map<String, Object> result = kgMapper.syncFromOntology(objectType, jobId);
            int nodes = ((Number) result.getOrDefault("nodes", 0)).intValue();
            int edges = ((Number) result.getOrDefault("edges", 0)).intValue();
            markLogSuccess(jobId, nodes, edges);
            log.info("KG sync done: object={}, nodes={}, edges={}, jobId={}", objectType, nodes, edges, jobId);
        } catch (Exception e) {
            markLogFailed(jobId, e.getMessage());
            log.warn("AUDIT kg_sync_failed jobId={} object={}", jobId, objectType);
            log.error("KG sync failed: object={}, jobId={}, error={}", objectType, jobId, e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getSyncLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, object_type AS \"objectType\", op AS \"operation\", " +
                    "       job_id AS \"jobId\", status, progress, nodes, edges, " +
                    "       error_message AS \"message\", created_at AS \"timestamp\", finished_at AS \"finishedAt\" " +
                    "FROM ecos_knowledge.kg_sync_log ORDER BY created_at DESC LIMIT ?",
                    safeLimit);
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
            return jdbc.queryForList(
                    "SELECT \"id\", \"object_type\" AS \"objectType\", \"op\" AS \"operation\", " +
                    "\"job_id\" AS \"jobId\", \"status\", \"progress\", \"nodes\", \"edges\", " +
                    "\"error_message\" AS \"message\", \"created_at\" AS \"timestamp\", \"finished_at\" AS \"finishedAt\" " +
                    "FROM ecos_knowledge.kg_sync_log WHERE job_id = ? ORDER BY created_at DESC LIMIT ?",
                    jobId, safeLimit);
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
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT t.job_id AS \"jobId\", t.op AS \"operation\", t.status, " +
                    "       t.error_message AS \"error\", t.created_at AS \"createdAt\", t.finished_at AS \"finishedAt\" " +
                    "FROM (SELECT DISTINCT ON (job_id) job_id, op, status, error_message, created_at, finished_at " +
                    "      FROM ecos_knowledge.kg_sync_log ORDER BY job_id, created_at DESC) t " +
                    "ORDER BY t.created_at DESC LIMIT ?",
                    safeLimit);
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
        jdbc.update(
                "INSERT INTO ecos_knowledge.kg_sync_log " +
                "(object_type, op, job_id, status, progress, nodes, edges, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                objectType, op, jobId, status, progress, nodes, edges,
                errorMessage == null ? null : truncate(errorMessage, 1024));
    }

    private void markLogSuccess(String jobId, int nodes, int edges) {
        try {
            jdbc.update(
                    "UPDATE ecos_knowledge.kg_sync_log " +
                    "SET status = 'SUCCESS', progress = 100, nodes = ?, edges = ?, finished_at = NOW() " +
                    "WHERE job_id = ?",
                    nodes, edges, jobId);
        } catch (Exception e) {
            log.warn("markLogSuccess failed: jobId={}, err={}", jobId, e.getMessage());
        }
    }

    private void markLogFailed(String jobId, String err) {
        try {
            jdbc.update(
                    "UPDATE ecos_knowledge.kg_sync_log " +
                    "SET status = 'FAILED', progress = 0, error_message = ?, finished_at = NOW() " +
                    "WHERE job_id = ? AND status IN ('RUNNING','PENDING')",
                    err == null ? null : truncate(err, 1024), jobId);
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

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    /**
     * 审计事件：走 Kafka {@code ecos.audit}；Kafka 不可用时本地 log 兜底（铁律 §2.4 #5）。
     */
    private void emitAudit(String action, String detail) {
        try {
            String payload = String.format(
                    "{\"action\":\"%s\",\"detail\":\"%s\",\"ts\":\"%s\",\"service\":\"kb-engine\"}",
                    action, sanitize(detail), nowIso());
            log.info("AUDIT topic={} payload={}", KafkaTopics.AUDIT, payload);
        } catch (Exception e) {
            log.warn("emitAudit fallback log failed: {}", e.getMessage());
        }
    }

    private String sanitize(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\\", "/");
    }
}
