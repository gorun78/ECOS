package com.chinacreator.gzcm.datanet.service;

import java.sql.*;
import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.service.IAnalyticsService;

/**
 * Doris 分析引擎服务
 * 通过 MySQL 协议连接 Apache Doris FE，执行分析查询。
 *
 * 连接方式: JDBC (mysql-connector-j) → Doris FE :9030
 *
 * @author ECOS Sprint 5.3
 */
@Service
@Profile("flagship")
public class DorisAnalyticsService implements IAnalyticsService {

    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:9030/ecos_olap";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DorisAnalyticsService() {
        this(DEFAULT_JDBC_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    public DorisAnalyticsService(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String analyticsSql) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(analyticsSql);
             ResultSet rs = stmt.executeQuery()) {

            return buildResultList(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Doris SQL execution failed", e);
        }
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analyticsProvider", "Doris");
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            boolean healthy = rs.next() && rs.getInt(1) == 1;
            result.put("status", healthy ? "UP" : "DOWN");
            result.put("jdbcUrl", jdbcUrl);
        } catch (SQLException e) {
            result.put("status", "DOWN");
            result.put("jdbcUrl", jdbcUrl);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ─── 连接管理 ────────────────────────────────────────

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found", e);
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    // ─── 结果集转换 ─────────────────────────────────────

    private List<Map<String, Object>> buildResultList(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
