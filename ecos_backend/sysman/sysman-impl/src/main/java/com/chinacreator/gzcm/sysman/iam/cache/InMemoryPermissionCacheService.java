package com.chinacreator.gzcm.sysman.iam.cache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;

/**
 * 简单基于 ConcurrentHashMap 的本地权限缓存实现。
 * 使用 TTL 做软过期，读取时懒惰清理。
 */
public class InMemoryPermissionCacheService implements PermissionCacheService {

    private static class Entry {
        final Set<Permission> permissions;
        final long expireAt;

        Entry(Set<Permission> permissions, long expireAt) {
            this.permissions = permissions;
            this.expireAt = expireAt;
        }
    }
    
    private static class DecisionEntry {
        final Map<String, Object> decisions;
        final long expireAt;
        
        DecisionEntry(Map<String, Object> decisions, long expireAt) {
            this.decisions = Collections.unmodifiableMap(decisions);
            this.expireAt = expireAt;
        }
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, DecisionEntry> decisionCache = new ConcurrentHashMap<>();
    private final long defaultTtlMillis;
    private final ScheduledExecutorService cleaner;

    public InMemoryPermissionCacheService() {
        this(resolveDefaultTtlMillis());
    }

    public InMemoryPermissionCacheService(long defaultTtlMillis) {
        long resolved = defaultTtlMillis > 0 ? defaultTtlMillis : resolveDefaultTtlMillis();
        // TTL 限制在 5~15 分钟之间，避免过短或过长导致缓存失效或脏数据
        long min = TimeUnit.MINUTES.toMillis(5);
        long max = TimeUnit.MINUTES.toMillis(15);
        this.defaultTtlMillis = Math.min(Math.max(resolved, min), max);
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "permission-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        // 定时清理过期条目，默认每 1 分钟检查一次
        this.cleaner.scheduleAtFixedRate(this::evictExpiredEntries, this.defaultTtlMillis, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS);
    }

    @Override
    public void putUserPermissions(String userId, Set<Permission> permissions, long ttlMillis) {
        if (userId == null || permissions == null) {
            return;
        }
        long ttl = ttlMillis > 0 ? ttlMillis : defaultTtlMillis;
        long expireAt = System.currentTimeMillis() + ttl;
        cache.put(userId, new Entry(Collections.unmodifiableSet(permissions), expireAt));
    }

    @Override
    public Set<Permission> getUserPermissions(String userId) {
        if (userId == null) {
            return null;
        }
        Entry e = cache.get(userId);
        if (e == null) {
            return null;
        }
        if (e.expireAt < System.currentTimeMillis()) {
            cache.remove(userId);
            return null;
        }
        return e.permissions;
    }

    @Override
    public void putUserPermissionDecisions(String userId, Map<String, Object> decisions, long ttlMillis) {
        if (userId == null || decisions == null) {
            return;
        }
        long ttl = ttlMillis > 0 ? ttlMillis : defaultTtlMillis;
        long expireAt = System.currentTimeMillis() + ttl;
        decisionCache.put(userId, new DecisionEntry(decisions, expireAt));
    }
    
    @Override
    public Map<String, Object> getUserPermissionDecisions(String userId) {
        if (userId == null) {
            return null;
        }
        DecisionEntry e = decisionCache.get(userId);
        if (e == null) {
            return null;
        }
        if (e.expireAt < System.currentTimeMillis()) {
            decisionCache.remove(userId);
            return null;
        }
        return e.decisions;
    }

    @Override
    public void evictUser(String userId) {
        if (userId != null) {
            cache.remove(userId);
            decisionCache.remove(userId);
        }
    }

    @Override
    public void evictAll() {
        cache.clear();
        decisionCache.clear();
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
        decisionCache.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }

    private static long resolveDefaultTtlMillis() {
        String sysProp = System.getProperty("security.permission.cache.ttl-ms");
        String envProp = System.getenv("SECURITY_PERMISSION_CACHE_TTL_MS");
        return parseLongOrDefault(sysProp, parseLongOrDefault(envProp, TimeUnit.MINUTES.toMillis(10)));
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


