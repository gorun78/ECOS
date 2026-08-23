package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.TenantDao;
import com.chinacreator.gzcm.sysman.iam.entity.Tenant;

/**
 * 租户DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class TenantDaoImpl implements TenantDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/Tenant-sql.xml";

    @Autowired
    public TenantDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(Tenant tenant) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertTenant", tenant);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Tenant tenant) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateTenant", tenant);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void softDelete(String tenantId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", tenantId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "softDeleteTenant", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("软删除租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Tenant findById(String tenantId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", Tenant.class, tenantId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Tenant findByCode(String tenantCode) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByCode", Tenant.class, tenantCode);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据编码查询租户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Tenant> query(Map<String, Object> condition) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryTenants", Tenant.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询租户列表失败: " + e.getMessage(), e);
        }
    }
}


