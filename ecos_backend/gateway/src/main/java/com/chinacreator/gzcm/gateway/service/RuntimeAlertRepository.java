package com.chinacreator.gzcm.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 §1.6.4 铁律：Controller 不直接访问 JdbcTemplate，走 Service 层。
 * 该 Service 承担 F00-09 告警中心与 DLQ 重放的持久化（表由 V242/V243 建立）。
 */
@Service
public class RuntimeAlertRepository {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertRepository.class);
    private final JdbcTemplate jdbc;

    public RuntimeAlertRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> listAlerts(String severity, String status, int size, int offset) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE status = ? AND is_deleted = 0");
        args.add(status);
        if (severity != null && !severity.isBlank()) { where.append(" AND severity = ?"); args.add(severity); }
        args.add(size);
        args.add(offset);
        StringBuilder sb = new StringBuilder();
        try {
            return jdbc.queryForList(
                    "SELECT id, rule_code, severity, status, title, source_module, trace_id, ack_by, " +
                    "ack_time, close_time, occurred_at, tenant_id FROM ecos_runtime_alert_record" +
                    where + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                    args.toArray(new Object[0]));
        } catch (Exception ex) {
            log.error("listAlerts: {}", ex.getMessage()); throw ex;
        }
    }

    public int countAlerts(String severity, String status) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE status = ? AND is_deleted = 0");
        args.add(status);
        if (severity != null && !severity.isBlank()) { where.append(" AND severity = ?"); args.add(severity); }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_runtime_alert_record" + where, Integer.class,
                args.toArray(new Object[0]));
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> findAlertById(String id) {
        try {
            return jdbc.queryForList(
                    "SELECT * FROM ecos_runtime_alert_record WHERE id = ? AND is_deleted = 0", id);
        } catch (Exception ex) {
            log.error("findAlertById: {}", ex.getMessage()); throw ex;
        }
    }

    public int ackAlert(String id, String by) {
        try {
            return jdbc.update(
                    "UPDATE ecos_runtime_alert_record SET status='acked', ack_by=?, ack_time=NOW(), " +
                    "update_time=NOW() WHERE id = ? AND status = 'open'", by, id);
        } catch (Exception ex) {
            log.error("ackAlert: {}", ex.getMessage()); throw ex;
        }
    }

    public List<Map<String, Object>> listDlq(String status, int size, int offset) {
        try {
            return jdbc.queryForList(
                    "SELECT id, topic, dlt_topic, error_message, attempts, first_seen_at, " +
                    "replayed_at, status FROM ecos_runtime_event_dlq WHERE status = ? AND is_deleted = 0 " +
                    "ORDER BY first_seen_at DESC LIMIT ? OFFSET ?",
                    status, size, offset);
        } catch (Exception ex) {
            log.error("listDlq: {}", ex.getMessage()); throw ex;
        }
    }

    public int countDlq(String status) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_runtime_event_dlq WHERE status = ? AND is_deleted = 0",
                Integer.class, status);
        return n == null ? 0 : n;
    }

    public int replayDlq(String id) {
        try {
            return jdbc.update(
                    "UPDATE ecos_runtime_event_dlq SET status='replayed', replayed_at=NOW(), " +
                    "attempts=attempts+1, update_time=NOW() WHERE id = ? AND status = 'pending'", id);
        } catch (Exception ex) {
            log.error("replayDlq: {}", ex.getMessage()); throw ex;
        }
    }
}
