package com.chinacreator.gzcm.runtime.core.security.abac.dao.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.abac.dao.AbacPolicyDao;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;

/**
 * ABAC策略DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class AbacPolicyDaoImpl implements AbacPolicyDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/abac/dao/impl/AbacPolicy-sql.xml";

    @Autowired
    public AbacPolicyDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(AbacPolicy policy) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertPolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入ABAC策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(AbacPolicy policy) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updatePolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新ABAC策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String policyId) throws Exception {
        try {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("policyId", policyId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deletePolicy", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除ABAC策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AbacPolicy findById(String policyId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", AbacPolicy.class, policyId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询ABAC策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AbacPolicy> findAll() throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "findAll", AbacPolicy.class, null);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询所有ABAC策略失败: " + e.getMessage(), e);
        }
    }
}


