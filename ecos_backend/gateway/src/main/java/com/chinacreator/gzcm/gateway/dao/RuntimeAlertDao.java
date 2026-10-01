package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code RuntimeAlertRepository} 内联的
 * {@code ecos_runtime_alert_record}（V242）与 {@code ecos_runtime_event_dlq}（V243）
 * 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Service 层逐字节一致。</p>
 */
@Repository
public class RuntimeAlertDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listAlerts(String severity, String status, int size, int offset) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE status = ? AND is_deleted = 0");
        args.add(status);
        if (severity != null && !severity.isBlank()) { where.append(" AND severity = ?"); args.add(severity); }
        args.add(size);
        args.add(offset);
        return jdbcTemplate.queryForList(
                "SELECT id, rule_code, severity, status, title, source_module, trace_id, ack_by, " +
                "ack_time, close_time, occurred_at, tenant_id FROM ecos_runtime_alert_record" +
                where + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                args.toArray(new Object[0]));
    }

    public int countAlerts(String severity, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE status = ? AND is_deleted = 0");
        args.add(status);
        if (severity != null && !severity.isBlank()) { where.append(" AND severity = ?"); args.add(severity); }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_runtime_alert_record" + where, Integer.class,
                args.toArray(new Object[0]));
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> findAlertById(String id) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_runtime_alert_record WHERE id = ? AND is_deleted = 0", id);
    }

    public int ackAlert(String id, String by) {
        return jdbcTemplate.update(
                "UPDATE ecos_runtime_alert_record SET status='acked', ack_by=?, ack_time=NOW(), " +
                "update_time=NOW() WHERE id = ? AND status = 'open'", by, id);
    }

    public List<Map<String, Object>> listDlq(String status, int size, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT id, topic, dlt_topic, error_message, attempts, first_seen_at, " +
                "replayed_at, status FROM ecos_runtime_event_dlq WHERE status = ? AND is_deleted = 0 " +
                "ORDER BY first_seen_at DESC LIMIT ? OFFSET ?",
                status, size, offset);
    }

    public int countDlq(String status) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_runtime_event_dlq WHERE status = ? AND is_deleted = 0",
                Integer.class, status);
        return n == null ? 0 : n;
    }

    public int replayDlq(String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_runtime_event_dlq SET status='replayed', replayed_at=NOW(), " +
                "attempts=attempts+1, update_time=NOW() WHERE id = ? AND status = 'pending'", id);
    }
}
