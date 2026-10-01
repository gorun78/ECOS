package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * IntegrationMetadataService 的 JDBC 数据访问层。
 * <p>td_datasource / td_audit_log / ecos_audit_log / ecos_object_timeline / ecos_data.dq_rule_check 查询走本 DAO。</p>
 */
@Repository
public class IntegrationMetadataDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listActiveConnections() {
        return jdbcTemplate.queryForList(
            "SELECT datasource_id, datasource_name, datasource_type, status " +
            "FROM td_datasource WHERE status = 'ACTIVE' ORDER BY datasource_name");
    }

    public List<Map<String, Object>> tdAuditLog(int hours, int maxLimit) {
        return jdbcTemplate.queryForList(
            "SELECT event_type, timestamp, resource, action, result, details " +
            "FROM td_audit_log " +
            "WHERE timestamp >= NOW() - make_interval(hours => ?) " +
            "ORDER BY timestamp DESC LIMIT ?", hours, maxLimit);
    }

    public List<Map<String, Object>> ecosAuditLog(int hours, int maxLimit) {
        return jdbcTemplate.queryForList(
            "SELECT operation, entity_type, entity_id, changes, created_at, category " +
            "FROM ecos_audit_log " +
            "WHERE created_at >= NOW() - make_interval(hours => ?) " +
            "ORDER BY created_at DESC LIMIT ?", hours, maxLimit);
    }

    public List<Map<String, Object>> ecosObjectTimeline(int hours, int maxLimit) {
        return jdbcTemplate.queryForList(
            "SELECT event_type, event_summary, actor, details, created_at " +
            "FROM ecos_object_timeline " +
            "WHERE created_at >= NOW() - make_interval(hours => ?) " +
            "ORDER BY created_at DESC LIMIT ?", hours, maxLimit);
    }

    public List<Map<String, Object>> recentDqRuleChecks() {
        return jdbcTemplate.queryForList(
            "SELECT rc.rule_id, rc.passed, rc.total_rows, rc.failed_rows, rc.pass_rate, rc.executed_at " +
            "FROM ecos_data.dq_rule_check rc " +
            "WHERE rc.executed_at >= NOW() - interval '1 hour' " +
            "ORDER BY rc.executed_at DESC LIMIT 20");
    }
}
