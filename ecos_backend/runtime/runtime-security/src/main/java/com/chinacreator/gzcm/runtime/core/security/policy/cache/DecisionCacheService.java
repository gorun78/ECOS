package com.chinacreator.gzcm.runtime.core.security.policy.cache;

import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;

/**
 * 决策结果缓存服务接口
 * 用于缓存策略评估的决策结果，提升性能
 */
public interface DecisionCacheService {
    
    /**
     * 获取缓存的决策结果
     * 
     * @param cacheKey 缓存键
     * @return 决策结果，如果不存在或已过期则返回null
     */
    PolicyDecision get(String cacheKey);
    
    /**
     * 缓存决策结果
     * 
     * @param cacheKey 缓存键
     * @param decision 决策结果
     * @param ttlMillis TTL（毫秒），如果<=0则使用默认TTL
     */
    void put(String cacheKey, PolicyDecision decision, long ttlMillis);
    
    /**
     * 使缓存失效
     * 
     * @param cacheKey 缓存键，支持通配符（如 userId:*）
     */
    void evict(String cacheKey);
    
    /**
     * 清空所有缓存
     */
    void evictAll();
    
    /**
     * 生成缓存键
     * 
     * @param userId 用户ID
     * @param resource 资源
     * @param action 操作
     * @param contextHash 上下文哈希（可选）
     * @return 缓存键
     */
    default String generateCacheKey(String userId, String resource, String action, String contextHash) {
        StringBuilder key = new StringBuilder();
        key.append(userId != null ? userId : "null");
        key.append(":");
        key.append(resource != null ? resource : "null");
        key.append(":");
        key.append(action != null ? action : "null");
        if (contextHash != null && !contextHash.isEmpty()) {
            key.append(":");
            key.append(contextHash);
        }
        return key.toString();
    }
}
