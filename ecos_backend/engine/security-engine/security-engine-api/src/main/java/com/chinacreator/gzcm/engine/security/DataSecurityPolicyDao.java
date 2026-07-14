package com.chinacreator.gzcm.engine.security;

import java.util.List;

import com.chinacreator.gzcm.sysman.security.policy.entity.DataSecurityPolicy;

/**
 * 数据安全策略DAO接口
 */
public interface DataSecurityPolicyDao {
    
    void insert(DataSecurityPolicy policy) throws Exception;
    
    void update(DataSecurityPolicy policy) throws Exception;
    
    void delete(String policyId) throws Exception;
    
    DataSecurityPolicy findById(String policyId) throws Exception;
    
    List<DataSecurityPolicy> listByCondition(String policyType, String scope, String scopeId, Boolean enabled) throws Exception;
    
    /**
     * 查询适用的策略（按优先级排序）
     */
    List<DataSecurityPolicy> findApplicablePolicies(String policyType, String scopeId) throws Exception;
}
