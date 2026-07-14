package com.chinacreator.gzcm.sysman.iam.dao;

import java.util.Map;

/**
 * 租户资源使用情况DAO接口
 */
public interface TenantResourceUsageDao {
    /**
     * 获取租户资源使用情况
     * @param tenantId 租户ID
     * @return 资源使用情况Map，key为资源类型（USERS, STORAGE, API_CALLS等），value为已使用数量
     * @throws Exception
     */
    Map<String, Long> getUsage(String tenantId) throws Exception;
    
    /**
     * 更新租户资源使用量
     * @param tenantId 租户ID
     * @param resourceType 资源类型
     * @param delta 变化量（正数表示增加，负数表示减少）
     * @throws Exception
     */
    void updateUsage(String tenantId, String resourceType, Long delta) throws Exception;
    
    /**
     * 重置租户资源使用量（用于每日重置API调用次数等）
     * @param tenantId 租户ID
     * @param resourceType 资源类型
     * @throws Exception
     */
    void resetUsage(String tenantId, String resourceType) throws Exception;
}

