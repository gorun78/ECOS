package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：Git 归档补偿队列 {@code {schema}.ecos_ontology_git_archive_pending}（V170.2）
 * 的 JDBC 落点。
 *
 * <p>SQL 全量搬运自 {@code GitArchivePendingRepository}（W83/C66 · F03-15）：
 * 显式列清单、schema 限定、参数化、no {@code ::}（MC03）、no {@code @Scheduled}（铁律 §2.5-4）。
 *
 * <p>两个构造（同 {@link FunctionAuditDao} 同名双构造模式）：</p>
 * <ul>
 *   <li>Spring 主注入：{@code (String schema)}；</li>
 *   <li>手动构造（测试）：{@code (JdbcTemplate jdbc, String schema)}。</li>
 * </ul>
 *
 * <p><b>显式 bean name</b>：{@code @Repository("engineGitArchivePendingDao")}。
 * 与 {@code com.chinacreator.gzcm.runtime.core.ontology.archive.dao.GitArchivePendingDao}
 * （bean 名 {@code gitArchivePendingDao}，W83/C66 F03-15 收口）默认同 simple-name 冲突。
 * gateway 单 fat-JAR 内的 engine + runtime 都命中 ComponentScan，若不显式命名会触发
 * {@code ConflictingBeanDefinitionException}（2026-10-02 Gate C 实测）。
 */
@Repository("engineGitArchivePendingDao")
public class GitArchivePendingDao extends JdbcTemplateDaoSupport {

    private final String table;

    @org.springframework.beans.factory.annotation.Autowired
    public GitArchivePendingDao(
            @Value("${ecos.ontology.control-schema:ecos_ontology}") String pendingSchema) {
        this.table = pendingSchema + ".ecos_ontology_git_archive_pending";
    }

    public GitArchivePendingDao(JdbcTemplate jdbc, String pendingSchema) {
        this.jdbcTemplate = jdbc;
        this.table = pendingSchema + ".ecos_ontology_git_archive_pending";
    }

    private final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rn) -> {
        Map<String, Object> m = new LinkedHashMap<>();
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
        return jdbcTemplate.query(
            "SELECT id, ontology_id, version_id, target_version_no, repository_id, commit_message,"
                + " author_name, payload, attempts, next_retry_at, last_error, status, last_commit_id,"
                + " trace_id, create_time, update_time FROM " + table
                + " WHERE is_deleted = 0 AND status = 'pending' AND attempts < 3 AND next_retry_at <= ?"
                + " ORDER BY next_retry_at ASC LIMIT ?",
            ROW_MAPPER, now, limit);
    }

    public Optional<Map<String, Object>> findById(String id) {
        List<Map<String, Object>> l = jdbcTemplate.query(
            "SELECT id, ontology_id, version_id, target_version_no, repository_id, commit_message,"
                + " author_name, payload, attempts, next_retry_at, last_error, status, last_commit_id,"
                + " trace_id, create_time, update_time FROM " + table
                + " WHERE id = ? AND is_deleted = 0",
            ROW_MAPPER, id);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /** MC01：id 由应用侧 {@link UUID#randomUUID()} 生成。DR07：版本行固定 1.0.0。 */
    public String enqueue(String ontologyId, String versionId, String targetVersionNo,
                          String repositoryId, String commitMessage, String authorName,
                          String payload, String traceId) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
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

    /** attempts+1、指数退避 1min&lt;&lt;attempts。返回 false 表示 attempts 已>=3，应 markExhausted。 */
    public boolean bumpAttempt(String id, long delayMinutes) {
        int r = jdbcTemplate.update(
            "UPDATE " + table
                + " SET attempts = attempts + 1, last_error = 'git-archive-replay-failed',"
                + "     next_retry_at = NOW() + INTERVAL '1 minute' * (" + Math.max(1, (int) delayMinutes) + "),"
                + "     update_time = NOW() WHERE id = ? AND is_deleted = 0",
            id);
        return r > 0;
    }

    public int markClosed(String id, String commitId) {
        return jdbcTemplate.update(
            "UPDATE " + table
                + " SET status = 'closed', last_commit_id = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            commitId, id);
    }

    public int markExhausted(String id, String lastError) {
        String trimmed = lastError == null ? "exhausted" : (lastError.length() > 1000 ? lastError.substring(0, 1000) : lastError);
        return jdbcTemplate.update(
            "UPDATE " + table
                + " SET status = 'exhausted', last_error = ?, update_time = NOW() WHERE id = ? AND is_deleted = 0",
            trimmed, id);
    }
}
