package com.chinacreator.gzcm.runtime.core.task.executor;

import java.sql.*;
import java.util.*;

import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus.Status;

/**
 * Doris OLAP 执行器
 * 通过 MySQL 协议连接 Apache Doris FE，执行 SQL 查询并返回结果。
 * 
 * 连接方式: JDBC (mysql-connector-j) → Doris FE :9030
 * 
 * @author ECOS Sprint 5.3
 */
public class DorisRunner implements ITaskExecutor {

    private static final String DEFAULT_JDBC_URL = "jdbc:mysql://localhost:9030/ecos_olap";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DorisRunner() {
        this(DEFAULT_JDBC_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    public DorisRunner(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback callback)
            throws ITaskExecutor.TaskExecutionException {
        String taskId = executionPlan.getTaskId();
        String sql = extractSql(executionPlan);

        long startTime = System.currentTimeMillis();
        try {
            if (callback != null) {
                callback.onProgressUpdate(taskId, 0, "DorisRunner starting SQL execution");
            }

            String result = executeSql(sql);

            long elapsed = System.currentTimeMillis() - startTime;

            if (callback != null) {
                callback.onTaskComplete(taskId, true, result,
                    "DorisRunner completed in " + elapsed + "ms");
            }

            return result;

        } catch (SQLException e) {
            if (callback != null) {
                callback.onError(taskId, e.getMessage(), stackTraceToString(e));
            }
            throw new ITaskExecutor.TaskExecutionException("Doris SQL execution failed for task " + taskId, e);
        }
    }

    @Override
    public void cancel(String taskId) throws TaskExecutionException {
        // 无连接缓存需要清理 — JDBC 连接由 executeSql 的 try-with-resources 管理
    }

    // ─── SQL 执行 ────────────────────────────────────────

    public String executeSql(String sql) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            return buildJsonResult(rs);
        }
    }

    public int executeUpdate(String sql) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    public boolean healthCheck() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            return false;
        }
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

    // ─── SQL 提取 ────────────────────────────────────────

    private String extractSql(TaskExecutionPlan plan) {
        // 从执行计划的 ExecutionStep 列表中提取 SQL
        List<TaskExecutionPlan.ExecutionStep> steps = plan.getSteps();
        if (steps != null && !steps.isEmpty()) {
            for (TaskExecutionPlan.ExecutionStep step : steps) {
                String stepType = step.getStepType();
                if ("DORIS_SQL".equals(stepType) || "SQL_EXECUTE".equals(stepType)
                    || "READ".equals(stepType) || "TRANSFORM".equals(stepType)) {
                    Map<String, Object> config = step.getConfig();
                    if (config != null && config.containsKey("sql")) {
                        return (String) config.get("sql");
                    }
                }
            }
        }
        // 兜底：从 context 中查找 sql
        Map<String, Object> context = plan.getContext();
        if (context != null && context.containsKey("sql")) {
            return (String) context.get("sql");
        }
        throw new IllegalArgumentException("TaskExecutionPlan has no SQL in steps or context");
    }

    // ─── JSON 序列化 ─────────────────────────────────────

    private String buildJsonResult(ResultSet rs) throws SQLException {
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

        StringBuilder sb = new StringBuilder();
        sb.append("{\"columns\":[");
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) sb.append(",");
            sb.append("{\"name\":\"")
              .append(escapeJson(meta.getColumnLabel(i)))
              .append("\",\"type\":\"")
              .append(escapeJson(meta.getColumnTypeName(i)))
              .append("\"}");
        }
        sb.append("],\"rows\":[");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) sb.append(",");
            Map<String, Object> row = rows.get(r);
            sb.append("{");
            int c = 0;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (c++ > 0) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                Object value = entry.getValue();
                if (value == null) {
                    sb.append("null");
                } else if (value instanceof Number) {
                    sb.append(value);
                } else if (value instanceof Boolean) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(escapeJson(value.toString())).append("\"");
                }
            }
            sb.append("}");
        }
        sb.append("],\"rowCount\":").append(rows.size()).append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String stackTraceToString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append(e.toString()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void pause(String taskId) throws ITaskExecutor.TaskExecutionException {
        // DorisRunner 不支持暂停 — 无状态执行
    }

    @Override
    public void resume(String taskId) throws ITaskExecutor.TaskExecutionException {
        // DorisRunner 不支持恢复 — 无状态执行
    }

    @Override
    public TaskStatus getStatus(String taskId) throws ITaskExecutor.TaskExecutionException {
        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setStatus(Status.SUCCEEDED);
        status.setStatusMessage("DorisRunner is stateless — last execution completed");
        status.setProgress(100);
        return status;
    }
}
