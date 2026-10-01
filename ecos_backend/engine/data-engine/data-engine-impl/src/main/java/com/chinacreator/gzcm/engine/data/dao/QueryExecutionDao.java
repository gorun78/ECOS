package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data-access for the ad-hoc query execution domain:
 * template CRUD (ecOS_query_template) and query history (ecOS_query_history).
 */
@Repository
public class QueryExecutionDao extends JdbcTemplateDaoSupport {

    // --- ecos_query_template ---

    public int countTemplateById(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_query_template WHERE id = ?", Integer.class, id);
    }

    public int updateTemplate(String name, String description, String datasourceId,
                              String sqlContent, String paramsJson,
                              int timeoutSeconds, int maxRows, String id) {
        return jdbcTemplate.update(
                "UPDATE ecos_query_template SET name=?, description=?, datasource_id=?, " +
                "sql_content=?, params_json=?::jsonb, timeout_seconds=?, max_rows=?, updated_at=NOW() " +
                "WHERE id=?",
                name, description, datasourceId, sqlContent, paramsJson,
                timeoutSeconds, maxRows, id);
    }

    public int insertTemplate(String id, String name, String description, String datasourceId,
                              String sqlContent, String paramsJson,
                              int timeoutSeconds, int maxRows, String createdBy) {
        return jdbcTemplate.update(
                "INSERT INTO ecos_query_template (id, name, description, datasource_id, sql_content, " +
                "params_json, timeout_seconds, max_rows, created_by) " +
                "VALUES (?,?,?,?,?,?::jsonb,?,?,?)",
                id, name, description, datasourceId, sqlContent,
                paramsJson, timeoutSeconds, maxRows, createdBy);
    }

    public int countTemplates(String datasourceId) {
        String countSql = "SELECT COUNT(*) FROM ecos_query_template";
        List<Object> params = new ArrayList<>();
        if (datasourceId != null && !datasourceId.isBlank()) {
            countSql += " WHERE datasource_id = ?";
            params.add(datasourceId);
        }
        return jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
    }

    public List<Map<String, Object>> listTemplateRows(String datasourceId, int pageSize, int offset) {
        String dataSql = "SELECT * FROM ecos_query_template";
        List<Object> params = new ArrayList<>();
        if (datasourceId != null && !datasourceId.isBlank()) {
            dataSql += " WHERE datasource_id = ?";
            params.add(datasourceId);
        }
        dataSql += " ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.queryForList(dataSql, params.toArray());
    }

    public List<Map<String, Object>> getTemplateById(String id) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_query_template WHERE id = ?", id);
    }

    public int deleteTemplate(String id) {
        return jdbcTemplate.update("DELETE FROM ecos_query_template WHERE id = ?", id);
    }

    // --- ecos_query_history ---

    public int countQueryHistory() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_query_history", Integer.class);
    }

    public List<Map<String, Object>> listQueryHistory(int pageSize, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM ecos_query_history ORDER BY started_at DESC LIMIT ? OFFSET ?",
                pageSize, offset);
    }

    public int cancelQuery(String historyId) {
        return jdbcTemplate.update(
                "UPDATE ecos_query_history SET status='CANCELLED', finished_at=NOW() " +
                "WHERE id=? AND status='RUNNING'", historyId);
    }

    public int countQueryHistoryById(String historyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_query_history WHERE id=?", Integer.class, historyId);
    }

    public void insertHistoryStart(String id, String datasourceId, String sql) {
        jdbcTemplate.update(
                "INSERT INTO ecos_query_history (id, datasource_id, sql_content, status) " +
                "VALUES (?, ?, ?, 'RUNNING')",
                id, datasourceId, sql);
    }

    public void updateHistorySuccess(String id, int rowsReturned, int elapsedMs) {
        jdbcTemplate.update(
                "UPDATE ecos_query_history SET status='SUCCESS', rows_returned=?, " +
                "elapsed_ms=?, finished_at=NOW() WHERE id=?",
                rowsReturned, elapsedMs, id);
    }

    public void updateHistoryError(String id, String errorMsg, int elapsedMs) {
        jdbcTemplate.update(
                "UPDATE ecos_query_history SET status='ERROR', error_msg=?, " +
                "elapsed_ms=?, finished_at=NOW() WHERE id=?",
                errorMsg, elapsedMs, id);
    }
}
