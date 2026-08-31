package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * PMO-37 行数统计与审计落库服务。
 * <p>
 * 三态行数语义：-1=未采集 / 0=空表 / >=0=真实或估算值。
 * 全部走 JdbcTemplate 走系统 PG 连接（写 td_data_resource / td_datasource / td_metadata_collect_log），
 * 行数 SQL 走外部数据源 JDBC 连接（由 MetadataCollectTaskExecutor 传入 connectionConfig）。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class MetadataRowCountService {

    private static final Logger log = LoggerFactory.getLogger(MetadataRowCountService.class);

    private final JdbcTemplate jdbc;

    /** 外部数据源 JDBC 连接串前缀（仅 PG 支持 ESTIMATE） */
    private static final String PG_URL = "jdbc:postgresql";

    /** EXACT 方式下单表超时（秒）；大表 EXACT 会卡，调用方可在策略上改用 ESTIMATE */
    private static final int EXACT_STATEMENT_TIMEOUT_S = 30;

    public MetadataRowCountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 统计外部数据源单表行数。
     *
     * @param connectionConfig 外部数据源连接配置 JSON（jdbcUrl/username/password/schema）
     * @param tableName        带 schema 的表全名（schema.table）
     * @param countMethod      EXACT / ESTIMATE / OFF
     * @return 行数；OFF 返回 null
     */
    public Long countTable(String connectionConfig, String tableName, String countMethod)
            throws Exception {
        String method = countMethod == null ? "ESTIMATE" : countMethod.toUpperCase();
        if ("OFF".equals(method)) {
            return null;
        }
        if ("EXACT".equals(method)) {
            return countExact(connectionConfig, tableName);
        }
        // 默认 ESTIMATE
        return countEstimate(connectionConfig, tableName);
    }

    /** 精确统计：SELECT COUNT(*) FROM "schema"."table"，带 30s 语句超时 */
    private Long countExact(String connectionConfig, String tableName) throws Exception {
        java.util.Map<String, String> cfg = parseConfig(connectionConfig);
        String[] parts = splitName(tableName);
        String sql = "SELECT COUNT(*) FROM "
                + (parts.length == 2
                        ? "\"" + safeIdent(parts[0]) + "\".\"" + safeIdent(parts[1]) + "\""
                        : "\"" + safeIdent(tableName) + "\"");
        try (java.sql.Connection conn = DriverManagerConnection(cfg);
             java.sql.Statement stmt = conn.createStatement()) {
            try {
                stmt.setQueryTimeout(EXACT_STATEMENT_TIMEOUT_S);
            } catch (Exception ignore) { /* 部分驱动不支持 */ }
            try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    /** 估算：pg_stat n_live_tup（PG）/ table_rows（MySQL 兼容尽力而为） */
    private Long countEstimate(String connectionConfig, String tableName) throws Exception {
        java.util.Map<String, String> cfg = parseConfig(connectionConfig);
        String url = cfg.get("jdbcUrl");
        String[] parts = splitName(tableName);
        String schema = parts.length == 2 ? parts[0] : "public";
        String table = parts.length == 2 ? parts[1] : parts[0];

        String countSql;
        if (url != null && url.startsWith(PG_URL)) {
            countSql = "SELECT COALESCE(SUM(c.n_live_tup), 0) FROM pg_stat_user_tables c "
                    + "WHERE c.schemaname = ? AND c.relname = ?";
        } else {
            countSql = "SELECT COALESCE(table_rows, -1) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?";
        }
        try (java.sql.Connection conn = DriverManagerConnection(cfg);
             java.sql.PreparedStatement ps = conn.prepareStatement(countSql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? -1L : v;
                }
            }
        } catch (Exception e) {
            log.debug("ESTIMATE 回退 EXACT（{}）: {}", table, e.getMessage());
        }
        // 回退精确统计（视图等 pg_stat 查不到的对象）
        return countExact(connectionConfig, tableName);
    }

    /** 外部数据源连接建连（直建同 JdbcConnector） */
    private static java.sql.Connection DriverManagerConnection(java.util.Map<String, String> cfg)
            throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(
                cfg.get("jdbcUrl"), cfg.get("username"), cfg.get("password"));
    }

    private static String[] splitName(String qualified) {
        int idx = qualified.lastIndexOf('.');
        if (idx < 0) {
            return new String[]{qualified};
        }
        String schema = qualified.substring(0, idx);
        String table = qualified.substring(idx + 1);
        if (schema.isEmpty() || table.isEmpty()) {
            return new String[]{qualified};
        }
        return new String[]{schema, table};
    }

    private static String safeIdent(String ident) {
        // 标识符防御：剔除所有双引号，防注入（表名来自元数据发现，非用户输入，但仍兜底）
        return ident == null ? "" : ident.replace("\"", "");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, String> parseConfig(String connectionConfig) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(connectionConfig, java.util.Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid connection config JSON", e);
        }
    }

    /** 审计落库 */
    public void auditLog(String datasourceId, String countMethod, int total, int ok, int failed,
                          String failedTables, String status, String detail, String taskId,
                          long elapsedMs) {
        jdbc.update(
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
        jdbc.update("UPDATE td_datasource SET last_collect_time = NOW() WHERE datasource_id = ?",
                datasourceId);
    }

    /** 查某数据源最近一次采集时间 */
    public Timestamp getLastCollectTime(String datasourceId) {
        List<Timestamp> list = jdbc.query(
            "SELECT last_collect_time FROM td_datasource WHERE datasource_id = ?",
            (rs, i) -> rs.getTimestamp(1), datasourceId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 查采集审计日志（最近 N 条） */
    public List<java.util.Map<String, Object>> recentLogs(String datasourceId, int limit) {
        return jdbc.queryForList(
            "SELECT id, datasource_id, count_method, tables_total, tables_ok, tables_failed, " +
            "status, task_id, elapsed_ms, create_time FROM td_metadata_collect_log " +
            "WHERE datasource_id = ? ORDER BY create_time DESC LIMIT ?",
            datasourceId, Math.min(limit, 50));
    }
}
