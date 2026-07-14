package com.chinacreator.gzcm.datanet.connector;

import com.chinacreator.gzcm.datanet.model.DataResource;
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

            // 发现表
            try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    resources.add(buildResource(tables, orgId, orgName, "TABLE", schema));
                }
            }

            // 发现视图
            try (ResultSet views = metaData.getTables(catalog, schema, "%", new String[]{"VIEW"})) {
                while (views.next()) {
                    resources.add(buildResource(views, orgId, orgName, "VIEW", schema));
                }
            }

        } catch (SQLException e) {
            log.error("Failed to list JDBC resources: {}", e.getMessage(), e);
        }

        return resources;
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
