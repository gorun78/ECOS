package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * MetadataRowCountService 的 JDBC 数据访问层（系统 PG 侧审计落库 / 采集时间）。
 * <p>外部数据源行数 SQL 仍走 JdbcConnector，不在本 DAO 范围。</p>
 */
@Repository
public class MetadataRowCountDao extends JdbcTemplateDaoSupport {

    /** 审计落库 */
    public void auditLog(String datasourceId, String countMethod, int total, int ok, int failed,
                         String failedTables, String status, String detail, String taskId,
                         long elapsedMs) {
        jdbcTemplate.update(
            "INSERT INTO td_metadata_collect_log " +
            "(datasource_id, count_method, tables_total, tables_ok, tables_failed, failed_tables, " +
            " status, detail, task_id, elapsed_ms) VALUES (?,?,?,?,?,?,?,?,?,?)",
            datasourceId, countMethod, total, ok, failed,
            failedTables != null && failedTables.length() > 2000 ? failedTables.substring(0, 2000) : failedTables,
            status, detail, taskId, elapsedMs
        );
    }

    /** 更新数据源采集时间 */
    public void updateLastCollectTime(String datasourceId) {
        jdbcTemplate.update("UPDATE td_datasource SET last_collect_time = NOW() WHERE datasource_id = ?",
                datasourceId);
    }

    /** 查某数据源最近一次采集时间 */
    public Timestamp getLastCollectTime(String datasourceId) {
        List<Timestamp> list = jdbcTemplate.query(
            "SELECT last_collect_time FROM td_datasource WHERE datasource_id = ?",
            (rs, i) -> rs.getTimestamp(1), datasourceId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 查采集审计日志（最近 N 条） */
    public List<Map<String, Object>> recentLogs(String datasourceId, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT id, datasource_id, count_method, tables_total, tables_ok, tables_failed, " +
            "status, task_id, elapsed_ms, create_time FROM td_metadata_collect_log " +
            "WHERE datasource_id = ? ORDER BY create_time DESC LIMIT ?",
            datasourceId, Math.min(limit, 50));
    }

    public List<Map<String, Object>> recentCollectResults(String datasourceId, int limit) {
        return jdbcTemplate.queryForList(
            "SELECT result, created_at, task_id FROM td_metadata_collect_log " +
            "WHERE datasource_id = ? AND result IS NOT NULL ORDER BY created_at DESC LIMIT ?",
            datasourceId, Math.max(1, limit));
    }

    public String latestCollectResultJsonWithCommit(String datasourceId, Timestamp collectedBefore) {
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
            "SELECT result FROM td_metadata_collect_log " +
            "WHERE datasource_id = ? AND created_at <= ? AND result LIKE '%gitCommit%' " +
            "ORDER BY created_at DESC LIMIT 1",
            datasourceId, collectedBefore);
        if (logs.isEmpty()) {
            return null;
        }
        return (String) logs.get(0).get("result");
    }
}
