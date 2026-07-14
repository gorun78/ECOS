package com.chinacreator.gzcm.runtime.core.compliance.dao.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.compliance.dao.CompliancePolicyDao;
import com.chinacreator.gzcm.sysman.compliance.entity.CompliancePolicy;

/**
 * 合规策略DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class CompliancePolicyDaoImpl implements CompliancePolicyDao {

    private final ISystemDatabaseAccess databaseAccess;
    private static final String SQL_CONFIG_PATH = "com/chinacreator/gzcm/sysman/compliance/dao/impl/CompliancePolicy-sql.xml";

    @Autowired
    public CompliancePolicyDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }

    @Override
    public void insert(CompliancePolicy policy) throws Exception {
        try {
            databaseAccess.executeInsertFromConfig(SQL_CONFIG_PATH, "insertCompliancePolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(CompliancePolicy policy) throws Exception {
        try {
            databaseAccess.executeUpdateFromConfigWithEntity(SQL_CONFIG_PATH, "updateCompliancePolicy", policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String policyId) throws Exception {
        try {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("policyId", policyId);
            databaseAccess.executeUpdateFromConfig(SQL_CONFIG_PATH, "deleteCompliancePolicy", params);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompliancePolicy findById(String policyId) throws Exception {
        try {
            return databaseAccess.queryObjectFromConfig(SQL_CONFIG_PATH, "findById", CompliancePolicy.class, policyId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CompliancePolicy> query(CompliancePolicy condition) throws Exception {
        try {
            return databaseAccess.queryListFromConfig(SQL_CONFIG_PATH, "queryCompliancePolicy", CompliancePolicy.class, condition);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询合规策略列表失败: " + e.getMessage(), e);
        }
    }
}

