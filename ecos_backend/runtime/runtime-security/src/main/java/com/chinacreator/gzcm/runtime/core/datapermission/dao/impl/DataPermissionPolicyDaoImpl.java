package com.chinacreator.gzcm.runtime.core.datapermission.dao.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.datapermission.dao.DataPermissionPolicyDao;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;

/**
 * 数据权限策略DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class DataPermissionPolicyDaoImpl implements DataPermissionPolicyDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/datapermission/dao/impl/DataPermissionPolicy-sql.xml";

    @Autowired
    public DataPermissionPolicyDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(DataPermissionPolicy policy) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertPolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(DataPermissionPolicy policy) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updatePolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String policyId) throws Exception {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("policyId", policyId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deletePolicy", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public DataPermissionPolicy findById(String policyId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", DataPermissionPolicy.class, policyId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询数据权限策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DataPermissionPolicy> query(Map<String, Object> condition) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryPolicies", DataPermissionPolicy.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询数据权限策略列表失败: " + e.getMessage(), e);
        }
    }
}


