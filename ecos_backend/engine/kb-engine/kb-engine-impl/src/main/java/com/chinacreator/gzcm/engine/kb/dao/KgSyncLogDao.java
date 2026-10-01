package com.chinacreator.gzcm.engine.kb.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KgSyncLogDao extends JdbcTemplateDaoSupport {

    /** 按 domain 聚合的同步状态（V115 kg_sync_log 提供 lastSyncTime）。 */
    public List<Map<String, Object>> syncStatusByDomain() {
        return jdbcTemplate.queryForList(
                    "SELECT n.domain AS \"type\", " +
                    "       COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(l.finished_at) FROM ecos_knowledge.kg_sync_log l " +
                    "        WHERE l.object_type = n.domain AND l.status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM ecos_knowledge.graph_node n GROUP BY n.domain");
    }

    /** 全量统计行（type=ALL）。 */
    public List<Map<String, Object>> syncStatusOverall() {
        return jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS \"syncedCount\", " +
                    "       (SELECT MAX(finished_at) FROM ecos_knowledge.kg_sync_log WHERE status = 'SUCCESS') AS \"lastSyncTime\" " +
                    "FROM ecos_knowledge.graph_node");
    }

    public Integer countSyncLog() {
        return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ecos_knowledge.kg_sync_log", Integer.class);
    }

    public List<Map<String, Object>> getSyncLogs(int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT id, object_type AS \"objectType\", op AS \"operation\", " +
                    "       job_id AS \"jobId\", status, progress, nodes, edges, " +
                    "       error_message AS \"message\", created_at AS \"timestamp\", finished_at AS \"finishedAt\" " +
                    "FROM ecos_knowledge.kg_sync_log ORDER BY created_at DESC LIMIT ?",
                    safeLimit);
    }

    public List<Map<String, Object>> getJobLogs(String jobId, int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT \"id\", \"object_type\" AS \"objectType\", \"op\" AS \"operation\", " +
                    "\"job_id\" AS \"jobId\", \"status\", \"progress\", \"nodes\", \"edges\", " +
                    "\"error_message\" AS \"message\", \"created_at\" AS \"timestamp\", \"finished_at\" AS \"finishedAt\" " +
                    "FROM ecos_knowledge.kg_sync_log WHERE job_id = ? ORDER BY created_at DESC LIMIT ?",
                    jobId, safeLimit);
    }

    public List<Map<String, Object>> listJobs(int safeLimit) {
        return jdbcTemplate.queryForList(
                    "SELECT t.job_id AS \"jobId\", t.op AS \"operation\", t.status, " +
                    "       t.error_message AS \"error\", t.created_at AS \"createdAt\", t.finished_at AS \"finishedAt\" " +
                    "FROM (SELECT DISTINCT ON (job_id) job_id, op, status, error_message, created_at, finished_at " +
                    "      FROM ecos_knowledge.kg_sync_log ORDER BY job_id, created_at DESC) t " +
                    "ORDER BY t.created_at DESC LIMIT ?",
                    safeLimit);
    }

    public void insertLog(String objectType, String op, String jobId, String status,
                          int progress, int nodes, int edges, String errorMessage) {
        jdbcTemplate.update(
                "INSERT INTO ecos_knowledge.kg_sync_log " +
                "(object_type, op, job_id, status, progress, nodes, edges, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                objectType, op, jobId, status, progress, nodes, edges,
                errorMessage == null ? null : truncate(errorMessage, 1024));
    }

    public void markLogSuccess(String jobId, int nodes, int edges) {
        jdbcTemplate.update(
                    "UPDATE ecos_knowledge.kg_sync_log " +
                    "SET status = 'SUCCESS', progress = 100, nodes = ?, edges = ?, finished_at = NOW() " +
                    "WHERE job_id = ?",
                    nodes, edges, jobId);
    }

    public void markLogSuccessWithReport(String jobId, int nodes, int edges, String reportJson) {
        jdbcTemplate.update(
                    "UPDATE ecos_knowledge.kg_sync_log " +
                    "SET status = 'SUCCESS', progress = 100, nodes = ?, edges = ?, report = ?::jsonb, finished_at = NOW() " +
                    "WHERE job_id = ?",
                    nodes, edges, reportJson, jobId);
    }

    public void markLogFailed(String jobId, String err) {
        jdbcTemplate.update(
                    "UPDATE ecos_knowledge.kg_sync_log " +
                    "SET status = 'FAILED', progress = 0, error_message = ?, finished_at = NOW() " +
                    "WHERE job_id = ? AND status IN ('RUNNING','PENDING')",
                    err == null ? null : truncate(err, 1024), jobId);
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
