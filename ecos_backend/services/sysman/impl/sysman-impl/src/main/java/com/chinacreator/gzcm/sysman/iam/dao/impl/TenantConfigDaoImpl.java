package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.iam.dao.TenantConfigDao;
import com.chinacreator.gzcm.sysman.iam.entity.TenantConfig;

/**
 * 租户配置DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class TenantConfigDaoImpl implements TenantConfigDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/iam/dao/impl/TenantConfig-sql.xml";

    @Autowired
    public TenantConfigDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(TenantConfig config) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertTenantConfig", config);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(TenantConfig config) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateTenantConfig", config);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String configId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("configId", configId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteTenantConfig", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除租户配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TenantConfig> listByTenantId(String tenantId) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "listByTenantId", TenantConfig.class, tenantId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询租户配置列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TenantConfig findByTenantAndKey(String tenantId, String key) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("tenantId", tenantId);
            params.put("key", key);
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findByTenantAndKey", TenantConfig.class, params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("根据租户和键查询配置失败: " + e.getMessage(), e);
        }
    }
}


