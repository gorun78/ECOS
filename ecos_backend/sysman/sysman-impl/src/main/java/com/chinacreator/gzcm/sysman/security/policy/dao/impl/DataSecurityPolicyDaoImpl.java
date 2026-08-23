package com.chinacreator.gzcm.sysman.security.policy.dao.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.sysman.security.policy.dao.DataSecurityPolicyDao;
import com.chinacreator.gzcm.sysman.security.policy.entity.DataSecurityPolicy;

/**
 * 数据安全策略DAO实现
 * 通过Runtime的系统数据库访问接口完成数据库操作
 */
@Repository
public class DataSecurityPolicyDaoImpl implements DataSecurityPolicyDao {
    
    private final ISystemDatabaseAccess databaseAccess;
    private static final String TABLE_NAME = "td_data_security_policy";
    
    @Autowired
    public DataSecurityPolicyDaoImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void insert(DataSecurityPolicy policy) throws Exception {
        try {
            databaseAccess.insert(TABLE_NAME, policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("插入数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void update(DataSecurityPolicy policy) throws Exception {
        try {
            databaseAccess.update(TABLE_NAME, policy);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("更新数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void delete(String policyId) throws Exception {
        try {
            databaseAccess.delete(TABLE_NAME, policyId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("删除数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DataSecurityPolicy findById(String policyId) throws Exception {
        try {
            return databaseAccess.findById(TABLE_NAME, DataSecurityPolicy.class, policyId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DataSecurityPolicy> listByCondition(String policyType, String scope, String scopeId, Boolean enabled) throws Exception {
        try {
            // 构建查询条件
            Map<String, Object> condition = new HashMap<>();
            if (policyType != null) {
                condition.put("policy_type", policyType);
            }
            if (scope != null) {
                condition.put("scope", scope);
            }
            if (scopeId != null) {
                condition.put("scope_id", scopeId);
            }
            if (enabled != null) {
                condition.put("enabled", enabled);
            }
            
            // 使用SQL查询以支持ORDER BY
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME);
            List<Object> params = new ArrayList<>();
            
            if (!condition.isEmpty()) {
                sql.append(" WHERE 1=1");
                if (policyType != null) {
                    sql.append(" AND policy_type = ?");
                    params.add(policyType);
                }
                if (scope != null) {
                    sql.append(" AND scope = ?");
                    params.add(scope);
                }
                if (scopeId != null) {
                    sql.append(" AND scope_id = ?");
                    params.add(scopeId);
                }
                if (enabled != null) {
                    sql.append(" AND enabled = ?");
                    params.add(enabled);
                }
            }
            
            sql.append(" ORDER BY priority DESC, created_time DESC");
            
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql.toString(), params.toArray());
            return convertToEntityList(results);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询数据安全策略列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DataSecurityPolicy> findApplicablePolicies(String policyType, String scopeId) throws Exception {
        try {
            // 查询策略：ALL范围 + 指定scopeId范围 + 指定policyType
            String sql = "SELECT * FROM " + TABLE_NAME + " " +
                    "WHERE policy_type = ? AND enabled = 1 AND status = 'ACTIVE' " +
                    "AND (scope = 'ALL' OR (scope = 'TENANT' AND scope_id = ?) OR (scope = 'RESOURCE' AND scope_id = ?)) " +
                    "ORDER BY priority DESC, created_time DESC";
            
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, policyType, scopeId, scopeId);
            return convertToEntityList(results);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            throw new Exception("查询适用的数据安全策略失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 将查询结果转换为实体列表
     */
    private List<DataSecurityPolicy> convertToEntityList(List<Map<String, Object>> results) {
        List<DataSecurityPolicy> policies = new ArrayList<>();
        if (results == null) {
            return policies;
        }
        
        for (Map<String, Object> row : results) {
            DataSecurityPolicy policy = new DataSecurityPolicy();
            policy.setPolicyId((String) row.get("policy_id"));
            policy.setPolicyName((String) row.get("policy_name"));
            policy.setPolicyType((String) row.get("policy_type"));
            policy.setStatus((String) row.get("status"));
            policy.setPolicyContent((String) row.get("policy_content"));
            policy.setScope((String) row.get("scope"));
            policy.setScopeId((String) row.get("scope_id"));
            if (row.get("priority") != null) {
                policy.setPriority(((Number) row.get("priority")).intValue());
            }
            if (row.get("enabled") != null) {
                policy.setEnabled(((Number) row.get("enabled")).intValue() == 1);
            }
            policy.setCreatedTime((java.util.Date) row.get("created_time"));
            policy.setCreatedBy((String) row.get("created_by"));
            policy.setUpdatedTime((java.util.Date) row.get("updated_time"));
            policy.setUpdatedBy((String) row.get("updated_by"));
            policy.setDescription((String) row.get("description"));
            policies.add(policy);
        }
        return policies;
    }
}

