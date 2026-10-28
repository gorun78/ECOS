package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <p>V200/R3-B1: schema 由 `${kb.control-schema:ecos_knowledge}` 注入 (MC06 全面落地)。</p>
 *
 * <p><b>#146h D-7 收口（2026-10-27）</b>：由裸表 {@code kg_sync_log}（V115，dev PG
 * 库面不存在）切至 V179 合规新表 {@code ecos_knowledge.ecos_kg_sync_log}。
 * 新表列名已语义化：{@code op→sync_op / nodes→node_count / edges→edge_count /
 * created_at→create_time / finished_at→finished_time / report→report_json(TEXT)}。
 * 本类以 SQL 别名保持既有 map 键面契约（op / nodes / edges / created_at / finished_at /
 * finishedAt / timestamp）不变，消费方（KgSyncServiceImpl / KgMapperService 等）零改动。
 * 写腿按 V179 新增 {@code id}（VARCHAR(36) NOT NULL，无默认，应用侧 UUID 生成，MC01）
 * 与 {@code create_time}（默认 CURRENT_TIMESTAMP 但显式 NOW() 保持语义）。
 * 裸名 V115 判 knownLegacy 停再跑（见其头注注记）。</p>
 */
@Repository
public class KgSyncLogDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public KgSyncLogDao(
            @Value("${kb.control-schema:ecos_knowledge}") String schema) {
        this.schema = schema;
    }

    /** 按 domain 聚合的同步状态（lastSyncTime 取 MAX(finished_time) where status=SUCCESS）。 */
    public List<Map<String, Object>> syncStatusByDomain() {
        return jdbcTemplate.queryForList(
                    "SELECT n.domain AS \"type\", " +
                    "       COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(l.finished_time) FROM " + schema + ".ecos_kg_sync_log l " +
                    "        WHERE l.object_type = n.domain AND l.status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM " + schema + ".graph_node n GROUP BY n.domain");
    }

    /** 全量统计行（type=ALL）。 */
    public List<Map<String, Object>> syncStatusOverall() {
        return jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(finished_time) FROM " + schema + ".ecos_kg_sync_log " +
                    "        WHERE status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM " + schema + ".graph_node");
    }

    public Integer countSyncLog() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".ecos_kg_sync_log", Integer.class);
    }

    public List<Map<String, Object>> getSyncLogs(int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT id, object_type AS \"objectType\", sync_op AS \"operation\", " +
                    "       job_id AS \"jobId\", status, progress, node_count AS \"nodes\", edge_count AS \"edges\", " +
                    "       error_message AS \"message\", create_time AS \"timestamp\", finished_time AS \"finishedAt\" " +
                    "FROM " + schema + ".ecos_kg_sync_log ORDER BY create_time DESC LIMIT ?",
                    safeLimit);
    }

    public List<Map<String, Object>> getJobLogs(String jobId, int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT \"id\", \"object_type\" AS \"objectType\", \"sync_op\" AS \"operation\", " +
                    "\"job_id\" AS \"jobId\", \"status\", \"progress\", \"node_count\" AS \"nodes\", \"edge_count\" AS \"edges\", " +
                    "\"error_message\" AS \"message\", \"create_time\" AS \"timestamp\", \"finished_time\" AS \"finishedAt\" " +
                    "FROM " + schema + ".ecos_kg_sync_log WHERE job_id = ? ORDER BY create_time DESC LIMIT ?",
                    jobId, safeLimit);
    }

    public List<Map<String, Object>> listJobs(int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT t.job_id AS \"jobId\", t.sync_op AS \"operation\", t.status, " +
                    "       t.error_message AS \"error\", t.create_time AS \"createdAt\", t.finished_time AS \"finishedAt\" " +
                    "FROM (SELECT DISTINCT ON (job_id) job_id, sync_op, status, error_message, create_time, finished_time " +
                    "      FROM " + schema + ".ecos_kg_sync_log ORDER BY job_id, create_time DESC) t " +
                    "ORDER BY t.create_time DESC LIMIT ?",
                    safeLimit);
    }

    /**
     * 分页读取同步日志（附录 A 第 15 条 graph/sync/logs）。
     *
     * <p>与 {@link #getSyncLogs(int)} 的差别：带 {@code trace_id} 投影（契约要求条目含 traceId）、
     * 带 OFFSET 分页，且<b>不吞异常</b>——读故障原样上浮给 kb 局部 advice（500 {@code ECOS-KB-050}），
     * 禁以空列表把「表坏了」伪装成「没日志」。</p>
     */
    public List<Map<String, Object>> getSyncLogsPage(int offset, int limit) {
        return jdbcTemplate.queryForList(
                    "SELECT id, object_type AS \"objectType\", sync_op AS \"operation\", " +
                    "       job_id AS \"jobId\", status, progress, node_count AS \"nodes\", edge_count AS \"edges\", " +
                    "       error_message AS \"message\", create_time AS \"timestamp\", finished_time AS \"finishedAt\", " +
                    "       trace_id AS \"traceId\" " +
                    "FROM " + schema + ".ecos_kg_sync_log ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                    limit, offset);
    }

    /**
     * 最近一次成功同步完成时刻（{@code MAX(finished_time) WHERE status='SUCCESS'}）。
     *
     * <p>聚合恒返回一行，无成功行时该行取值为 {@code null}（与「读不到」区分：后者抛
     * DataAccessException 上浮 500）。</p>
     */
    public Timestamp lastSuccessTime() {
        List<Timestamp> rows = jdbcTemplate.queryForList(
                    "SELECT MAX(finished_time) AS \"lastSuccess\" FROM " + schema + ".ecos_kg_sync_log " +
                    "WHERE status = 'SUCCESS'",
                    Timestamp.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 写 sync log 首条。V179 合规形态：{@code id} 应用侧 UUID、{@code create_time}
     * 默认 CURRENT_TIMESTAMP 显式补，{@code node_count/edge_count} 语义比裸表
     * {@code nodes/edges} 更精确（V115 未实跑，本方法一直是唯一活的旧裸表写点）。
     */
    public void insertLog(String objectType, String op, String jobId, String status,
                          int progress, int nodes, int edges, String errorMessage) {
        insertLog(objectType, op, jobId, status, progress, nodes, edges, errorMessage, null);
    }

    /**
     * 写 sync log 首条并携带全链 {@code trace_id}（V179 列，附录 A 第 15 条日志条目的 traceId 来源）。
     */
    public void insertLog(String objectType, String op, String jobId, String status,
                          int progress, int nodes, int edges, String errorMessage, String traceId) {
        jdbcTemplate.update(
                "INSERT INTO " + schema + ".ecos_kg_sync_log " +
                "(id, object_type, sync_op, job_id, status, progress, node_count, edge_count, " +
                " error_message, trace_id, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString(),
                objectType, op, jobId, status, progress, nodes, edges,
                errorMessage == null ? null : truncate(errorMessage, 200),
                traceId == null ? null : truncate(traceId, 64));
    }

    public void markLogSuccess(String jobId, int nodes, int edges) {
        jdbcTemplate.update(
                    "UPDATE " + schema + ".ecos_kg_sync_log " +
                    "SET status = 'SUCCESS', progress = 100, node_count = ?, edge_count = ?, finished_time = NOW() " +
                    "WHERE job_id = ?",
                    nodes, edges, jobId);
    }

    /**
     * 写报告 SQL：V179 新表 {@code report_json} 类型 = TEXT（DR04），
     * <b>禁 ::jsonb 强转</b>（旧腿原实装错，本 DAO 一并修正）。
     */
    public void markLogSuccessWithReport(String jobId, int nodes, int edges, String reportJson) {
        jdbcTemplate.update(
                    "UPDATE " + schema + ".ecos_kg_sync_log " +
                    "SET status = 'SUCCESS', progress = 100, node_count = ?, edge_count = ?, " +
                    "    report_json = ?, finished_time = NOW() " +
                    "WHERE job_id = ?",
                    nodes, edges, reportJson, jobId);
    }

    public void markLogFailed(String jobId, String err) {
        jdbcTemplate.update(
                    "UPDATE " + schema + ".ecos_kg_sync_log " +
                    "SET status = 'FAILED', progress = 0, error_message = ?, finished_time = NOW() " +
                    "WHERE job_id = ? AND status IN ('RUNNING','PENDING')",
                    err == null ? null : truncate(err, 200), jobId);
    }

    /**
     * 截断到 V179 列宽 {@code VARCHAR(200)}（旧腿写点按 V115 {@code VARCHAR(1024)}，
     * 新表已收敛 summary 档，本方法在本批下调至 200）。
     */
    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
