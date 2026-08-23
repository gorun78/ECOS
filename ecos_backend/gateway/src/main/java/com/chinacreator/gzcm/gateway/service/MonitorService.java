package com.chinacreator.gzcm.gateway.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 系统监控服务 — 从 MonitorController 下沉的 JdbcTemplate 访问层。
 * SQL 语义与原 Controller 保持一致。
 */
@Service
public class MonitorService {

    private final JdbcTemplate jdbc;

    public MonitorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 活跃告警数（status=OPEN） */
    public Integer countActiveAlerts() {
        return jdbc.queryForObject(
            "SELECT count(*) FROM ecos_alert_history WHERE status = 'OPEN'", Integer.class);
    }

    /** 今日告警总数 */
    public Integer countAlertsToday() {
        return jdbc.queryForObject(
            "SELECT count(*) FROM ecos_alert_history WHERE created_at >= CURRENT_DATE", Integer.class);
    }

    /** 最近 10 条告警 */
    public List<Map<String, Object>> queryRecentAlerts() {
        return jdbc.query(
            "SELECT id, rule_name, level, message, status, created_at " +
            "FROM ecos_alert_history ORDER BY created_at DESC LIMIT 10",
            (rs, _i) -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("rule_name", rs.getString("rule_name"));
                m.put("level", rs.getString("level"));
                m.put("message", rs.getString("message"));
                m.put("status", rs.getString("status"));
                Timestamp ts = rs.getTimestamp("created_at");
                m.put("created_at", ts != null ? ts.toLocalDateTime().toString() : null);
                return m;
            });
    }

    /** 打开的 DQ 问题数 */
    public Integer countOpenDqIssues() {
        return jdbc.queryForObject(
            "SELECT count(*) FROM ecos_dq_issue WHERE status = 'OPEN'", Integer.class);
    }

    /** 数据库健康探测：SELECT 1 */
    public boolean isDbUp() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 今日告警按级别统计 */
    public List<Map<String, Object>> queryAlertsTodayByLevel() {
        return jdbc.query(
            "SELECT level, count(*) as cnt FROM ecos_alert_history WHERE created_at >= CURRENT_DATE " +
            "GROUP BY level ORDER BY cnt DESC",
            (rs, _i) -> Map.of("level", (Object) rs.getString("level"), "count", rs.getLong("cnt")));
    }

    /** 告警总数 */
    public Integer countTotalAlerts() {
        return jdbc.queryForObject("SELECT count(*) FROM ecos_alert_history", Integer.class);
    }
}
