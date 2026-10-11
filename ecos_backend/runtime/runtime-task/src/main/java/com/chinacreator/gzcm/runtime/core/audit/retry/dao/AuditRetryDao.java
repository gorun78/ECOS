package com.chinacreator.gzcm.runtime.core.audit.retry.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 C.5.4 (F00-12) — 审计兜底重试表持久化收口
 * （R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>控制域 {@code ecos_control.ecos_runtime_audit_retry}（DDL=V244）全部 raw-SQL 自
 * {@code com.chinacreator.gzcm.runtime.core.audit.retry.AuditRetryTask} 逐字节下沉，
 * SQL 文本与参数顺序不变；{@code AuditRetryTask} 退化为重放/退避/告警编排门面，
 * SQL 只经本 DAO（{@link JdbcTemplateDaoSupport} 的 {@code jdbcTemplate} 通道）执行。</p>
 *
 * <p>仅在有 {@link JdbcTemplate} 的容器内生效；无数据源栈则本 Bean 不装配。</p>
 *
 * <p>红线 EN01/IR01：全部 slot 参数化。</p>
 */
@Repository
public class AuditRetryDao extends JdbcTemplateDaoSupport {

    private static final String T = "ecos_control.ecos_runtime_audit_retry";
    private static final String V = "1.0.0";

    /** 生产侧 fallback 落库：Kafka 写失败时调用（业务写已成功，仅审计打点失败）。返回应用侧 UUID。 */
    public String insertRetry(String id, String eventType, String payload, String traceId) {
        jdbcTemplate.update(
            "INSERT INTO " + T +
            " (id, event_type, payload, attempts, next_retry_at, status, trace_id, version_no, is_deleted, domain)" +
            " VALUES (?, ?, ?, 0, NOW(), 'pending', ?, ?, 0, 'default')",
            id, eventType, payload, traceId, V);
        return id;
    }

    public List<String> queryDueIds(java.sql.Timestamp now, int maxAttempts, int batch) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM " + T +
            " WHERE is_deleted = 0 AND status = 'pending' AND attempts < " + maxAttempts +
            "   AND next_retry_at <= ? ORDER BY next_retry_at ASC LIMIT " + batch,
            String.class, now);
    }

    public Map<String, Object> findRow(String id) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT id, event_type AS eventType, payload, attempts FROM " + T +
                " WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean markClosed(String id) {
        return jdbcTemplate.update("UPDATE " + T +
                " SET status = 'closed', update_time = NOW() WHERE id = ?", id) > 0;
    }

    /** 失败一次 → attempts+1，指数退避：1min, 2min, 4min（attempts 1→2min, 2→4min, 3→exhausted）。 */
    public boolean bumpAttempt(String id, String delayMinutes) {
        return jdbcTemplate.update("UPDATE " + T +
                " SET attempts = attempts + 1, last_error = 'replay-failed'," +
                " next_retry_at = NOW() + (?)::int * interval '1 minute'," +
                " update_time = NOW() WHERE id = ?",
                delayMinutes, id) > 0;
    }

    public void markExhausted(String id) {
        jdbcTemplate.update("UPDATE " + T +
                " SET status = 'exhausted', update_time = NOW() WHERE id = ?", id);
    }
}
