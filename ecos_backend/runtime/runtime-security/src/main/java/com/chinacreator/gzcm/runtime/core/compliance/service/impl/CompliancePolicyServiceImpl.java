package com.chinacreator.gzcm.runtime.core.compliance.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.chinacreator.gzcm.sysman.compliance.dao.CompliancePolicyDao;
import com.chinacreator.gzcm.sysman.compliance.entity.CompliancePolicy;
import com.chinacreator.gzcm.sysman.compliance.service.ICompliancePolicyService;

/**
 * 合规策略服务实现
 */
public class CompliancePolicyServiceImpl implements ICompliancePolicyService {

    private final CompliancePolicyDao compliancePolicyDao;

    public CompliancePolicyServiceImpl(CompliancePolicyDao compliancePolicyDao) {
        this.compliancePolicyDao = compliancePolicyDao;
    }

    @Override
    public CompliancePolicy createPolicy(CompliancePolicy policy, String operator) throws ComplianceException {
        try {
            policy.setPolicyId(UUID.randomUUID().toString());
            policy.setStatus(policy.getStatus() != null ? policy.getStatus() : "ACTIVE");
            policy.setCreatedTime(new Date());
            policy.setCreatedBy(operator);
            policy.setUpdatedTime(new Date());
            policy.setUpdatedBy(operator);
            compliancePolicyDao.insert(policy);
            return compliancePolicyDao.findById(policy.getPolicyId());
        } catch (Exception e) {
            throw new ComplianceException("创建合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompliancePolicy updatePolicy(CompliancePolicy policy, String operator) throws ComplianceException {
        try {
            CompliancePolicy existing = compliancePolicyDao.findById(policy.getPolicyId());
            if (existing == null) {
                throw new ComplianceException("合规策略不存在");
            }
            policy.setUpdatedTime(new Date());
            policy.setUpdatedBy(operator);
            compliancePolicyDao.update(policy);
            return compliancePolicyDao.findById(policy.getPolicyId());
        } catch (ComplianceException e) {
            throw e;
        } catch (Exception e) {
            throw new ComplianceException("更新合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deletePolicy(String policyId, String operator) throws ComplianceException {
        try {
            CompliancePolicy existing = compliancePolicyDao.findById(policyId);
            if (existing == null) {
                throw new ComplianceException("合规策略不存在");
            }
            compliancePolicyDao.delete(policyId);
        } catch (ComplianceException e) {
            throw e;
        } catch (Exception e) {
            throw new ComplianceException("删除合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompliancePolicy getPolicy(String policyId) throws ComplianceException {
        try {
            return compliancePolicyDao.findById(policyId);
        } catch (Exception e) {
            throw new ComplianceException("获取合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CompliancePolicy> listPolicies(String policyType, String regulation, String status) throws ComplianceException {
        try {
            CompliancePolicy condition = new CompliancePolicy();
            condition.setPolicyType(policyType);
            condition.setRegulation(regulation);
            condition.setStatus(status);
            return compliancePolicyDao.query(condition);
        } catch (Exception e) {
            throw new ComplianceException("查询合规策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkCompliance(String policyType, String context) throws ComplianceException {
        try {
            // 查询活动策略
            List<CompliancePolicy> policies = listPolicies(policyType, null, "ACTIVE");
            if (policies.isEmpty()) {
                // 无策略，默认通过
                return true;
            }

            // TODO: 解析策略规则并评估上下文
            // 简化实现：有活动策略即通过
            return true;
        } catch (Exception e) {
            throw new ComplianceException("合规检查失败: " + e.getMessage(), e);
        }
    }
}

