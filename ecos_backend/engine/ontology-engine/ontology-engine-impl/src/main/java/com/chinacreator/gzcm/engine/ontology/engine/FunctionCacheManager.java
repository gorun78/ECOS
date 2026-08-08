package com.chinacreator.gzcm.engine.ontology.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FunctionCacheManager — Function 执行结果缓存 + 审计日志写入。
 *
 * <p>使用 ConcurrentHashMap 实现简单 TTL 缓存（不引入 Caffeine 依赖）：</p>
 * <ul>
 *   <li>最大缓存条目: 1000</li>
 *   <li>TTL: 300 秒</li>
 *   <li>缓存键: expression + "|" + entityName</li>
 * </ul>
 *
 * <p>审计日志写入 ecos_function_audit_log 表。</p>
 */
@Component
public class FunctionCacheManager {

    private static final Logger log = LoggerFactory.getLogger(FunctionCacheManager.class);

    /** 缓存 TTL (毫秒) */
    private static final long TTL_MS = 300_000L; // 300s

    /** 最大缓存条目 */
    private static final int MAX_ENTRIES = 1000;

    /** 内存缓存 */
    private final ConcurrentHashMap<String, CacheEntry<FunctionResult>> cache = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbc;

    public FunctionCacheManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 从缓存获取结果。返回 null 表示缓存未命中或已过期。
     */
    public FunctionResult get(String expression, String entityName) {
        String key = buildKey(expression, entityName);
        CacheEntry<FunctionResult> entry = cache.get(key);
        if (entry == null) return null;

        // TTL 检查
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            cache.remove(key);
            return null;
        }

        log.debug("Cache hit for key={}", key);
        FunctionResult result = entry.data;
        if (result != null) {
            result.setFromCache(true);
            result.setCacheKey(key);
        }
        return result;
    }

    /**
     * 写入缓存。
     */
    public void put(String expression, String entityName, FunctionResult result) {
        // 容量控制：超过上限时清理过期条目
        if (cache.size() >= MAX_ENTRIES) {
            evictExpired();
            // 如果还是满的，随机淘汰一个
            if (cache.size() >= MAX_ENTRIES) {
                String firstKey = cache.keys().nextElement();
                cache.remove(firstKey);
                log.debug("Cache evicted (full): key={}", firstKey);
            }
        }

        String key = buildKey(expression, entityName);
        cache.put(key, new CacheEntry<>(result));
        log.debug("Cached result for key={}", key);
    }

    /**
     * 本体变更时主动失效相关缓存（按实体名模糊匹配）。
     */
    public void invalidateByEntity(String entityName) {
        if (entityName == null) return;
        cache.keySet().removeIf(key -> key.contains(entityName));
        log.info("Cache invalidated for entity={}", entityName);
    }

    /**
     * 清空所有缓存。
     */
    public void invalidateAll() {
        cache.clear();
        log.info("All function caches invalidated");
    }

    /**
     * 缓存大小。
     */
    public int size() {
        return cache.size();
    }

    // ── 审计日志 ─────────────────────────────────────

    /**
     * 写入审计日志。
     */
    public void writeAudit(String functionName, String expression, String entityName,
                            String resultValue, long executionTimeMs, String callerId,
                            String status, String errorMessage) {
        try {
            jdbc.update("""
                INSERT INTO ecos_function_audit_log
                    (function_name, expression, entity_name, result_value,
                     execution_time_ms, caller_id, status, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                truncate(functionName, 256),
                expression,
                entityName,
                truncate(resultValue, 1000),
                executionTimeMs,
                truncate(callerId, 64),
                status,
                truncate(errorMessage, 2000));
            log.debug("Audit log written: status={} caller={}", status, callerId);
        } catch (Exception e) {
            log.error("Failed to write function audit log: {}", e.getMessage());
        }
    }

    /**
     * 查询审计日志（分页）。
     */
    public Map<String, Object> queryAudit(String status, String callerId, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();

        StringBuilder sql = new StringBuilder(
            "SELECT * FROM ecos_function_audit_log WHERE 1=1");
        StringBuilder countSql = new StringBuilder(
            "SELECT COUNT(*) FROM ecos_function_audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
        }
        if (callerId != null && !callerId.isBlank()) {
            sql.append(" AND caller_id = ?");
            countSql.append(" AND caller_id = ?");
            params.add(callerId);
        }

        // 总数
        int total;
        try {
            Integer count = jdbc.queryForObject(countSql.toString(), Integer.class, params.toArray());
            total = count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count audit logs: {}", e.getMessage());
            total = 0;
        }

        // 分页数据
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add((page - 1) * pageSize);

        List<Map<String, Object>> items;
        try {
            items = jdbc.queryForList(sql.toString(), dataParams.toArray());
        } catch (Exception e) {
            log.warn("Failed to query audit logs: {}", e.getMessage());
            items = List.of();
        }

        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    // ── private helpers ────────────────────────────────

    private String buildKey(String expression, String entityName) {
        return (expression != null ? expression : "") + "|" + (entityName != null ? entityName : "");
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now - entry.getValue().createdAt > TTL_MS);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen);
    }

    // ── 内部类 ──────────────────────────────────────

    private static class CacheEntry<T> {
        final T data;
        final long createdAt;

        CacheEntry(T data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
