package com.chinacreator.gzcm.engine.ontology.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FunctionSandboxEngine — SQL 编译 + 沙箱执行引擎。
 *
 * <p>手写简单 SQL 表达式解析器，零外部依赖（不使用 Aviator/MVEL/SpEL）。</p>
 *
 * <h3>编译流程</h3>
 * <ol>
 *   <li>解析表达式 → 提取计算部分 + FROM 实体 + WHERE 条件</li>
 *   <li>替换字符串参数为 ? 占位符</li>
 *   <li>组装参数化 SQL: SELECT (计算部分) AS result FROM 表名 WHERE 参数化条件</li>
 * </ol>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>5s 超时（Statement.setQueryTimeout）</li>
 *   <li>参数化查询防注入</li>
 *   <li>白名单函数验证（由 FunctionValidator 前置）</li>
 * </ul>
 */
@Component
public class FunctionSandboxEngine {

    private static final Logger log = LoggerFactory.getLogger(FunctionSandboxEngine.class);

    private static final Pattern FROM_PATTERN = Pattern.compile(
        "\\bFROM\\s+(\\w+(?:\\.\\w+)?)\\s*(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_PATTERN = Pattern.compile(
        "\\bWHERE\\b\\s+(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern STRING_LITERAL = Pattern.compile(
        "'([^']*)'");

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final JdbcTemplate jdbc;

    public FunctionSandboxEngine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 编译 Function 表达式为参数化 SQL。
     *
     * @param expression 原始表达式，如 "SUM(amount) / COUNT(*) FROM fin_revenue WHERE period='2026-07'"
     * @return 编译结果 {sql, params, entityName}
     */
    public Map<String, Object> compile(String expression) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = expression.trim();

        // 1. 提取 FROM 子句
        Matcher fromMatcher = FROM_PATTERN.matcher(trimmed);
        if (!fromMatcher.find()) {
            result.put("error", "无法解析 FROM 子句");
            return result;
        }

        String computePart = trimmed.substring(0, fromMatcher.start()).trim();
        String entityName = fromMatcher.group(1);
        String rest = fromMatcher.group(2) != null ? fromMatcher.group(2).trim() : "";

        // 去掉可能的末尾分号
        if (computePart.endsWith(";")) {
            computePart = computePart.substring(0, computePart.length() - 1).trim();
        }

        // 2. 提取 WHERE 子句（参数化）
        String whereClause = "";
        List<Object> params = new ArrayList<>();

        if (!rest.isEmpty()) {
            // 提取 WHERE 条件
            Matcher whereMatcher = WHERE_PATTERN.matcher(rest);
            if (whereMatcher.find()) {
                String rawWhere = whereMatcher.group(1).trim();
                // 参数化：替换字符串字面量为 ?
                StringBuffer paramWhere = new StringBuffer();
                Matcher strMatcher = STRING_LITERAL.matcher(rawWhere);
                while (strMatcher.find()) {
                    params.add(strMatcher.group(1));
                    strMatcher.appendReplacement(paramWhere, "?");
                }
                strMatcher.appendTail(paramWhere);
                whereClause = " WHERE " + paramWhere.toString();
            } else {
                // 没有 WHERE，但 rest 可能包含其他内容（如 GROUP BY、ORDER BY）
                // 直接作为附加子句
                whereClause = " " + rest;
            }
        }

        // 3. 组装 SQL
        String sql = "SELECT (" + computePart + ") AS result FROM " + entityName + whereClause;

        // 清理多余空白
        sql = sql.replaceAll("\\s+", " ").trim();

        result.put("sql", sql);
        result.put("params", params);
        result.put("entityName", entityName);

        return result;
    }

    /**
     * 编译并执行 Function 表达式。
     *
     * @param expression 原始表达式
     * @param entityTableMapping 实体名→DB表名映射（可为null则直接用实体名作表名）
     * @return 执行结果
     */
    public FunctionResult execute(String expression, Map<String, String> entityTableMapping) {
        long startTime = System.currentTimeMillis();

        // 编译
        @SuppressWarnings("unchecked")
        Map<String, Object> compiled = compile(expression);
        if (compiled.containsKey("error")) {
            throw new IllegalArgumentException("编译失败: " + compiled.get("error"));
        }

        String sql = (String) compiled.get("sql");
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) compiled.get("params");
        String entityName = (String) compiled.get("entityName");

        // 如果提供了实体表映射，替换表名
        if (entityTableMapping != null && entityTableMapping.containsKey(entityName)) {
            String tableName = entityTableMapping.get(entityName);
            sql = sql.replace("FROM " + entityName, "FROM " + tableName);
        }

        // 执行（带超时）
        String finalSql = sql;
        Object value;
        try {
            if (params.isEmpty()) {
                value = jdbc.query((Connection conn) -> {
                    PreparedStatement ps = conn.prepareStatement(finalSql);
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    return ps;
                }, (ResultSet rs) -> {
                    if (rs.next()) {
                        return rs.getObject("result");
                    }
                    return null;
                });
            } else {
                value = jdbc.query((Connection conn) -> {
                    PreparedStatement ps = conn.prepareStatement(finalSql);
                    ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    return ps;
                }, (ResultSet rs) -> {
                    if (rs.next()) {
                        return rs.getObject("result");
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Function execution failed: sql={} params={} error={}", finalSql, params, e.getMessage());
            throw new RuntimeException("Function执行失败: " + e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        String sqlType = inferSqlType(expression);

        FunctionResult result = FunctionResult.of(value, sqlType, elapsed, finalSql);
        log.info("Function executed in {}ms: sql={} value={} type={}", elapsed, finalSql, value, sqlType);
        return result;
    }

    /**
     * 测试执行（带完整审计，调用方需自行写审计日志）。
     */
    public FunctionResult test(String expression, String entityName, String callerId) {
        Map<String, String> mapping = entityName != null
            ? Map.of(entityName, entityName)
            : Map.of();
        return execute(expression, mapping);
    }

    /**
     * 仅编译（不执行），返回生成的 SQL 供前端预览。
     */
    public String compileOnly(String expression) {
        Map<String, Object> compiled = compile(expression);
        if (compiled.containsKey("error")) {
            throw new IllegalArgumentException("编译失败: " + compiled.get("error"));
        }
        return (String) compiled.get("sql");
    }

    // ── private helpers ────────────────────────────────

    /**
     * 根据表达式中使用的聚合函数推断结果类型。
     */
    static String inferSqlType(String expression) {
        String upper = expression.toUpperCase();
        if (upper.contains("COUNT(")) {
            return "LONG";
        }
        if (upper.contains("SUM(") || upper.contains("AVG(") ||
            upper.contains("ABS(") || upper.contains("ROUND(") ||
            upper.contains("CEIL(") || upper.contains("FLOOR(")) {
            return "DOUBLE";
        }
        if (upper.contains("MIN(") || upper.contains("MAX(")) {
            // MIN/MAX 类型取决于字段类型，保守返回 NUMERIC
            return "NUMERIC";
        }
        // 默认 NUMERIC
        return "NUMERIC";
    }
}
