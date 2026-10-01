package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code MonitorService} 内联的
 * {@code ecos_alert_history} / {@code ecos_dq_issue} 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Service 层逐字节一致；
 * 行 → Map 映射随 SQL 一并落点。</p>
 */
@Repository
public class MonitorDao extends JdbcTemplateDaoSupport {

    /** 活跃告警数（status=OPEN） */
    public Integer countActiveAlerts() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ecos_alert_history WHERE status = 'OPEN'", Integer.class);
    }

    /** 今日告警总数 */
    public Integer countAlertsToday() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ecos_alert_history WHERE created_at >= CURRENT_DATE", Integer.class);
    }

    /** 最近 10 条告警 */
    public List<Map<String, Object>> queryRecentAlerts() {
        return jdbcTemplate.query(
            "SELECT id, rule_name, level, message, status, created_at " +
            "FROM ecos_alert_history ORDER BY created_at DESC LIMIT 10",
            (rs, _i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
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
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ecos_dq_issue WHERE status = 'OPEN'", Integer.class);
    }

    /** 数据库健康探测：SELECT 1 */
    public boolean isDbUp() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 今日告警按级别统计 */
    public List<Map<String, Object>> queryAlertsTodayByLevel() {
        return jdbcTemplate.query(
            "SELECT level, count(*) as cnt FROM ecos_alert_history WHERE created_at >= CURRENT_DATE " +
            "GROUP BY level ORDER BY cnt DESC",
            (rs, _i) -> Map.of("level", (Object) rs.getString("level"), "count", rs.getLong("cnt")));
    }

    /** 告警总数 */
    public Integer countTotalAlerts() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM ecos_alert_history", Integer.class);
    }
}
