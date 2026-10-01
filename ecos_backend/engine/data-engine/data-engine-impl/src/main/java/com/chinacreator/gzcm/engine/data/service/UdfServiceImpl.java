package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.UdfService;
import com.chinacreator.gzcm.engine.data.dao.UdfDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UDF 服务实现 — 用户自定义函数管理 + SQL→UDF 转换。
 * <p>原始 SQL 由 {@link UdfDao} 执行。</p>
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class UdfServiceImpl implements UdfService {

    private static final Logger log = LoggerFactory.getLogger(UdfServiceImpl.class);

    private final UdfDao udfDao;

    public UdfServiceImpl(UdfDao udfDao) {
        this.udfDao = udfDao;
    }

    // H8-T1: ecos_pipeline_udf 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL

    @Override
    public Map<String, Object> register(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        String sourceCode = (String) body.get("source_code");

        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name 不能为空");
        if (sourceCode == null || sourceCode.isEmpty()) throw new IllegalArgumentException("source_code 不能为空");

        udfDao.insert(id, body);

        log.info("注册 UDF: {} (id={})", name, id);
        return getById(id);
    }

    @Override
    public Map<String, Object> update(String id, Map<String, Object> body) {
        getById(id);
        udfDao.update(id, body);
        log.info("更新 UDF: id={}", id);
        return getById(id);
    }

    @Override
    public void delete(String id) {
        getById(id);
        udfDao.delete(id);
        log.info("删除 UDF: id={}", id);
    }

    @Override
    public Map<String, Object> getById(String id) {
        Map<String, Object> row = udfDao.getByIdRow(id);
        if (row == null) throw new IllegalArgumentException("UDF 不存在: " + id);
        return row;
    }

    @Override
    public Map<String, Object> list(int page, int pageSize, String category, String language) {
        return udfDao.list(page, pageSize, category, language);
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

            import com.chinacreator.gzcm.engine.data.transform.TransformStep;

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
