package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.dao.TenantDao;
import com.chinacreator.gzcm.sysman.iam.dao.TenantResourceUsageDao;
import com.chinacreator.gzcm.sysman.iam.entity.Tenant;
import com.chinacreator.gzcm.sysman.iam.service.ITenantService;

@Service
public class TenantServiceImpl implements ITenantService {

    private final TenantDao tenantDao;
    private final TenantResourceUsageDao resourceUsageDao;

    @Autowired
    public TenantServiceImpl(TenantDao tenantDao) {
        this(tenantDao, null);
    }

    public TenantServiceImpl(TenantDao tenantDao, TenantResourceUsageDao resourceUsageDao) {
        this.tenantDao = tenantDao;
        this.resourceUsageDao = resourceUsageDao;
    }

    @Override
    public Tenant createTenant(Tenant tenant, String operator) throws TenantException {
        try {
            if (tenant.getTenantCode() != null && tenantDao.findByCode(tenant.getTenantCode()) != null) {
                throw new TenantException("租户编码已存在");
            }
            tenant.setTenantId(tenant.getTenantId() == null ? UUID.randomUUID().toString() : tenant.getTenantId());
            if (tenant.getStatus() == null) {
                tenant.setStatus("ACTIVE");
            }
            Date now = new Date();
            tenant.setCreatedTime(now);
            tenant.setUpdatedTime(now);
            tenant.setCreatedBy(operator);
            tenant.setUpdatedBy(operator);
            tenantDao.insert(tenant);
            return tenantDao.findById(tenant.getTenantId());
        } catch (TenantException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantException("创建租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Tenant updateTenant(Tenant tenant, String operator) throws TenantException {
        try {
            Tenant existing = tenantDao.findById(tenant.getTenantId());
            if (existing == null) {
                throw new TenantException("租户不存在");
            }
            if (tenant.getTenantName() != null) existing.setTenantName(tenant.getTenantName());
            if (tenant.getTenantCode() != null) existing.setTenantCode(tenant.getTenantCode());
            if (tenant.getDescription() != null) existing.setDescription(tenant.getDescription());
            if (tenant.getStatus() != null) existing.setStatus(tenant.getStatus());
            if (tenant.getMaxUsers() != null) existing.setMaxUsers(tenant.getMaxUsers());
            if (tenant.getMaxConcurrentUsers() != null) existing.setMaxConcurrentUsers(tenant.getMaxConcurrentUsers());
            if (tenant.getMaxStorage() != null) existing.setMaxStorage(tenant.getMaxStorage());
            if (tenant.getMaxApiCallsPerDay() != null) existing.setMaxApiCallsPerDay(tenant.getMaxApiCallsPerDay());
            if (tenant.getMaxOrganizations() != null) existing.setMaxOrganizations(tenant.getMaxOrganizations());
            if (tenant.getMaxRoles() != null) existing.setMaxRoles(tenant.getMaxRoles());
            if (tenant.getIsolationMode() != null) existing.setIsolationMode(tenant.getIsolationMode());
            if (tenant.getDatabaseName() != null) existing.setDatabaseName(tenant.getDatabaseName());
            if (tenant.getSchemaName() != null) existing.setSchemaName(tenant.getSchemaName());
            if (tenant.getTablePrefix() != null) existing.setTablePrefix(tenant.getTablePrefix());
            existing.setUpdatedBy(operator);
            existing.setUpdatedTime(new Date());
            tenantDao.update(existing);
            return tenantDao.findById(existing.getTenantId());
        } catch (TenantException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantException("更新租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteTenant(String tenantId, String operator) throws TenantException {
        try {
            tenantDao.softDelete(tenantId);
        } catch (Exception e) {
            throw new TenantException("删除租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Tenant getTenant(String tenantId) throws TenantException {
        try {
            return tenantDao.findById(tenantId);
        } catch (Exception e) {
            throw new TenantException("获取租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Tenant> listTenants(String keyword, String status) throws TenantException {
        try {
            Map<String, Object> cond = new java.util.HashMap<>();
            cond.put("keyword", keyword);
            cond.put("status", status);
            return tenantDao.query(cond);
        } catch (Exception e) {
            throw new TenantException("查询租户列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkQuota(String tenantId, String resourceType, Long requestedAmount) throws TenantException {
        try {
            Tenant tenant = tenantDao.findById(tenantId);
            if (tenant == null) {
                throw new TenantException("租户不存在: " + tenantId);
            }
            
            Map<String, Long> usage = getResourceUsage(tenantId);
            Long currentUsage = usage.getOrDefault(resourceType, 0L);
            Long maxQuota = getMaxQuota(tenant, resourceType);
            
            if (maxQuota == null || maxQuota <= 0) {
                return true; // 无限制
            }
            
            return (currentUsage + requestedAmount) <= maxQuota;
        } catch (TenantException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantException("检查配额失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> getResourceUsage(String tenantId) throws TenantException {
        try {
            Map<String, Long> usage = new HashMap<>();
            
            if (resourceUsageDao != null) {
                // 从数据库获取资源使用情况
                usage = resourceUsageDao.getUsage(tenantId);
            } else {
                // 如果没有DAO，返回默认值（实际应该从数据库查询）
                usage.put("USERS", 0L);
                usage.put("STORAGE", 0L);
                usage.put("API_CALLS", 0L);
                usage.put("ORGANIZATIONS", 0L);
                usage.put("ROLES", 0L);
            }
            
            return usage;
        } catch (Exception e) {
            throw new TenantException("获取资源使用情况失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateResourceUsage(String tenantId, String resourceType, Long delta) throws TenantException {
        try {
            if (resourceUsageDao != null) {
                resourceUsageDao.updateUsage(tenantId, resourceType, delta);
            }
            // 如果没有DAO，这里应该记录日志或抛出异常
        } catch (Exception e) {
            throw new TenantException("更新资源使用量失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateIsolationConfig(String tenantId) throws TenantException {
        try {
            Tenant tenant = tenantDao.findById(tenantId);
            if (tenant == null) {
                throw new TenantException("租户不存在: " + tenantId);
            }
            
            String isolationMode = tenant.getIsolationMode();
            if (isolationMode == null || isolationMode.isEmpty()) {
                return false; // 必须配置隔离模式
            }
            
            switch (isolationMode) {
                case "DATABASE":
                    return tenant.getDatabaseName() != null && !tenant.getDatabaseName().isEmpty();
                case "SCHEMA":
                    return tenant.getSchemaName() != null && !tenant.getSchemaName().isEmpty();
                case "TABLE_PREFIX":
                    return tenant.getTablePrefix() != null && !tenant.getTablePrefix().isEmpty();
                default:
                    return false; // 未知的隔离模式
            }
        } catch (TenantException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantException("验证隔离配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getIsolationIdentifier(String tenantId) throws TenantException {
        try {
            Tenant tenant = tenantDao.findById(tenantId);
            if (tenant == null) {
                throw new TenantException("租户不存在: " + tenantId);
            }
            
            Map<String, String> identifier = new HashMap<>();
            identifier.put("tenantId", tenantId);
            identifier.put("isolationMode", tenant.getIsolationMode());
            identifier.put("databaseName", tenant.getDatabaseName());
            identifier.put("schemaName", tenant.getSchemaName());
            identifier.put("tablePrefix", tenant.getTablePrefix());
            
            return identifier;
        } catch (TenantException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantException("获取隔离标识失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取指定资源类型的最大配额
     */
    private Long getMaxQuota(Tenant tenant, String resourceType) {
        switch (resourceType) {
            case "USERS":
                return tenant.getMaxUsers() != null ? tenant.getMaxUsers().longValue() : null;
            case "STORAGE":
                return tenant.getMaxStorage();
            case "API_CALLS":
                return tenant.getMaxApiCallsPerDay();
            case "ORGANIZATIONS":
                return tenant.getMaxOrganizations() != null ? tenant.getMaxOrganizations().longValue() : null;
            case "ROLES":
                return tenant.getMaxRoles() != null ? tenant.getMaxRoles().longValue() : null;
            default:
                return null;
        }
    }
}


