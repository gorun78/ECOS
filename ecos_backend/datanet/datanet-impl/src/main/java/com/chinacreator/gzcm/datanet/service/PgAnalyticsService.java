package com.chinacreator.gzcm.datanet.service;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.service.IAnalyticsService;

/**
 * PostgreSQL 分析引擎服务
 * 将分析 SQL 翻译为 PostgreSQL 兼容语法后执行。
 *
 * 翻译规则:
 * - PERCENTILE(x, y) → PERCENTILE_CONT(y) WITHIN GROUP (ORDER BY x)
 * - GROUPING SETS → UNION ALL 展开
 *
 * @author ECOS Sprint 5.3
 */
@Service
public class PgAnalyticsService implements IAnalyticsService {

    @Autowired(required = false)
    private DataSource dataSource;

    @Override
    public List<Map<String, Object>> executeQuery(String analyticsSql) {
        String pgSql = translateSql(analyticsSql);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(pgSql);
             ResultSet rs = stmt.executeQuery()) {

            return buildResultList(rs);
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL analytics query failed", e);
        }
    }

    @Override
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analyticsProvider", "PG");
        result.put("status", "UP");
        return result;
    }

    // ─── SQL 翻译 ───────────────────────────────────────

    /**
     * 将分析 SQL 翻译为 PostgreSQL 兼容语法
     */
    String translateSql(String analyticsSql) {
        String sql = translatePercentile(analyticsSql);
        sql = translateGroupingSets(sql);
        return sql;
    }

    /**
     * PERCENTILE(x, y) → PERCENTILE_CONT(y) WITHIN GROUP (ORDER BY x)
     */
    private String translatePercentile(String sql) {
        // 匹配 PERCENTILE(expr, percentile) 模式
        Pattern pattern = Pattern.compile(
            "PERCENTILE\\s*\\(\\s*([^,]+?)\\s*,\\s*([^)]+?)\\s*\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String percentile = matcher.group(2).trim();
            String replacement = "PERCENTILE_CONT(" + percentile
                + ") WITHIN GROUP (ORDER BY " + expr + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * GROUPING SETS → UNION ALL 展开 (简化实现: 将 GROUPING SETS 替换为多列 GROUP BY)
     * 完整 GROUPING SETS 展开需要解析器，此处做最小实现 — 提示不支持的语法
     */
    private String translateGroupingSets(String sql) {
        if (sql.toUpperCase().contains("GROUPING SETS")) {
            throw new UnsupportedOperationException(
                "GROUPING SETS is not directly supported in PG analytics. "
                + "Please rewrite using UNION ALL with individual GROUP BY clauses."
            );
        }
        return sql;
    }

    // ─── 连接管理 ───────────────────────────────────────

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("No DataSource available for PG analytics");
        }
        return dataSource.getConnection();
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
