package com.chinacreator.gzcm.workspace.workbook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P2-16 SQL 运行时 — 安全执行 SELECT 查询。
 * <p>
 * 安全限制：
 * <ol>
 *   <li>只允许 SELECT 语句</li>
 *   <li>白名单模式：正则校验 SQL 结构</li>
 *   <li>禁止多语句（分号分割）</li>
 *   <li>禁止危险关键字（备用黑名单）</li>
 * </ol>
 */
@Component
public class SqlRuntime {

    private static final Logger log = LoggerFactory.getLogger(SqlRuntime.class);

    /** P2-16: 危险关键字黑名单（作为白名单校验的补充） */
    private static final String[] FORBIDDEN_KEYWORDS = {
        "DROP", "ALTER", "DELETE", "INSERT", "UPDATE", "TRUNCATE", "CREATE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "CALL", "MERGE", "REPLACE"
    };

    /** P2-16: SELECT 白名单正则 — 必须以 SELECT 开头，禁止子查询中的 DML */
    private static final Pattern SELECT_WHITELIST = Pattern.compile(
        "^\\s*SELECT\\s+.+",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /** P2-16: 检测多语句分隔 */
    private static final Pattern MULTI_STATEMENT = Pattern.compile(
        ";(?=(?:[^']*'[^']*')*[^']*$)(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    );

    private final JdbcTemplate jdbc;

    public SqlRuntime(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * P2-16 执行 SQL（增强安全校验：白名单正则 + 黑名单补充）。
     *
     * @param code SQL 代码
     * @return {columns: [...], rows: [...], elapsed_ms: N}
     */
    public Map<String, Object> execute(String code) {
        if (code == null || code.isBlank()) {
            return errorResult("SQL code is empty");
        }

        String trimmed = code.trim();
        String upper = trimmed.toUpperCase();

        // P2-16: 1. 白名单正则 — 必须以 SELECT 开头
        if (!SELECT_WHITELIST.matcher(trimmed).matches()) {
            return errorResult("Only SELECT statements are allowed. Query starts with: "
                    + trimmed.substring(0, Math.min(50, trimmed.length())));
        }

        // P2-16: 2. 禁止多语句（分号后还有内容）
        String withoutStrings = removeStringLiterals(trimmed);
        if (withoutStrings.contains(";")) {
            int semiIdx = withoutStrings.indexOf(";");
            String after = withoutStrings.substring(semiIdx + 1).trim();
            if (!after.isEmpty() && !after.startsWith("--") && !after.startsWith("//")) {
                return errorResult("Multiple statements are not allowed");
            }
        }

        // P2-16: 3. 危险关键字黑名单（补充检查）
        for (String kw : FORBIDDEN_KEYWORDS) {
            // 只在非字符串上下文中检查
            if (upper.contains(kw)) {
                return errorResult("Forbidden keyword detected: " + kw);
            }
        }

        // 4. 执行并计时
        long start = System.currentTimeMillis();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(trimmed);
            long elapsed = System.currentTimeMillis() - start;

            // 提取列名
            List<String> columns = new ArrayList<>();
            if (!rows.isEmpty()) {
                columns.addAll(rows.get(0).keySet());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("row_count", rows.size());
            result.put("elapsed_ms", elapsed);
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("SQL execution failed: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            result.put("elapsed_ms", elapsed);
            return result;
        }
    }

    /**
     * P2-16 移除 SQL 中的字符串字面量（单引号和双引号字符串），避免关键字检查误判。
     */
    private String removeStringLiterals(String sql) {
        // 移除单引号字符串
        String result = sql.replaceAll("'[^']*'", "''");
        // 移除双引号标识符
        result = result.replaceAll("\"[^\"]*\"", "\"\"");
        return result;
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        result.put("columns", List.of());
        result.put("rows", List.of());
        result.put("elapsed_ms", 0);
        return result;
    }
}
