package com.chinacreator.gzcm.sysman.iam.cache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 基于 Caffeine 的本地权限缓存实现。
 * 自动 TTL + 容量上限，消除手写过期清理和内存泄漏风险。
 */
public class InMemoryPermissionCacheService implements PermissionCacheService {

    private static class Entry {
        final Set<Permission> permissions;

        Entry(Set<Permission> permissions) {
            this.permissions = Collections.unmodifiableSet(permissions);
        }
    }

    private static class DecisionEntry {
        final Map<String, Object> decisions;

        DecisionEntry(Map<String, Object> decisions) {
            this.decisions = Collections.unmodifiableMap(decisions);
        }
    }

    private final Cache<String, Entry> cache;
    private final Cache<String, DecisionEntry> decisionCache;
    private final long defaultTtlMillis;

    public InMemoryPermissionCacheService() {
        this(resolveDefaultTtlMillis());
    }

    public InMemoryPermissionCacheService(long defaultTtlMillis) {
        long resolved = defaultTtlMillis > 0 ? defaultTtlMillis : resolveDefaultTtlMillis();
        // TTL 限制在 5~15 分钟之间，避免过短或过长导致缓存失效或脏数据
        long min = TimeUnit.MINUTES.toMillis(5);
        long max = TimeUnit.MINUTES.toMillis(15);
        this.defaultTtlMillis = Math.min(Math.max(resolved, min), max);

        this.cache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(this.defaultTtlMillis, TimeUnit.MILLISECONDS)
            .build();

        this.decisionCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(this.defaultTtlMillis, TimeUnit.MILLISECONDS)
            .build();
    }

    @Override
    public void putUserPermissions(String userId, Set<Permission> permissions, long ttlMillis) {
        if (userId == null || permissions == null) {
            return;
        }
        cache.put(userId, new Entry(permissions));
    }

    @Override
    public Set<Permission> getUserPermissions(String userId) {
        if (userId == null) {
            return null;
        }
        Entry e = cache.getIfPresent(userId);
        if (e == null) {
            return null;
        }
        return e.permissions;
    }

    @Override
    public void putUserPermissionDecisions(String userId, Map<String, Object> decisions, long ttlMillis) {
        if (userId == null || decisions == null) {
            return;
        }
        decisionCache.put(userId, new DecisionEntry(decisions));
    }

    @Override
    public Map<String, Object> getUserPermissionDecisions(String userId) {
        if (userId == null) {
            return null;
        }
        DecisionEntry e = decisionCache.getIfPresent(userId);
        if (e == null) {
            return null;
        }
        return e.decisions;
    }

    @Override
    public void evictUser(String userId) {
        if (userId != null) {
            cache.invalidate(userId);
            decisionCache.invalidate(userId);
        }
    }

    @Override
    public void evictAll() {
        cache.invalidateAll();
        decisionCache.invalidateAll();
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
