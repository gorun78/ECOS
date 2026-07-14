package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.datanet.service.DataSourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class QueryExecutionServiceImpl implements QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionServiceImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final DataSourceService dataSourceService;

    private final ThreadPoolExecutor queryExecutor = new ThreadPoolExecutor(
            4, 10, 60L, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Map<String, Future<?>> runningQueries = new ConcurrentHashMap<>();

    public QueryExecutionServiceImpl(JdbcTemplate jdbc, DataSourceService dataSourceService) {
        this.jdbc = jdbc;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public Map<String, Object> execute(String datasourceId, String sql,
                                        Map<String, Object> params,
                                        int maxRows, int timeoutSeconds) {
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }

        String historyId = UUID.randomUUID().toString();
        insertHistoryStart(historyId, datasourceId, sql);
        runningQueries.put(historyId, null);

        String resolvedSql = resolveParams(sql, params);
        Map<String, String> connConfig = parseConnectionConfig(ds.getConnectionConfig());

        long startMs = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(
                connConfig.get("jdbcUrl"),
                connConfig.getOrDefault("username", ""),
                connConfig.getOrDefault("password", ""));
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(timeoutSeconds);
            stmt.setMaxRows(maxRows);

            ResultSet rs = stmt.executeQuery(resolvedSql);
            ResultSetMetaData rsmd = rs.getMetaData();
            int colCount = rsmd.getColumnCount();

            List<Map<String, String>> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                Map<String, String> col = new LinkedHashMap<>();
                col.put("name", rsmd.getColumnName(i));
                col.put("label", rsmd.getColumnLabel(i));
                col.put("type", rsmd.getColumnTypeName(i));
                columns.add(col);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(rsmd.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }

            int elapsed = (int) (System.currentTimeMillis() - startMs);
            updateHistorySuccess(historyId, rows.size(), elapsed);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("elapsedMs", elapsed);
            result.put("historyId", historyId);
            return result;

        } catch (SQLException e) {
            int elapsed = (int) (System.currentTimeMillis() - startMs);
            updateHistoryError(historyId, e.getMessage(), elapsed);
            log.error("Query execution failed [{}]: {}", historyId, e.getMessage());
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        } finally {
            runningQueries.remove(historyId);
        }
    }

    @Override
    public Map<String, Object> getSchemaTree(String datasourceId) {
        DataSourceEntity ds = dataSourceService.getById(datasourceId);
        if (ds == null) {
            throw new IllegalArgumentException("数据源不存在: " + datasourceId);
        }

        Map<String, String> connConfig = parseConnectionConfig(ds.getConnectionConfig());

        List<Map<String, Object>> schemas = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                connConfig.get("jdbcUrl"),
                connConfig.getOrDefault("username", ""),
                connConfig.getOrDefault("password", ""))) {

            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = connConfig.getOrDefault("schema", conn.getSchema());

            Map<String, List<Map<String, Object>>> schemaTables = new LinkedHashMap<>();
            try (ResultSet tables = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    String tableSchema = tables.getString("TABLE_SCHEM");
                    if (tableSchema == null) tableSchema = schema;
                    String tableName = tables.getString("TABLE_NAME");
                    String tableType = tables.getString("TABLE_TYPE");

                    Map<String, Object> tableInfo = new LinkedHashMap<>();
                    tableInfo.put("name", tableName);
                    tableInfo.put("type", tableType);
                    tableInfo.put("columns", new ArrayList<Map<String, Object>>());

                    schemaTables.computeIfAbsent(tableSchema, k -> new ArrayList<>()).add(tableInfo);
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : schemaTables.entrySet()) {
                String schemaName = entry.getKey();
                List<Map<String, Object>> tables = entry.getValue();

                for (Map<String, Object> table : tables) {
                    String tableName = (String) table.get("name");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");

                    try (ResultSet cols = meta.getColumns(catalog, schemaName, tableName, "%")) {
                        while (cols.next()) {
                            Map<String, Object> col = new LinkedHashMap<>();
                            col.put("name", cols.getString("COLUMN_NAME"));
                            col.put("type", cols.getString("TYPE_NAME"));
                            col.put("size", cols.getInt("COLUMN_SIZE"));
                            col.put("nullable", cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                            col.put("comment", cols.getString("REMARKS"));
                            columns.add(col);
                        }
                    }
                }

                Map<String, Object> schemaObj = new LinkedHashMap<>();
                schemaObj.put("name", schemaName);
                schemaObj.put("tables", tables);
                schemas.add(schemaObj);
            }

        } catch (SQLException e) {
            log.error("Failed to get schema tree for {}: {}", datasourceId, e.getMessage());
            throw new RuntimeException("获取 Schema 失败: " + e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasourceId", datasourceId);
        result.put("datasourceName", ds.getDatasourceName());
        result.put("datasourceType", ds.getDatasourceType());
        result.put("schemas", schemas);
        return result;
    }

    @Override
    public Map<String, Object> saveTemplate(Map<String, Object> body) {
        String id = body.containsKey("id") ? (String) body.get("id") : UUID.randomUUID().toString();
        String name = (String) body.get("name");
        String description = (String) body.getOrDefault("description", null);
        String datasourceId = (String) body.get("datasource_id");
        String sqlContent = (String) body.get("sql_content");
        String paramsJson = body.containsKey("params_json") ? body.get("params_json").toString() : "{}";
        int timeoutSeconds = body.containsKey("timeout_seconds") ? ((Number) body.get("timeout_seconds")).intValue() : 30;
        int maxRows = body.containsKey("max_rows") ? ((Number) body.get("max_rows")).intValue() : 10000;
        String createdBy = (String) body.getOrDefault("created_by", null);

        if (name == null || datasourceId == null || sqlContent == null) {
            throw new IllegalArgumentException("模板名称、数据源ID和SQL内容不能为空");
        }

        int count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_query_template WHERE id = ?", Integer.class, id);

        if (count > 0) {
            jdbc.update(
                    "UPDATE ecos_query_template SET name=?, description=?, datasource_id=?, " +
                    "sql_content=?, params_json=?::jsonb, timeout_seconds=?, max_rows=?, updated_at=NOW() " +
                    "WHERE id=?",
                    name, description, datasourceId, sqlContent, paramsJson,
                    timeoutSeconds, maxRows, id);
        } else {
            jdbc.update(
                    "INSERT INTO ecos_query_template (id, name, description, datasource_id, sql_content, " +
                    "params_json, timeout_seconds, max_rows, created_by) " +
                    "VALUES (?,?,?,?,?,?::jsonb,?,?,?)",
                    id, name, description, datasourceId, sqlContent,
                    paramsJson, timeoutSeconds, maxRows, createdBy);
        }

        return getTemplate(id);
    }

    @Override
    public Map<String, Object> listTemplates(String datasourceId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;

        String countSql = "SELECT COUNT(*) FROM ecos_query_template";
        String dataSql = "SELECT * FROM ecos_query_template";

        List<Object> params = new ArrayList<>();
        if (datasourceId != null && !datasourceId.isBlank()) {
            String where = " WHERE datasource_id = ?";
            countSql += where;
            dataSql += where;
            params.add(datasourceId);
        }
        dataSql += " ORDER BY updated_at DESC LIMIT ? OFFSET ?";

        int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("items", items);
        return result;
    }

    @Override
    public Map<String, Object> getTemplate(String id) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT * FROM ecos_query_template WHERE id = ?", id);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        return list.get(0);
    }

    @Override
    public void deleteTemplate(String id) {
        int rows = jdbc.update("DELETE FROM ecos_query_template WHERE id = ?", id);
        if (rows == 0) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
    }

    @Override
    public Map<String, Object> getQueryHistory(int page, int pageSize) {
        int offset = (page - 1) * pageSize;

        int total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_query_history", Integer.class);

        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT * FROM ecos_query_history ORDER BY started_at DESC LIMIT ? OFFSET ?",
                pageSize, offset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("items", items);
        return result;
    }

    @Override
    public void cancelQuery(String historyId) {
        int updated = jdbc.update(
                "UPDATE ecos_query_history SET status='CANCELLED', finished_at=NOW() " +
                "WHERE id=? AND status='RUNNING'", historyId);

        if (updated == 0) {
            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_query_history WHERE id=?", Integer.class, historyId);
            if (count == 0) {
                throw new IllegalArgumentException("查询记录不存在: " + historyId);
            }
        }
    }

    private void insertHistoryStart(String id, String datasourceId, String sql) {
        jdbc.update(
                "INSERT INTO ecos_query_history (id, datasource_id, sql_content, status) " +
                "VALUES (?, ?, ?, 'RUNNING')",
                id, datasourceId, sql);
    }

    private void updateHistorySuccess(String id, int rowsReturned, int elapsedMs) {
        jdbc.update(
                "UPDATE ecos_query_history SET status='SUCCESS', rows_returned=?, " +
                "elapsed_ms=?, finished_at=NOW() WHERE id=?",
                rowsReturned, elapsedMs, id);
    }

    private void updateHistoryError(String id, String errorMsg, int elapsedMs) {
        jdbc.update(
                "UPDATE ecos_query_history SET status='ERROR', error_msg=?, " +
                "elapsed_ms=?, finished_at=NOW() WHERE id=?",
                errorMsg, elapsedMs, id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseConnectionConfig(String connectionConfig) {
        try {
            return mapper.readValue(connectionConfig, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("连接配置 JSON 解析失败: " + connectionConfig, e);
        }
    }

    private String resolveParams(String sql, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return sql;
        }
        String resolved = sql;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String replaceValue = formatParamValue(value);
            resolved = resolved.replace(":" + key, replaceValue);
        }
        return resolved;
    }

    private String formatParamValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        String str = value.toString().replace("'", "''");
        return "'" + str + "'";
    }
}
