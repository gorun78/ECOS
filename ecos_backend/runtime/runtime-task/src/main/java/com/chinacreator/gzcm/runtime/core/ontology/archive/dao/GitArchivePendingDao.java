package com.chinacreator.gzcm.runtime.core.ontology.archive.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * F03-15 (W83/C66) — 本体版本 × Git 归档补偿队列持久化收口
 * （R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>{@code ecos_ontology.ecos_ontology_git_archive_pending} 全部 raw-SQL 自
 * {@code com.chinacreator.gzcm.runtime.core.ontology.archive.GitArchiveRetryTask}
 * 逐字节下沉，SQL 文本与参数顺序不变；该任务退化为重放/退避/告警编排门面，
 * SQL 只经本 DAO（{@link JdbcTemplateDaoSupport} 的 {@code jdbcTemplate} 通道）执行。</p>
 *
 * <p>仅在有 {@link JdbcTemplate} 的容器内生效；无数据源栈则本 Bean 不装配。</p>
 *
 * <p>红线 EN01/IR01：全部 slot 参数化（delayMinutes 为退避常数拼接，非用户输入）。</p>
 */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class GitArchivePendingDao extends JdbcTemplateDaoSupport {

    private static final String T = "ecos_ontology.ecos_ontology_git_archive_pending";

    public List<String> queryDueIds(java.sql.Timestamp now, int maxAttempts, int batch) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM " + T
                + " WHERE is_deleted = 0 AND status = 'pending' AND attempts < " + maxAttempts
                + "   AND next_retry_at <= ? ORDER BY next_retry_at ASC LIMIT " + batch,
            String.class, now);
    }

    public Map<String, Object> findRow(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, ontology_id AS ontologyId, version_id AS versionId,"
                    + " target_version_no AS targetVersionNo, repository_id AS repositoryId,"
                    + " commit_message AS commitMessage, author_name AS authorName,"
                    + " payload, attempts FROM " + T
                    + " WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            return null;
        }
    }

    public void markClosed(String id, String commitId) {
        jdbcTemplate.update("UPDATE " + T
                + " SET status = 'closed', last_commit_id = ?, update_time = NOW() WHERE id = ?",
            commitId, id);
    }

    public void bumpAttempt(String id, String reason, long delayMinutes) {
        jdbcTemplate.update("UPDATE " + T
                + " SET attempts = attempts + 1, last_error = ?,"
                + "   next_retry_at = NOW() + INTERVAL '1 minute' * " + delayMinutes + ","
                + "   update_time = NOW() WHERE id = ?",
            reason, id);
    }

    public void markExhausted(String id, String reason) {
        jdbcTemplate.update("UPDATE " + T
                + " SET status = 'exhausted', last_error = ?, update_time = NOW() WHERE id = ?",
            reason, id);
    }
}
