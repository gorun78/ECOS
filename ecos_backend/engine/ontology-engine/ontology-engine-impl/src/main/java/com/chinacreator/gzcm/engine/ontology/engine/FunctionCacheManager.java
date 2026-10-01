package com.chinacreator.gzcm.engine.ontology.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.engine.ontology.dao.FunctionAuditDao;

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
 * <p><b>审计双通道（W82/M1 · F03-06）</b>：{@link #writeAudit(FunctionAuditRecord)} 优先发
 * {@code FUNCTION_EXEC} 事件到 {@code KafkaTopics.AUDIT}（Kafka 为 source of truth）；
 * Kafka 不可用/发布失败 → 本地兜底写 {@code ecos_ontology.ecos_function_audit}（V172）；
 * 两者皆失败 → 抛 {@link AuditWriteException}（Controller 映射 500 ECOS-ONTO-061 拒收）。</p>
 *
 * <p>B3c 数据访问收口（架构铁律 §2.5 #1 / IR06）：本类不再直接持有/引用 {@code JdbcTemplate}，
 * 审计 SQL 全部委托给 {@link FunctionAuditDao}（继承 {@code JdbcTemplateDaoSupport}）。</p>
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

    /** 审计双通道写盘与查询下放到 DAO（B3c 收口）。 */
    private final FunctionAuditDao dao;

    /** Kafka 审计通道（runtime-event 事件总线）；缺 Bean 时走内存 fallback，不可用则纯本地兜底。 */
    private final EventBusService eventBus;

    /** 控制域 schema（ST07 · W82）：新审计表 {@code {schema}.ecos_function_audit}，默认 ecos_ontology。 */
    private final String controlSchema;

    @Autowired
    public FunctionCacheManager(FunctionAuditDao dao,
                                @Autowired(required = false) EventBusService eventBus,
                                @Value("${ecos.ontology.control-schema:ecos_ontology}") String controlSchema) {
        this.dao = dao;
        this.eventBus = eventBus;
        this.controlSchema = (controlSchema == null || controlSchema.isBlank()) ? "ecos_ontology" : controlSchema;
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
     * 写入审计日志（<b>新·W82</b> 双通道：Kafka 优先 + 本地兜底）。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>构造 {@code FUNCTION_EXEC} 事件（detail 清单见 V172 / F03-06）→
     *       {@code eventBus.publish(KafkaTopics.AUDIT, event)}；成功即返回（Kafka 为 source of truth，无需本地写）。</li>
     *   <li>eventBus 缺 Bean 或 publish 抛异常 → 落本地兜底
     *       {@code {schema}.ecos_function_audit}（V172 新表）。</li>
     *   <li>本地兜底成功 → {@code log.warn}（Kafka 不可用已降级）；本地失败 → 抛 {@link AuditWriteException}（双失败=拒收）。</li>
     * </ol>
     */
    public void writeAudit(FunctionAuditRecord r) {
        // 1) Kafka 优先
        if (eventBus != null) {
            try {
                eventBus.publish(KafkaTopics.AUDIT, buildFunctionExecEvent(r));
                log.debug("Audit published to Kafka topic={} functionId={}",
                        KafkaTopics.AUDIT, r.functionId());
                return;
            } catch (Exception e) {
                log.warn("Kafka audit publish failed, falling back to local table: {}", e.getMessage());
                // fall through → 本地兜底
            }
        } else {
            log.debug("EventBus bean absent, skipping Kafka audit path");
        }

        // 2) 本地兜底
        try {
            localFallbackInsert(r);
            log.warn("Kafka audit channel unavailable, fell back to local table {}.ecos_function_audit",
                    controlSchema);
        } catch (Exception e) {
            log.error("Function audit dual-fail (Kafka + local both failed): {}", e.getMessage());
            throw new AuditWriteException(
                    "function audit dual-fail (Kafka + local). status=" + r.status()
                            + " caller=" + r.callerId(), e);
        }
    }

    /**
     * 构造 {@code FUNCTION_EXEC} 事件（Kafka detail 清单，V172 / F03-06）。
     *
     * <p>{@code expression} 事件侧截断 ≤512（列存全长由本地兜底承担）；
     * {@code cacheHit(boolean)} → 事件字段，落库映射 is_cache_hit 0/1。</p>
     */
    private Map<String, Object> buildFunctionExecEvent(FunctionAuditRecord r) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("type", "FUNCTION_EXEC");
        ev.put("functionId", r.functionId());
        ev.put("functionName", r.functionName());
        ev.put("expression", truncate(r.expression(), 512));
        ev.put("objectType", r.objectType());
        ev.put("params", r.params());
        ev.put("resultSummary", r.resultSummary());
        ev.put("durationMs", r.durationMs());
        ev.put("cacheHit", r.cacheHit());
        ev.put("traceId", r.traceId());
        ev.put("status", r.status());
        return ev;
    }

    /**
     * 本地兜底：INSERT 到 {@code {controlSchema}.ecos_function_audit}（V172）。
     *
     * <p>B3c 收口：SQL 与参数序原样搬至 {@link FunctionAuditDao#localFallbackInsert}；
     * 本方法仅保留 truncation 规则的应用（DR04：本层只做存取校验），再委托 DAO 落盘。</p>
     */
    private void localFallbackInsert(FunctionAuditRecord r) {
        // B3c 收口：DAO 内部已硬编码 is_deleted=0, version_no="1", domain="default"
        // （与 V172 表审计列的默认语义一致），故本方法只传 15 个业务字段。
        dao.localFallbackInsert(
                UUID.randomUUID().toString(),
                truncate(r.functionId(), 64),
                truncate(r.functionName(), 255),
                r.expression(),
                truncate(r.objectType(), 128),
                toParamsJson(r.params()),
                truncate(r.resultSummary(), 1000),
                (int) r.durationMs(),
                r.cacheHit() ? 1 : 0,
                truncate(r.callerId(), 64),
                truncate(r.traceId(), 64),
                r.status(),
                truncate(r.errorMessage(), 2000),
                truncate(r.callerId(), 100),
                truncate(r.callerId(), 100));
    }

    /**
     * 写入审计日志（<b>旧·8 参签名</b>，保留向后兼容，委托新签名）。
     *
     * <p>W80 错位修正：首参 {@code functionId} 写 {@code function_id} 列（旧代码把 function_id 与
     * function_name 混用，/test 路径 functionId 恒 null 但函数名错位到 entityName 位置）。
     * 新签名 {@link #writeAudit(FunctionAuditRecord)} 用 builder 语义清晰分开字段。</p>
     */
    @Deprecated
    public void writeAudit(String functionId, String expression, String entityName,
                           String resultValue, long executionTimeMs, String callerId,
                           String status, String errorMessage) {
        writeAudit(FunctionAuditRecord.builder()
                .functionId(functionId)
                .expression(expression)
                .objectType(entityName)
                .resultSummary(resultValue)
                .durationMs(executionTimeMs)
                .callerId(callerId)
                .status(status)
                .errorMessage(errorMessage)
                .build());
    }

    /**
     * 查询审计日志（分页）—— 读取 V172 新表 {@code {schema}.ecos_function_audit}
     * （显式列清单禁 SELECT *，参数化，ORDER BY create_time DESC 命中 idx_ecos_func_audit_created）。
     *
     * <p>B3c 收口：SQL 与 count/items 双查询 try/catch 语义搬入 {@link FunctionAuditDao#queryAudit}。</p>
     */
    public Map<String, Object> queryAudit(String status, String callerId, int page, int pageSize) {
        return dao.queryAudit(status, callerId, page, pageSize);
    }

    // ── private helpers ────────────────────────────────

    /**
     * params 入参 → JSON 文本（DR04/MC02：只做存取禁 WHERE/JOIN/索引）。
     * null → null；String 视为已是 JSON 文本原样；Number/Boolean 转十进制；
     * 其余 Object 走 {@code String.valueOf}（不引入新依赖）。
     */
    private String toParamsJson(Object params) {
        if (params == null) return null;
        if (params instanceof String s) return s;
        if (params instanceof Number n) return n.toString();
        if (params instanceof Boolean b) return b.toString();
        return String.valueOf(params);
    }

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

    /**
     * 审计双失败异常（Kafka + 本地兜底均失败，W82）。
     *
     * <p>Controller 捕获后映射 {@code 500 ECOS-ONTO-061}（对函数执行 = 拒收）。</p>
     */
    public static class AuditWriteException extends RuntimeException {
        public AuditWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class CacheEntry<T> {
        final T data;
        final long createdAt;

        CacheEntry(T data) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
