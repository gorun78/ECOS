package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.OrganizationDao;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.entity.OrgPermission;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;

/**
 * 机构DAO实现类
 * 通过Runtime的系统数据库访问接口完成数据库操作
 * 
 * @author CDRC Security Team
 */
@Repository
public class OrganizationDaoImpl implements OrganizationDao {
    
    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/Organization-sql.xml";
    
    @Autowired
    public OrganizationDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void createOrganization(Organization organization) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "createOrganization", organization);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("创建机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Organization getOrganization(String orgId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "getOrganization", Organization.class, orgId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void updateOrganization(Organization organization) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateOrganization", organization);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteOrganization(String orgId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("orgId", orgId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteOrganization", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Organization> getChildren(String parentOrgId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "getChildren", Organization.class, parentOrgId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询子机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Organization> listOrganizations(String orgType, String status) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("orgType", orgType);
            params.put("status", status);
            Object queryParams = hasAnyValue(params) ? params : new Object[0];
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "listOrganizations", Organization.class, queryParams);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询机构列表失败: " + e.getMessage(), e);
        }
    }
    
    private boolean hasAnyValue(Map<String, Object> map) {
        if (map == null) return false;
        for (Object v : map.values()) {
            if (v != null && !String.valueOf(v).isEmpty()) return true;
        }
        return false;
    }
    
    @Override
    public boolean hasChildren(String orgId) throws Exception {
        try {
            Integer count = databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "hasChildren", Integer.class, orgId);
            return count != null && count > 0;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("检查是否有子机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void assignUserToOrg(UserOrganization userOrg) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "assignUserToOrg", userOrg);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("分配用户到机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void removeUserFromOrg(String userId, String orgId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("orgId", orgId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "removeUserFromOrg", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("从机构移除用户失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Organization> getUserOrganizations(String userId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "getUserOrganizations", Organization.class, userId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询用户机构列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * #142/B4-3e-80 只增（D-B4-23 属主侧读回）：原样交付关联行，含 {@code is_primary}。
     * 不吞空结果、不在 DAO 里筛主机构——多重性判定属裁决侧，DAO 折叠任何一种都会丢信息。
     */
    @Override
    public List<UserOrganization> getUserOrganizationAffiliations(String userId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH,
                    "getUserOrganizationAffiliations", UserOrganization.class, userId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询用户机构关联行失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void setPrimaryOrg(String userId, String orgId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("orgId", orgId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "setPrimaryOrg", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("设置主机构失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void createOrgPermission(OrgPermission permission) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "createOrgPermission", permission);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("创建机构权限失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<OrgPermission> getOrgPermissions(String orgId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "getOrgPermissions", OrgPermission.class, orgId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询机构权限列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteOrgPermission(String permissionId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("permissionId", permissionId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteOrgPermission", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除机构权限失败: " + e.getMessage(), e);
        }
    }
}

