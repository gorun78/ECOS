package com.chinacreator.gzcm.engine.security.policy.cache.impl;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.chinacreator.gzcm.engine.security.policy.cache.DecisionCacheService;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 基于内存的决策结果缓存实现
 * 使用 Caffeine 存储，支持 TTL 和自动清理，无需手写 ScheduledExecutorService
 */
public class InMemoryDecisionCacheService implements DecisionCacheService {

    private final Cache<String, PolicyDecision> cache;
    private final long defaultTtlMillis;

    public InMemoryDecisionCacheService() {
        this(resolveDefaultTtlMillis());
    }

    public InMemoryDecisionCacheService(long defaultTtlMillis) {
        long resolved = defaultTtlMillis > 0 ? defaultTtlMillis : resolveDefaultTtlMillis();
        // TTL 限制在 1~10 分钟之间
        long min = TimeUnit.MINUTES.toMillis(1);
        long max = TimeUnit.MINUTES.toMillis(10);
        this.defaultTtlMillis = Math.min(Math.max(resolved, min), max);

        this.cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(this.defaultTtlMillis, TimeUnit.MILLISECONDS)
            .build();
    }

    @Override
    public PolicyDecision get(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }
        return cache.getIfPresent(cacheKey);
    }

    @Override
    public void put(String cacheKey, PolicyDecision decision, long ttlMillis) {
        if (cacheKey == null || decision == null) {
            return;
        }
        cache.put(cacheKey, decision);
    }

    @Override
    public void evict(String cacheKey) {
        if (cacheKey == null) {
            return;
        }

        // 支持通配符匹配（如 userId:*）
        if (cacheKey.endsWith("*")) {
            String prefix = cacheKey.substring(0, cacheKey.length() - 1);
            Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + ".*");
            cache.asMap().keySet().removeIf(key -> pattern.matcher(key).matches());
        } else {
            cache.invalidate(cacheKey);
        }
    }

    @Override
    public void evictAll() {
        cache.invalidateAll();
    }

    /**
     * 清理过期条目（Caffeine 自动管理，此方法仅用于显式触发）
     */
    public void cleanUp() {
        cache.cleanUp();
    }

    /**
     * 关闭资源（Caffeine 无需关闭资源，保留方法兼容调用方）
     */
    public void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
    }

    private static long resolveDefaultTtlMillis() {
        String sysProp = System.getProperty("security.policy.decision.cache.ttl-ms");
        String envProp = System.getenv("SECURITY_POLICY_DECISION_CACHE_TTL_MS");
        return parseLongOrDefault(sysProp, parseLongOrDefault(envProp, TimeUnit.MINUTES.toMillis(5)));
    }

    private static long parseLongOrDefault(String source, long defaultValue) {
        if (source == null || source.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(source.trim());
        } catch (NumberFormatException ignore) {
            return defaultValue;
        }
    }
}
