package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.UdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UDF 服务实现 — 用户自定义函数管理 + SQL→UDF 转换。
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class UdfServiceImpl implements UdfService {

    private static final Logger log = LoggerFactory.getLogger(UdfServiceImpl.class);

    private final JdbcTemplate jdbc;

    public UdfServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_udf (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(200) NOT NULL UNIQUE,
                    category VARCHAR(50),
                    language VARCHAR(20) DEFAULT 'python',
                    signature TEXT,
                    source_code TEXT NOT NULL,
                    compiled_path VARCHAR(500),
                    version INTEGER DEFAULT 1,
                    author VARCHAR(100),
                    is_shared BOOLEAN DEFAULT false,
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("ecos_pipeline_udf 表已就绪");
        } catch (Exception e) {
            log.warn("ecos_pipeline_udf 表初始化异常: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> register(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        String sourceCode = (String) body.get("source_code");

        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name 不能为空");
        if (sourceCode == null || sourceCode.isEmpty()) throw new IllegalArgumentException("source_code 不能为空");

        jdbc.update(
            "INSERT INTO ecos_pipeline_udf (id, name, category, language, signature, source_code, author, is_shared, description) " +
            "VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?)",
            id, name,
            body.getOrDefault("category", "transform"),
            body.getOrDefault("language", "python"),
            body.getOrDefault("signature", "[]"),
            sourceCode,
            body.getOrDefault("author", "system"),
            body.getOrDefault("is_shared", false),
            body.getOrDefault("description", ""));

        log.info("注册 UDF: {} (id={})", name, id);
        return getById(id);
    }

    @Override
    public Map<String, Object> update(String id, Map<String, Object> body) {
        getById(id);
        jdbc.update(
            "UPDATE ecos_pipeline_udf SET name = COALESCE(?, name), category = COALESCE(?, category), " +
            "language = COALESCE(?, language), signature = COALESCE(?::text, signature), " +
            "source_code = COALESCE(?, source_code), description = COALESCE(?, description), " +
            "is_shared = COALESCE(?, is_shared), version = version + 1, updated_at = NOW() WHERE id = ?",
            body.get("name"), body.get("category"), body.get("language"),
            body.get("signature"), body.get("source_code"), body.get("description"),
            body.get("is_shared"), id);
        log.info("更新 UDF: id={}", id);
        return getById(id);
    }

    @Override
    public void delete(String id) {
        getById(id);
        jdbc.update("DELETE FROM ecos_pipeline_udf WHERE id = ?", id);
        log.info("删除 UDF: id={}", id);
    }

    @Override
    public Map<String, Object> getById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_udf WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("UDF 不存在: " + id);
        return rows.get(0);
    }

    @Override
    public Map<String, Object> list(int page, int pageSize, String category, String language) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_pipeline_udf WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (language != null && !language.isEmpty()) {
            sql.append(" AND language = ?");
            params.add(language);
        }

        int total = jdbc.queryForObject("SELECT COUNT(*)" + sql.substring(sql.indexOf("FROM") - 1), Integer.class, params.toArray());
        int offset = Math.max(0, page - 1) * pageSize;

        sql.append(" ORDER BY updated_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    @Override
    public Map<String, Object> test(String id, Map<String, Object> params) {
        Map<String, Object> udf = getById(id);
        String language = (String) udf.get("language");
        String sourceCode = (String) udf.get("source_code");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("udfId", id);
        result.put("name", udf.get("name"));
        result.put("language", language);

        try {
            UdfSandbox.SandboxOutput sandboxResult = UdfSandbox.execute(language, sourceCode, params);
            result.put("status", sandboxResult.success ? "PASSED" : "FAILED");
            result.put("output", sandboxResult.output);
            result.put("error", sandboxResult.error);
            result.put("elapsedMs", sandboxResult.elapsedMs);
            result.put("message", sandboxResult.success ? "UDF 沙箱执行通过" : "UDF 沙箱执行失败: " + sandboxResult.error);
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            result.put("message", "UDF 测试异常: " + e.getMessage());
        }

        log.info("测试 UDF: {}, language={}, status={}", udf.get("name"), language, result.get("status"));
        return result;
    }

    @Override
    public Map<String, Object> convertSqlToUdf(String sql, String language) {
        if (sql == null || sql.isEmpty()) throw new IllegalArgumentException("SQL 不能为空");
        String lang = language != null ? language : "python";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalSql", sql);
        result.put("language", lang);

        if ("python".equalsIgnoreCase(lang)) {
            String udfCode = convertToPythonUdf(sql);
            result.put("generatedCode", udfCode);
            result.put("functionName", extractFunctionName(sql));
        } else if ("java".equalsIgnoreCase(lang)) {
            result.put("generatedCode", convertToJavaUdf(sql));
            result.put("functionName", extractFunctionName(sql));
        } else {
            result.put("generatedCode", "-- SQL UDF: " + sql);
        }

        log.info("SQL→UDF 转换: lang={}, sqlLen={}", lang, sql.length());
        return result;
    }

    /**
     * SQL → Python UDF 骨架转换。
     * 正则提取 SELECT → def transform(df) 骨架。
     */
    private String convertToPythonUdf(String sql) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Auto-generated UDF from SQL\n");
        sb.append("import pandas as pd\n");
        sb.append("import numpy as np\n\n");

        String funcName = extractFunctionName(sql);
        sb.append("def ").append(funcName).append("(df: pd.DataFrame, params: dict = None) -> pd.DataFrame:\n");
        sb.append("    \"\"\"\n");
        sb.append("    SQL: ").append(truncate(sql, 80)).append("\n");
        sb.append("    \"\"\"\n");

        // 解析 SELECT 列
        Pattern selectPattern = Pattern.compile("SELECT\\s+(.+?)\\s+FROM", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = selectPattern.matcher(sql);
        if (m.find()) {
            String columns = m.group(1).trim();
            sb.append("    # Columns: ").append(columns).append("\n");
            sb.append("    # TODO: 实现具体转换逻辑\n");
            sb.append("    result = df.copy()\n");
            sb.append("    return result\n");
        } else {
            sb.append("    # TODO: 解析 SQL 并实现转换逻辑\n");
            sb.append("    result = df.copy()\n");
            sb.append("    return result\n");
        }
        return sb.toString();
    }

    private String convertToJavaUdf(String sql) {
        String funcName = extractFunctionName(sql);
        return """
            // Auto-generated UDF from SQL
            // SQL: %s
            
            import com.chinacreator.gzcm.runtime.core.transform.TransformStep;
            
            public class %sTransform implements TransformStep {
                @Override
                public DataFrame apply(DataFrame input) {
                    // TODO: 实现 SQL 逻辑
                    return input;
                }
            }
            """.formatted(truncate(sql, 60), capitalize(funcName));
    }

    private String extractFunctionName(String sql) {
        // 尝试从 FROM 子句提取表名
        Pattern tablePattern = Pattern.compile("FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher m = tablePattern.matcher(sql);
        if (m.find()) {
            return "transform_" + m.group(1).toLowerCase();
        }
        return "transform_custom";
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
