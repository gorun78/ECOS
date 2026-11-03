package com.chinacreator.gzcm.runtime.core.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 字符串缓存通道（§2.9.8 远程共享层；#147AF/D 批）。
 *
 * <p><b>职责边界</b>：本类只做「键 → 字符串 + TTL」的存取与清理，<b>不理解</b>缓存对象语义；
 * 键规范、TTL 上限、失效时机仍由 {@link RuntimeCacheManager} 单源决定（本类不自行拼键）。</p>
 *
 * <p><b>失败语义</b>：任何 Redis 异常都被吞掉并降级为「未命中 / no-op」——缓存是受控加速层，
 * 不可用只会让调用方回源；<b>绝不</b>因此改变鉴权/授权结论（§2.9.8⑦）。降级会按 WARN 留痕。</p>
 *
 * <p><b>可用性探测</b>：{@link #available()} 以 PING 结果为准并缓存 10s，避免每次访问都打网络；
 * 探测失败即视为不可用，直到下一次窗口重试。</p>
 *
 * @author ECOS Runtime #147AF
 */
@Component
public class RedisStringCache {

    private static final Logger log = LoggerFactory.getLogger(RedisStringCache.class);

    /** 可用性探测缓存窗口（毫秒）。 */
    private static final long PROBE_TTL_MILLIS = 10_000L;

    private final ObjectProvider<StringRedisTemplate> templates;

    private volatile boolean probed;
    private volatile boolean available;
    private volatile long probedAt;

    public RedisStringCache(ObjectProvider<StringRedisTemplate> templates) {
        this.templates = templates;
    }

    /** Redis 是否可用（PING 成功）；不可用即整个通道降级。 */
    public boolean available() {
        long now = System.currentTimeMillis();
        if (probed && now - probedAt < PROBE_TTL_MILLIS) {
            return available;
        }
        StringRedisTemplate template = template();
        if (template == null) {
            probed = true;
            probedAt = now;
            available = false;
            return false;
        }
        try {
            String pong = template.execute(connection -> connection.ping(), true);
            available = pong != null;
        } catch (RuntimeException e) {
            log.warn("Redis PING 失败，缓存远程通道降级（不会影响鉴权）: {}", e.getMessage());
            available = false;
        }
        probed = true;
        probedAt = now;
        return available;
    }

    /** 读；异常/不可用一律视为未命中。 */
    public Optional<String> get(String key) {
        StringRedisTemplate template = template();
        if (template == null || !available()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(template.opsForValue().get(key));
        } catch (RuntimeException e) {
            log.warn("Redis GET 失败(key={})，按未命中处理: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 写；失败即 no-op（调用方仍视作未缓存，后续回源）。 */
    public void put(String key, String value, long ttlSeconds) {
        StringRedisTemplate template = template();
        if (template == null || value == null || ttlSeconds <= 0 || !available()) {
            return;
        }
        try {
            template.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException e) {
            log.warn("Redis SET 失败(key={})，本次不缓存: {}", key, e.getMessage());
        }
    }

    /** 精确删除。 */
    public void evict(String key) {
        StringRedisTemplate template = template();
        if (template == null || !available()) {
            return;
        }
        try {
            template.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis DEL 失败(key={})，交由 TTL 收敛: {}", key, e.getMessage());
        }
    }

    /**
     * 前缀删除（域级/对象级失效）。使用 SCAN 而非 KEYS，避免阻塞 Redis 单线程。
     *
     * @return 实际删除的键数（不可用时 0）
     */
    public int evictPrefix(String prefix) {
        StringRedisTemplate template = template();
        if (template == null || !available()) {
            return 0;
        }
        int removed = 0;
        try (Cursor<String> cursor = template.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                template.delete(cursor.next());
                removed++;
            }
        } catch (RuntimeException e) {
            log.warn("Redis SCAN 失效失败(prefix={})，交由 TTL 收敛: {}", prefix, e.getMessage());
        }
        return removed;
    }

    /** 频道发布（Outbox 中继用）；不可用返回 -1。 */
    public long publish(String channel, String message) {
        StringRedisTemplate t = template();
        if (t == null || !available()) {
            return -1L;
        }
        try {
            Long n = t.convertAndSend(channel, message);
            return n == null ? -1L : n;
        } catch (RuntimeException e) {
            log.warn("Redis PUBLISH 失败(channel={}): {}", channel, e.getMessage());
            return -1L;
        }
    }

    /** 供运维/门禁观测：真实存在的键数（按前缀 SCAN 计数，不可用返回 -1）。 */
    public long countPrefix(String prefix) {
        StringRedisTemplate template = template();
        if (template == null || !available()) {
            return -1L;
        }
        long n = 0;
        try (Cursor<String> cursor = template.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(500).build())) {
            while (cursor.hasNext()) {
                cursor.next();
                n++;
            }
        } catch (RuntimeException e) {
            return -1L;
        }
        return n;
    }

    private StringRedisTemplate template() {
        return templates == null ? null : templates.getIfAvailable();
    }

    /** 测试/运维诊断用：命中的全部键（小规模）。 */
    public Set<String> keys(String pattern) {
        StringRedisTemplate template = template();
        if (template == null || !available()) {
            return Set.of();
        }
        try {
            Set<String> keys = template.keys(pattern);
            return keys == null ? Set.of() : keys;
        } catch (RuntimeException e) {
            return Set.of();
        }
    }
}
