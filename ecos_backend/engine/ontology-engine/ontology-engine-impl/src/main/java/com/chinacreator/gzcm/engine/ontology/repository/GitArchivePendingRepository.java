package com.chinacreator.gzcm.engine.ontology.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 本体版本 Git 归档补偿队列（W83/C66 / F03-15）—
 * 生产侧（本体引擎）只做「未 COMMITTED → 入队」；消费侧在 runtime/runtime-task。
 *
 * <p>表 {@code ecos_ontology.ecos_ontology_git_archive_pending}（V170.2）。
 * 本体控制面 schema（与 V244 {@code ecos_runtime_audit_retry} 同族，同由 runtime-task 统一驱动）。
 *
 * <p>红线：本仓库不做 {@code @Scheduled} 重试（铁律 §2.5-4）；
 * 唯一动作是 enqueue 一次、查自己列入队计数、markClosed/markExhausted 三方法，
 * 全部经 {@link JdbcTemplate} 参数化。
 *
 * <p>SQL 全部 schema 限定（DR01）；显式列清单，禁 {@code SELECT *}（IR04）。
 */
@Repository
public class GitArchivePendingRepository {

    private final JdbcTemplate jdbc;
    private final String table;

    public GitArchivePendingRepository(JdbcTemplate jdbc,
                                       @Value("${ecos.ontology.control-schema:ecos_ontology}") String pendingSchema) {
        this.jdbc = jdbc;
        this.table = pendingSchema + ".ecos_ontology_git_archive_pending";
    }

    private final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rn) -> {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("ontologyId", rs.getString("ontology_id"));
        m.put("versionId", rs.getString("version_id"));
        m.put("targetVersionNo", rs.getString("target_version_no"));
        m.put("repositoryId", rs.getString("repository_id"));
        m.put("commitMessage", rs.getString("commit_message"));
        m.put("authorName", rs.getString("author_name"));
        m.put("payload", rs.getString("payload"));
        m.put("attempts", rs.getObject("attempts"));
        m.put("nextRetryAt", rs.getTimestamp("next_retry_at"));
        m.put("lastError", rs.getString("last_error"));
        m.put("status", rs.getString("status"));
        m.put("lastCommitId", rs.getString("last_commit_id"));
        m.put("traceId", rs.getString("trace_id"));
        m.put("createTime", rs.getTimestamp("create_time"));
        m.put("updateTime", rs.getTimestamp("update_time"));
        return m;
    };

    public List<Map<String, Object>> listPendingDue(Timestamp now, int limit) {
        return jdbc.query(
            "SELECT id, ontology_id, version_id, target_version_no, repository_id, commit_message,"
                + " author_name, payload, attempts, next_retry_at, last_error, status, last_commit_id,"
                + " trace_id, create_time, update_time FROM " + table
                + " WHERE is_deleted = 0 AND status = 'pending' AND attempts < 3 AND next_retry_at <= ?"
                + " ORDER BY next_retry_at ASC LIMIT ?",
            ROW_MAPPER, now, limit);
    }

    public Optional<Map<String, Object>> findById(String id) {
        List<Map<String, Object>> l = jdbc.query(
            "SELECT id, ontology_id, version_id, target_version_no, repository_id, commit_message,"
                + " author_name, payload, attempts, next_retry_at, last_error, status, last_commit_id,"
                + " trace_id, create_time, update_time FROM " + table
                + " WHERE id = ? AND is_deleted = 0",
            ROW_MAPPER, id);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /**
     * 发布成功但 Git 归档非 COMMITTED 时入队。
     *
     * <p>MC01：id 由应用侧 {@link UUID#randomUUID()} 生成。
     * DR07：版本行固定 1.0.0；本行不升。
     */
    public String enqueue(String ontologyId, String versionId, String targetVersionNo,
                          String repositoryId, String commitMessage, String authorName,
                          String payload, String traceId) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO " + table
                + " (id, ontology_id, version_id, target_version_no, repository_id, commit_message,"
                + "  author_name, payload, attempts, next_retry_at, status, trace_id,"
                + "  create_time, update_time, create_by, update_by, is_deleted, version_no, domain)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), 'pending', ?, NOW(), NOW(), 'system', 'system', 0, '1.0.0', 'default')",
            id,
            ontologyId == null ? "" : ontologyId,
            versionId == null ? "" : versionId,
            targetVersionNo == null ? "" : targetVersionNo,
            repositoryId == null ? "ontology" : repositoryId,
            commitMessage == null ? "" : commitMessage,
            authorName == null ? "system" : authorName,
            payload == null ? "" : payload,
            traceId == null ? "" : traceId);
        return id;
    }

    /** attempts+1、指数退避 1min<<attempts。返回 false 表示 attempts 已>=3，应 markExhausted。 */
    public boolean bumpAttempt(String id, long delayMinutes) {
        int r = jdbc.update(
            "UPDATE " + table
                + " SET attempts = attempts + 1, last_error = 'git-archive-replay-failed',"
                + "     next_retry_at = NOW() + INTERVAL '1 minute' * (" + Math.max(1, (int) delayMinutes) + "),"
                + "     update_time = NOW() WHERE id = ? AND is_deleted = 0",
            id);
        return r > 0;
    }

    public int markClosed(String id, String commitId) {
        return jdbc.update(
            "UPDATE " + table
                + " SET status = 'closed', last_commit_id = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            commitId, id);
    }

    public int markExhausted(String id, String lastError) {
        String trimmed = lastError == null ? "exhausted" : (lastError.length() > 1000 ? lastError.substring(0, 1000) : lastError);
        return jdbc.update(
            "UPDATE " + table
                + " SET status = 'exhausted', last_error = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            trimmed, id);
    }
}
