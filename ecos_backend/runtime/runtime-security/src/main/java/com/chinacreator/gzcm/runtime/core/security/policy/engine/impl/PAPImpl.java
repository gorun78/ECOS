package com.chinacreator.gzcm.runtime.core.security.policy.engine.impl;


import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.policy.engine.PAP;

/**
 * Policy Administration Point 实现
 * 基于ABAC策略服务实现策略管理
 */
public class PAPImpl implements PAP {
    
    private final IAbacPolicyService abacPolicyService;
    
    public PAPImpl(IAbacPolicyService abacPolicyService) {
        this.abacPolicyService = abacPolicyService;
    }
    
    @Override
    public AbacPolicy createPolicy(AbacPolicy policy, String operator) throws PolicyAdministrationException {
        try {
            return abacPolicyService.createPolicy(policy);
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyAdministrationException("创建策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyAdministrationException("创建策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AbacPolicy updatePolicy(AbacPolicy policy, String operator) throws PolicyAdministrationException {
        try {
            return abacPolicyService.updatePolicy(policy);
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyAdministrationException("更新策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyAdministrationException("更新策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deletePolicy(String policyId, String operator) throws PolicyAdministrationException {
        try {
            abacPolicyService.deletePolicy(policyId);
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyAdministrationException("删除策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyAdministrationException("删除策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AbacPolicy getPolicy(String policyId) throws PolicyAdministrationException {
        try {
            return abacPolicyService.getPolicy(policyId);
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyAdministrationException("获取策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyAdministrationException("获取策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<AbacPolicy> listPolicies() throws PolicyAdministrationException {
        try {
            return abacPolicyService.listPolicies();
        } catch (IAbacPolicyService.AbacException e) {
            throw new PolicyAdministrationException("列出策略失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyAdministrationException("列出策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<AbacPolicy> queryPolicies(Map<String, Object> condition) throws PolicyAdministrationException {
        try {
            // 如果策略服务支持条件查询，使用条件查询；否则返回所有策略后过滤
            List<AbacPolicy> all = listPolicies();
            // 这里可以添加过滤逻辑
            return all;
        } catch (Exception e) {
            throw new PolicyAdministrationException("查询策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void enablePolicy(String policyId, String operator) throws PolicyAdministrationException {
        try {
            AbacPolicy policy = getPolicy(policyId);
            if (policy != null) {
                // 假设策略有状态字段，这里需要根据实际实体结构调整
                // policy.setStatus("ACTIVE");
                updatePolicy(policy, operator);
            }
        } catch (Exception e) {
            throw new PolicyAdministrationException("启用策略异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void disablePolicy(String policyId, String operator) throws PolicyAdministrationException {
        try {
            AbacPolicy policy = getPolicy(policyId);
            if (policy != null) {
                // 假设策略有状态字段，这里需要根据实际实体结构调整
                // policy.setStatus("INACTIVE");
                updatePolicy(policy, operator);
            }
        } catch (Exception e) {
            throw new PolicyAdministrationException("禁用策略异常: " + e.getMessage(), e);
        }
    }
}

