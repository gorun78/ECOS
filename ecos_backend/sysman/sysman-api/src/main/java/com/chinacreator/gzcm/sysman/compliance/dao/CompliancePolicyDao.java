package com.chinacreator.gzcm.sysman.compliance.dao;

import java.util.List;

import com.chinacreator.gzcm.sysman.compliance.entity.CompliancePolicy;

/**
 * 合规策略DAO接口
 */
public interface CompliancePolicyDao {
    void insert(CompliancePolicy policy) throws Exception;
    void update(CompliancePolicy policy) throws Exception;
    void delete(String policyId) throws Exception;
    CompliancePolicy findById(String policyId) throws Exception;
    List<CompliancePolicy> query(CompliancePolicy condition) throws Exception;
}

