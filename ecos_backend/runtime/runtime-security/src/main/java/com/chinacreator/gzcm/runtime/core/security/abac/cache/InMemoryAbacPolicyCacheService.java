package com.chinacreator.gzcm.runtime.core.security.abac.cache;


import java.util.Collections;

import java.util.List;

import java.util.concurrent.Executors;

import java.util.concurrent.ScheduledExecutorService;

import java.util.concurrent.TimeUnit;

import java.util.concurrent.atomic.AtomicReference;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.cache.AbacPolicyCacheService;

/**
 * 基于内存的ABAC策略缓存实现
 * 使用原子引用保证线程安全，支持自动刷新
 */
public class InMemoryAbacPolicyCacheService implements AbacPolicyCacheService {
    
    private final AtomicReference<List<AbacPolicy>> cachedPolicies = new AtomicReference<>();
    private final long defaultTtlMillis;
    private volatile long lastRefreshTime = 0;
    private final ScheduledExecutorService refreshExecutor;
    
    public InMemoryAbacPolicyCacheService() {
        this(resolveDefaultTtlMillis());
    }
    
    public InMemoryAbacPolicyCacheService(long defaultTtlMillis) {
        long resolved = defaultTtlMillis > 0 ? defaultTtlMillis : resolveDefaultTtlMillis();
        // TTL 限制在 5~30 分钟之间
        long min = TimeUnit.MINUTES.toMillis(5);
        long max = TimeUnit.MINUTES.toMillis(30);
        this.defaultTtlMillis = Math.min(Math.max(resolved, min), max);
        
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "abac-policy-cache-refresher");
            t.setDaemon(true);
            return t;
        });
    }
    
    @Override
    public List<AbacPolicy> getAllPolicies() {
        List<AbacPolicy> policies = cachedPolicies.get();
        if (policies == null || isExpired()) {
            return null; // 缓存过期或不存在，需要从服务层重新加载
        }
        return policies;
    }
    
    @Override
    public void refreshAll(List<AbacPolicy> policies) {
        if (policies == null) {
            cachedPolicies.set(Collections.emptyList());
        } else {
            cachedPolicies.set(Collections.unmodifiableList(policies));
        }
        lastRefreshTime = System.currentTimeMillis();
    }
    
    @Override
    public void evictAll() {
        cachedPolicies.set(null);
        lastRefreshTime = 0;
    }
    
    /**
     * 检查缓存是否过期
     */
    private boolean isExpired() {
        return (System.currentTimeMillis() - lastRefreshTime) > defaultTtlMillis;
    }
    
    /**
     * 设置自动刷新任务（可选，由外部调用）
     */
    public void scheduleAutoRefresh(Runnable refreshTask, long intervalMillis) {
        if (refreshTask != null && intervalMillis > 0) {
            refreshExecutor.scheduleAtFixedRate(() -> {
                try {
                    refreshTask.run();
                } catch (Exception e) {
                    // 记录日志，但不中断调度
                    System.err.println("ABAC策略缓存自动刷新失败: " + e.getMessage());
                }
            }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * 关闭资源
     */
    public void shutdown() {
        if (refreshExecutor != null && !refreshExecutor.isShutdown()) {
            refreshExecutor.shutdown();
        }
    }
    
    private static long resolveDefaultTtlMillis() {
        String sysProp = System.getProperty("security.abac.policy.cache.ttl-ms");
        String envProp = System.getenv("SECURITY_ABAC_POLICY_CACHE_TTL_MS");
        return parseLongOrDefault(sysProp, parseLongOrDefault(envProp, TimeUnit.MINUTES.toMillis(15)));
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
