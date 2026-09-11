package com.chinacreator.gzcm.sysman.compliance.service;

import java.util.List;

import com.chinacreator.gzcm.sysman.compliance.entity.CompliancePolicy;

/**
 * 合规策略服务接口
 */
public interface ICompliancePolicyService {

    /**
     * 创建合规策略
     */
    CompliancePolicy createPolicy(CompliancePolicy policy, String operator) throws ComplianceException;

    /**
     * 更新合规策略
     */
    CompliancePolicy updatePolicy(CompliancePolicy policy, String operator) throws ComplianceException;

    /**
     * 删除合规策略
     */
    void deletePolicy(String policyId, String operator) throws ComplianceException;

    /**
     * 获取合规策略
     */
    CompliancePolicy getPolicy(String policyId) throws ComplianceException;

    /**
     * 查询合规策略列表
     */
    List<CompliancePolicy> listPolicies(String policyType, String regulation, String status) throws ComplianceException;

    /**
     * 应用合规策略检查
     *
     * @param policyType 策略类型
     * @param context    检查上下文（JSON格式）
     * @return true 通过，false 不通过
     */
    boolean checkCompliance(String policyType, String context) throws ComplianceException;

    /**
     * 合规异常
     */
    class ComplianceException extends Exception {
        private static final long serialVersionUID = 1L;

        public ComplianceException(String message) {
            super(message);
        }

        public ComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

