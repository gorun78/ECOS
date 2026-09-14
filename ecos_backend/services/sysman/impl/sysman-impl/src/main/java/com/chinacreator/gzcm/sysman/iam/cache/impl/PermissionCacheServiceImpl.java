package com.chinacreator.gzcm.sysman.iam.cache.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;

/**
 * 权限缓存服务的本地内存实现
 * 用于本地开发和联调环境
 * 生产环境建议替换为Redis实现
 */
@Service
public class PermissionCacheServiceImpl implements PermissionCacheService {

    private final Map<String, Set<Permission>> userPermissionsCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> userDecisionsCache = new ConcurrentHashMap<>();

    @Override
    public void putUserPermissions(String userId, Set<Permission> permissions, long ttlMillis) {
        if (userId != null && permissions != null) {
            userPermissionsCache.put(userId, permissions);
        }
    }

    @Override
    public Set<Permission> getUserPermissions(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return userPermissionsCache.getOrDefault(userId, Collections.emptySet());
    }

    @Override
    public void putUserPermissionDecisions(String userId, Map<String, Object> decisions, long ttlMillis) {
        if (userId != null && decisions != null) {
            userDecisionsCache.put(userId, decisions);
        }
    }

    @Override
    public Map<String, Object> getUserPermissionDecisions(String userId) {
        if (userId == null) {
            return Collections.emptyMap();
        }
        return userDecisionsCache.getOrDefault(userId, Collections.emptyMap());
    }

    @Override
    public void evictUser(String userId) {
        if (userId != null) {
            userPermissionsCache.remove(userId);
            userDecisionsCache.remove(userId);
        }
    }

    @Override
    public void evictAll() {
        userPermissionsCache.clear();
        userDecisionsCache.clear();
    }
}
