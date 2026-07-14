package com.chinacreator.gzcm.runtime.core.security.policy.cache.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.chinacreator.gzcm.runtime.core.security.policy.cache.DecisionCacheService;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;

/**
 * 基于内存的决策结果缓存实现
 * 使用 ConcurrentHashMap 存储，支持 TTL 和自动清理
 */
public class InMemoryDecisionCacheService implements DecisionCacheService {
    
    private static class CacheEntry {
        final PolicyDecision decision;
        final long expireAt;
        
        CacheEntry(PolicyDecision decision, long expireAt) {
            this.decision = decision;
            this.expireAt = expireAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
    
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long defaultTtlMillis;
    private final ScheduledExecutorService cleaner;
    
    public InMemoryDecisionCacheService() {
        this(resolveDefaultTtlMillis());
    }
    
    public InMemoryDecisionCacheService(long defaultTtlMillis) {
        long resolved = defaultTtlMillis > 0 ? defaultTtlMillis : resolveDefaultTtlMillis();
        // TTL 限制在 1~10 分钟之间
        long min = TimeUnit.MINUTES.toMillis(1);
        long max = TimeUnit.MINUTES.toMillis(10);
        this.defaultTtlMillis = Math.min(Math.max(resolved, min), max);
        
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "decision-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        
        // 定时清理过期条目，默认每 1 分钟检查一次
        this.cleaner.scheduleAtFixedRate(
            this::evictExpiredEntries, 
            this.defaultTtlMillis, 
            TimeUnit.MINUTES.toMillis(1), 
            TimeUnit.MILLISECONDS
        );
    }
    
    @Override
    public PolicyDecision get(String cacheKey) {
        if (cacheKey == null) {
            return null;
        }
        
        CacheEntry entry = cache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        
        if (entry.isExpired()) {
            cache.remove(cacheKey);
            return null;
        }
        
        return entry.decision;
    }
    
    @Override
    public void put(String cacheKey, PolicyDecision decision, long ttlMillis) {
        if (cacheKey == null || decision == null) {
            return;
        }
        
        long ttl = ttlMillis > 0 ? ttlMillis : defaultTtlMillis;
        long expireAt = System.currentTimeMillis() + ttl;
        cache.put(cacheKey, new CacheEntry(decision, expireAt));
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
            cache.entrySet().removeIf(entry -> pattern.matcher(entry.getKey()).matches());
        } else {
            cache.remove(cacheKey);
        }
    }
    
    @Override
    public void evictAll() {
        cache.clear();
    }
    
    /**
     * 清理过期条目
     */
    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        if (cleaner != null && !cleaner.isShutdown()) {
            cleaner.shutdown();
        }
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
