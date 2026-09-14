package com.chinacreator.gzcm.sysman.iam.cache;

import java.util.Map;
import java.util.Set;

import com.chinacreator.gzcm.sysman.iam.entity.Permission;

/**
 * 用户权限缓存抽象，后续可替换为 Redis 实现。
 */
public interface PermissionCacheService {

    void putUserPermissions(String userId, Set<Permission> permissions, long ttlMillis);

    Set<Permission> getUserPermissions(String userId);
    
    /**
     * 存储用户权限决策（包括effect和priority）
     * @param userId 用户ID
     * @param decisions 权限决策Map，key为permissionId，value为决策信息（effect和priority）
     * @param ttlMillis TTL（毫秒），-1表示使用默认TTL
     */
    void putUserPermissionDecisions(String userId, Map<String, Object> decisions, long ttlMillis);
    
    /**
     * 获取用户权限决策
     * @param userId 用户ID
     * @return 权限决策Map，key为permissionId，value为决策信息（effect和priority）
     */
    Map<String, Object> getUserPermissionDecisions(String userId);

    void evictUser(String userId);

    void evictAll();
}


