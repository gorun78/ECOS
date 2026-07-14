package com.chinacreator.gzcm.sysman.iam.service;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.iam.entity.Tenant;

public interface ITenantService {

    Tenant createTenant(Tenant tenant, String operator) throws TenantException;

    Tenant updateTenant(Tenant tenant, String operator) throws TenantException;

    void deleteTenant(String tenantId, String operator) throws TenantException;

    Tenant getTenant(String tenantId) throws TenantException;

    List<Tenant> listTenants(String keyword, String status) throws TenantException;
    
    /**
     * 检查租户资源配额
     * @param tenantId 租户ID
     * @param resourceType 资源类型：USERS, STORAGE, API_CALLS, ORGANIZATIONS, ROLES
     * @param requestedAmount 请求的资源数量
     * @return true表示配额充足，false表示超出配额
     * @throws TenantException
     */
    boolean checkQuota(String tenantId, String resourceType, Long requestedAmount) throws TenantException;
    
    /**
     * 获取租户资源使用情况
     * @param tenantId 租户ID
     * @return 资源使用情况Map，key为资源类型，value为已使用数量
     * @throws TenantException
     */
    Map<String, Long> getResourceUsage(String tenantId) throws TenantException;
    
    /**
     * 更新租户资源使用量
     * @param tenantId 租户ID
     * @param resourceType 资源类型
     * @param delta 变化量（正数表示增加，负数表示减少）
     * @throws TenantException
     */
    void updateResourceUsage(String tenantId, String resourceType, Long delta) throws TenantException;
    
    /**
     * 验证租户隔离配置
     * @param tenantId 租户ID
     * @return true表示隔离配置有效
     * @throws TenantException
     */
    boolean validateIsolationConfig(String tenantId) throws TenantException;
    
    /**
     * 获取租户数据隔离标识（用于数据查询时的隔离）
     * @param tenantId 租户ID
     * @return 隔离标识Map，包含databaseName, schemaName, tablePrefix等
     * @throws TenantException
     */
    Map<String, String> getIsolationIdentifier(String tenantId) throws TenantException;

    class TenantException extends Exception {
        private static final long serialVersionUID = 1L;
        public TenantException(String message) { super(message); }
        public TenantException(String message, Throwable cause) { super(message, cause); }
    }
}


