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
import java.util.Properties;
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
        Object s = testConnectionDetailed(connectionConfig, "JDBC").get("success");
        return Boolean.TRUE.equals(s);
    }

    /**
     * PMO-46 Wave2 — 详细连通性测试：返回 {success, error, type, driverClass, ...}。
     * <p>
     * 与 {@link #testConnection(String)} 的行为差异：
     * <ol>
     *   <li>按数据源 type 解析 JDBC URL + 驱动类（而非仅依赖 config.jdbcUrl）；</li>
     *   <li>先 {@code Class.forName(driverClass)} —— 驱动不在 classpath 时返回
     *       “驱动未加载”，避免裸报 SQLException / 500；</li>
     *   <li>连接超时 connectTimeout/socketTimeout = 10s；</li>
     *   <li>SQLException 按 网络不可达 / 认证失败 / 驱动无适配 分类，输出可读 error。</li>
     * </ol>
     * 供 {@code DataSourceServiceImpl.testConnectionById} 返回给
     * {@code POST /api/v1/datasource/test-connection/{id}}，满足验收：
     * 能清楚显示哪种连接没成功，而不是超时或异常裸报 500。
     *
     * @param connectionConfig 连接配置 JSON
     * @param dsType           数据源业务类型（ORACLE/MSSQL/DM/KINGBASE/GAUSS/MYSQL/POSTGRESQL/...）
     * @return 连通性明细（never null；success=false 时 error 非空）
     */
    public Map<String, Object> testConnectionDetailed(String connectionConfig, String dsType) {
        String type = dsType != null ? dsType.toUpperCase() : "JDBC";
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("type", type);
        try {
            Map<String, String> cfg = parseConfig(connectionConfig);
            String url = buildJdbcUrl(type, cfg);
            String driver = resolveDriverClass(type, url);
            r.put("driverClass", driver);
            if (url == null || url.isBlank()) {
                r.put("success", false);
                String err = "连接配置缺少 jdbcUrl 或 host/port 参数，无法拼装 JDBC URL (type=" + type + ")";
                r.put("error", err);
                r.put("message", err);
                return r;
            }
            // 1) 驱动加载检测 — 缺失时给出可读提示，而非 SQLException 裸报
            try {
                Class.forName(driver);
                r.put("driverLoaded", true);
            } catch (ClassNotFoundException cnfe) {
                r.put("driverLoaded", false);
                String err = "JDBC 驱动未加载: " + driver + " (type=" + type + ")。"
                    + "当前部署包不含该驱动 JAR —— 请使用 -Pjdbc-drivers profile 重构建，"
                    + "或确认企业版/旗舰版运行时已附带对应驱动。";
                r.put("success", false);
                r.put("error", err);
                r.put("message", err);
                return r;
            }
            // 2) 建连（10s 超时）
            Properties props = new Properties();
            if (cfg.get("username") != null) props.setProperty("user", cfg.get("username"));
            if (cfg.get("password") != null) props.setProperty("password", cfg.get("password"));
            props.setProperty("connectTimeout", "10");   // PG: 秒
            props.setProperty("socketTimeout", "10");
            try (Connection conn = DriverManager.getConnection(url, props)) {
                boolean ok;
                try { ok = conn.isValid(5); } catch (Exception ignored) { ok = false; }
                r.put("success", ok);
                if (!ok) {
                    String err = "JDBC 已建连但 isValid(5s)=false (type=" + type + ")";
                    r.put("error", err);
                    r.put("message", err);
                }
            } catch (SQLException e) {
                String err = classifySqlError(type, url, e);
                r.put("success", false);
                r.put("error", err);
                r.put("message", err);
                log.warn("JdbcConnector.testConnectionDetailed FAILED type={} url={}: {}", type, url, e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            String err = "连接配置 JSON 解析失败: " + e.getMessage();
            r.put("success", false);
            r.put("error", err);
            r.put("message", err);
        } catch (Exception e) {
            String err = "连接测试异常 (type=" + type + "): " + e.getMessage();
            r.put("success", false);
            r.put("error", err);
            r.put("message", err);
        }
        return r;
    }

    /**
     * 按数据源类型拼装 JDBC URL。优先使用 config.jdbcUrl；缺失时按 host/port 拼装。
     */
    private String buildJdbcUrl(String type, Map<String, String> cfg) {
        String direct = cfg.get("jdbcUrl");
        if (direct != null && !direct.isBlank()) return direct;
        String host = cfg.get("host");
        String port = cfg.get("port");
        if (host == null || host.isBlank()) return null;
        if (port == null) port = defaultPort(type);
        switch (type) {
            case "ORACLE": {
                String svc = cfg.getOrDefault("serviceName", cfg.getOrDefault("sid", ""));
                return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + svc;
            }
            case "MSSQL": {
                String db = cfg.getOrDefault("databaseName", cfg.getOrDefault("database", ""));
                return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db
                    + ";encrypt=false;trustServerCertificate=true;connectTimeout=10;socketTimeout=10";
            }
            case "DM":
                return "jdbc:dm://" + host + ":" + port + "/" + cfg.getOrDefault("schema", "").replaceFirst("^/", "");
            case "KINGBASE":
                return "jdbc:kingbase8://" + host + ":" + port + "/" + cfg.getOrDefault("database", "");
            case "GAUSS":
                return "jdbc:opengauss://" + host + ":" + port + "/" + cfg.getOrDefault("database", "");
            case "MYSQL":
                return "jdbc:mysql://" + host + ":" + port + "/" + cfg.getOrDefault("database", "")
                    + "?connectTimeout=10000&socketTimeout=10000&useSSL=false";
            case "POSTGRESQL":
            default:
                return "jdbc:postgresql://" + host + ":" + port + "/" + cfg.getOrDefault("database", "")
                    + "?connectTimeout=10&socketTimeout=10";
        }
    }

    private static String defaultPort(String type) {
        switch (type) {
            case "ORACLE": return "1521";
            case "MSSQL": return "1433";
            case "DM": return "5236";
            case "KINGBASE": return "54321";
            case "GAUSS": return "5432";
            case "MYSQL": return "3306";
            case "POSTGRESQL":
            default: return "5432";
        }
    }

    /**
     * 依 URL scheme 或业务类型解析 JDBC 驱动类。
     */
    private String resolveDriverClass(String type, String jdbcUrl) {
        if (jdbcUrl != null) {
            if (jdbcUrl.startsWith("jdbc:oracle")) return "oracle.jdbc.OracleDriver";
            if (jdbcUrl.startsWith("jdbc:sqlserver")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            if (jdbcUrl.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
            if (jdbcUrl.startsWith("jdbc:kingbase8")) return "com.kingbase8.Driver";
            if (jdbcUrl.startsWith("jdbc:opengauss") || jdbcUrl.startsWith("jdbc:gaussdb")) return "org.opengauss.Driver";
            if (jdbcUrl.startsWith("jdbc:postgresql")) return "org.postgresql.Driver";
            if (jdbcUrl.startsWith("jdbc:mysql")) return "com.mysql.cj.jdbc.Driver";
        }
        switch (type) {
            case "ORACLE": return "oracle.jdbc.OracleDriver";
            case "MSSQL": return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "DM": return "dm.jdbc.driver.DmDriver";
            case "KINGBASE": return "com.kingbase8.Driver";
            case "GAUSS": return "org.opengauss.Driver";
            case "MYSQL": return "com.mysql.cj.jdbc.Driver";
            case "POSTGRESQL":
            default: return "org.postgresql.Driver";
        }
    }

    /**
     * 分类 SQLException 为可读 error（网络不可达 / 认证失败 / 驱动无适配 / 其他）。
     */
    private String classifySqlError(String type, String url, SQLException e) {
        String m = e.getMessage() != null ? e.getMessage() : "";
        String combined = (type + " " + m).toLowerCase();
        if (m.contains("No suitable driver")) {
            return "无可用 JDBC 驱动适配 URL: " + url + " (type=" + type + ")";
        }
        if (m.contains("password authentication failed") || m.contains("FATAL:  password")
                || m.contains("Login failed") || m.contains("access denied")
                || m.contains("ORA-01017") || m.contains("ORA-28000")) {
            return "认证失败 (type=" + type + "，账号/密码错误)";
        }
        if (m.contains("Connection refused") || m.contains("ORA-12154") || m.contains("ORA-12170")
                || m.contains("network is unreachable") || m.contains("Connection timed out")
                || m.contains("connect timed out") || m.contains("timed out")
                || m.contains("No route to host") || m.contains("ECONNREFUSED")
                || m.contains("通信链路失败") || m.contains("拒绝")) {
            return "网络不可达/连接被拒绝 (type=" + type + ", " + url + ") — 请检查 host/port/防火墙/目标服务是否运行";
        }
        return "JDBC 连接失败 (type=" + type + "): " + firstLine(m);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        String line = nl > 0 ? s.substring(0, nl) : s;
        if (line.length() > 160) line = line.substring(0, 160) + "…";
        return line.isBlank() ? s : line;
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
            // PMO-49 W4 fix: JSON 数字/布尔端口等值此前按 Integer 装入 Map<String,String>，
            // 后续 cfg.get("port") 触发 ClassCastException 导致 testConnectionDetailed 全量不可用。
            // 统一 coerce 为 String，与 buildJdbcUrl/props.setProperty 的 String 契约一致。
            Map<String, Object> raw = mapper.readValue(connectionConfig, Map.class);
            Map<String, String> r = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                r.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString());
            }
            return r;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid connection config JSON: " + connectionConfig, e);
        }
    }
}
