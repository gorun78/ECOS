package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.runtime.access.connector.JdbcConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Statement;
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
    private final JdbcConnector jdbcConnector;

    /** 外部数据源 JDBC 连接串前缀（仅 PG 支持 ESTIMATE） */
    private static final String PG_URL = "jdbc:postgresql";

    /** EXACT 方式下单表超时（秒）；大表 EXACT 会卡，调用方可在策略上改用 ESTIMATE */
    private static final int EXACT_STATEMENT_TIMEOUT_S = 30;

    /** {@link JdbcConnector#queryScalarLong} 的"无行"哨兵值（估算 SQL 带 COALESCE，值域不可能取到它） */
    private static final long NO_ROW_SENTINEL = Long.MIN_VALUE;

    @Autowired
    public MetadataRowCountService(JdbcTemplate jdbc, JdbcConnector jdbcConnector) {
        this.jdbc = jdbc;
        this.jdbcConnector = jdbcConnector;
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
        String[] parts = splitName(tableName);
        String sql = "SELECT COUNT(*) FROM "
                + (parts.length == 2
                        ? "\"" + safeIdent(parts[0]) + "\".\"" + safeIdent(parts[1]) + "\""
                        : "\"" + safeIdent(tableName) + "\"");
        // H2-T6：建连经 runtime-access JdbcConnector；语句超时与取值留在引擎侧
        try (Connection conn = jdbcConnector.openConnection(connectionConfig);
             Statement stmt = conn.createStatement()) {
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
        String[] parts = splitName(tableName);
        String schema = parts.length == 2 ? parts[0] : "public";
        String table = parts.length == 2 ? parts[1] : parts[0];

        String countSql;
        if (isPostgresUrl(connectionConfig)) {
            countSql = "SELECT COALESCE(SUM(c.n_live_tup), 0) FROM pg_stat_user_tables c "
                    + "WHERE c.schemaname = ? AND c.relname = ?";
        } else {
            countSql = "SELECT COALESCE(table_rows, -1) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?";
        }
        try {
            // H2-T6：参数化标量查询经 JdbcConnector.queryScalarLong（PreparedStatement 绑定，不拼串）。
            // 两条估算 SQL 均带 COALESCE，取值不可能为 SQL NULL，故 sentinel 仅可能命中"无行"分支；
            // 无行时与下沉前一致回退 EXACT（视图等 information_schema 查不到的对象）。
            long v = jdbcConnector.queryScalarLong(connectionConfig, countSql,
                    List.of(schema, table), NO_ROW_SENTINEL);
            if (v != NO_ROW_SENTINEL) {
                return v;
            }
        } catch (Exception e) {
            log.debug("ESTIMATE 回退 EXACT（{}）: {}", table, e.getMessage());
        }
        // 回退精确统计（视图等 pg_stat 查不到的对象）
        return countExact(connectionConfig, tableName);
    }

    /** 方言分支判断只需 jdbcUrl 前缀；配置解析统一走 {@link JdbcConnector#readConnectionConfig}（铁律 #4）。 */
    private boolean isPostgresUrl(String connectionConfig) {
        String url = jdbcConnector.readConnectionConfig(connectionConfig).get("jdbcUrl");
        return url != null && url.startsWith(PG_URL);
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

    /**
     * 查采集结果 JSON 原文（最近 N 条，仅取带 result 的行）— 供 {@code /collect-diff} 端点消费。
     * <p>
     * PMO-74 H9-T4：原内联在 {@code MetadataController.collectDiff} 的 SQL 下沉至此。
     * 返回列名（{@code result} / {@code created_at} / {@code task_id}）保持 snake_case，
     * 与下沉前 {@code queryForList} 的结果键完全一致（调用方按原键取值）。
     *
     * @param datasourceId 数据源 ID
     * @param limit        条数（下限 1，与下沉前 {@code Math.max(1, limit)} 口径一致）
     * @return 结果行列表（{@code created_at} 倒序）
     */
    public List<java.util.Map<String, Object>> recentCollectResults(String datasourceId, int limit) {
        return jdbc.queryForList(
            "SELECT result, created_at, task_id FROM td_metadata_collect_log " +
            "WHERE datasource_id = ? AND result IS NOT NULL ORDER BY created_at DESC LIMIT ?",
            datasourceId, Math.max(1, limit));
    }

    /**
     * 查指定时刻之前最近一条带 {@code gitCommit} 的采集 result JSON 原文 —
     * 供 {@code /version-diff} 端点补充提交说明。
     * <p>
     * PMO-74 H9-T4：原内联在 {@code MetadataController.enrichCommitMessage} 的 SQL 下沉至此。
     * 无命中行或该行 result 为 NULL 时返回 {@code null}；result 列取值仍按原实现做
     * {@code (String)} 强转（异常语义与下沉前一致，由调用方 catch 降级）。
     *
     * @param datasourceId     数据源 ID
     * @param collectedBefore  采集时刻上界（含）
     * @return result JSON 原文；无记录返回 {@code null}
     */
    public String latestCollectResultJsonWithCommit(String datasourceId, Timestamp collectedBefore) {
        List<java.util.Map<String, Object>> logs = jdbc.queryForList(
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
