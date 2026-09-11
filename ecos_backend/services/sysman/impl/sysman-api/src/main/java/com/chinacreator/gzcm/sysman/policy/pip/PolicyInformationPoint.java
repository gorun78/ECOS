package com.chinacreator.gzcm.sysman.policy.pip;

import java.util.Map;

/**
 * 策略信息点（Policy Information Point）：提供策略评估所需的属性信息
 */
public interface PolicyInformationPoint {

    /**
     * 获取用户属性
     *
     * @param userId 用户ID
     * @return 用户属性Map
     */
    Map<String, Object> getUserAttributes(String userId);

    /**
     * 获取资源属性
     *
     * @param resourceId 资源ID
     * @return 资源属性Map
     */
    Map<String, Object> getResourceAttributes(String resourceId);

    /**
     * 获取环境属性（时间、IP、设备等）
     *
     * @return 环境属性Map
     */
    Map<String, Object> getEnvironmentAttributes();

    /**
     * 获取租户属性
     *
     * @param tenantId 租户ID
     * @return 租户属性Map
     */
    Map<String, Object> getTenantAttributes(String tenantId);
}

