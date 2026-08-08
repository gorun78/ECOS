package com.chinacreator.gzcm.engine.ontology.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * FunctionResult — Function 沙箱执行结果 POJO。
 *
 * <p>包含计算结果、SQL类型、执行耗时和编译后的SQL（供前端调试）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionResult {

    /** 计算结果（单值） */
    private Object value;

    /** SQL 类型：NUMERIC / STRING / BOOLEAN */
    private String sqlType;

    /** 执行耗时（毫秒） */
    private long executionTimeMs;

    /** 编译生成的 SQL（供调试/预览） */
    private String compiledSql;

    /** 是否来自缓存 */
    private boolean fromCache;

    /** 缓存键 */
    private String cacheKey;

    // ── 构造器 ──────────────────────────────────────

    public FunctionResult() {}

    public FunctionResult(Object value, String sqlType, long executionTimeMs, String compiledSql) {
        this.value = value;
        this.sqlType = sqlType;
        this.executionTimeMs = executionTimeMs;
        this.compiledSql = compiledSql;
    }

    // ── 工厂方法 ────────────────────────────────────

    public static FunctionResult of(Object value, String sqlType, long executionTimeMs, String compiledSql) {
        return new FunctionResult(value, sqlType, executionTimeMs, compiledSql);
    }

    // ── getter/setter ───────────────────────────────

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getCompiledSql() { return compiledSql; }
    public void setCompiledSql(String compiledSql) { this.compiledSql = compiledSql; }

    public boolean isFromCache() { return fromCache; }
    public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }

    public String getCacheKey() { return cacheKey; }
    public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
}
