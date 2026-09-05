package com.chinacreator.gzcm.runtime.access.connector;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC 连接器 — 通过 JDBC 连接关系型数据库，自动发现表和视图。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class JdbcConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(JdbcConnector.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return "JDBC";
    }

    @Override
    public boolean testConnection(String connectionConfig) {
        try {
            Map<String, String> config = parseConfig(connectionConfig);
            try (Connection conn = DriverManager.getConnection(
                    config.get("jdbcUrl"),
                    config.get("username"),
                    config.get("password"))) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            log.warn("JDBC connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<DataResource> listResources(String connectionConfig, String orgId, String orgName) {
        List<DataResource> resources = new ArrayList<>();
        Map<String, String> config = parseConfig(connectionConfig);

        try (Connection conn = DriverManager.getConnection(
                config.get("jdbcUrl"),
                config.get("username"),
                config.get("password"))) {

            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = config.getOrDefault("schema", conn.getSchema());

            // 优先扫描配置/默认 schema
            collectTablesAndViews(metaData, catalog, schema, orgId, orgName, resources);

            // 兜底：默认 schema 无表时扫描所有非系统 schema（表可能不在 public 下）
            if (resources.isEmpty()) {
                for (String s : listNonSystemSchemas(metaData)) {
                    collectTablesAndViews(metaData, catalog, s, orgId, orgName, resources);
                }
            }

        } catch (SQLException e) {
            // 连接/元数据读取失败必须上抛：调用方（采集任务）需感知失败而非静默返回 0 表
            log.error("Failed to list JDBC resources: {}", e.getMessage(), e);
            throw new RuntimeException("数据源表清单读取失败（请检查 host/端口/库名/账号是否正确）: " + e.getMessage(), e);
        }

        return resources;
    }

    /** 扫描指定 schema 下的表与视图 */
    private void collectTablesAndViews(DatabaseMetaData metaData, String catalog, String schema,
                                       String orgId, String orgName, List<DataResource> out) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                out.add(buildResource(tables, orgId, orgName, "TABLE", schema));
            }
        }
        try (ResultSet views = metaData.getTables(catalog, schema, "%", new String[]{"VIEW"})) {
            while (views.next()) {
                out.add(buildResource(views, orgId, orgName, "VIEW", schema));
            }
        }
    }

    /** 列出所有非系统 schema（排除 pg_catalog / information_schema / pg_toast） */
    private List<String> listNonSystemSchemas(DatabaseMetaData metaData) throws SQLException {
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                String s = rs.getString("TABLE_SCHEM");
                if (s != null && !s.equalsIgnoreCase("pg_catalog")
                        && !s.equalsIgnoreCase("information_schema")
                        && !s.equalsIgnoreCase("pg_toast")) {
                    schemas.add(s);
                }
            }
        }
        return schemas;
    }

    @Override
    public List<Map<String, Object>> queryPreview(String connectionConfig, String tableName, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> config = parseConfig(connectionConfig);
        String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;

        try (Connection conn = DriverManager.getConnection(
                config.get("jdbcUrl"),
                config.get("username"),
                config.get("password"));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int colCount = rsmd.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    row.put(rsmd.getColumnName(i), val != null ? val : "NULL");
                }
                rows.add(row);
            }
            log.info("Previewed {} rows from {} (limit={})", rows.size(), tableName, limit);
        } catch (SQLException e) {
            log.error("Preview query failed for {}: {}", tableName, e.getMessage());
        }
        return rows;
    }

    /**
     * 在外部数据源上执行任意 SQL（SELECT 或 DML），返回结果行。
     * <p>
     * 供 Pipeline SOURCE_JDBC 节点使用 —— 通过 connectionConfig 建立到外部数据源的连接，
     * 执行 config.sql，返回结果行列表（SELECT）或受影响行数（DML 以 rows.size() 体现）。
     * 这是架构规则 2.5 的落地：Pipeline 节点执行外部数据源 SQL 必须走 Connector，禁系统 JdbcTemplate。
     *
     * @param connectionConfig 连接配置 JSON（jdbcUrl/username/password/schema）
     * @param sql              要执行的 SQL 语句
     * @param fetchSize        JDBC fetchSize（控制内存占用，&lt;=0 时使用默认值 1000）
     * @return 结果行列表，每行为 columnName -&gt; value 的 Map
     */
    public List<Map<String, Object>> executeSql(String connectionConfig, String sql, int fetchSize) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> config = parseConfig(connectionConfig);

        try (Connection conn = DriverManager.getConnection(
                config.get("jdbcUrl"),
                config.get("username"),
                config.get("password"));
             Statement stmt = conn.createStatement()) {

            if (fetchSize > 0) {
                stmt.setFetchSize(fetchSize);
            }

            boolean hasResultSet = stmt.execute(sql);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int colCount = rsmd.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(rsmd.getColumnName(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("affectedRows", updateCount);
                rows.add(row);
            }
            log.info("JdbcConnector.executeSql: rows={}, sql length={}", rows.size(), sql.length());
        } catch (SQLException e) {
            log.error("JdbcConnector.executeSql failed: {}", e.getMessage());
            throw new RuntimeException("External datasource SQL execution failed: " + e.getMessage(), e);
        }
        return rows;
    }

    private DataResource buildResource(ResultSet rs, String orgId, String orgName,
                                        String type, String schema) throws SQLException {
        DataResource r = new DataResource();
        r.setResourceId(UUID.randomUUID().toString().replace("-", ""));
        r.setResourceName(rs.getString("TABLE_NAME"));
        r.setResourceType(type);
        r.setOrgId(orgId);
        r.setOrgName(orgName);
        r.setSourcePath(schema + "." + rs.getString("TABLE_NAME"));
        r.setDescription(rs.getString("REMARKS"));
        r.setStatus("ACTIVE");
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseConfig(String connectionConfig) {
        try {
            return mapper.readValue(connectionConfig, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid connection config JSON: " + connectionConfig, e);
        }
    }
}
