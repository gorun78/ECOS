package com.chinacreator.gzcm.sysman.policy.pap;

import java.util.List;

/**
 * 策略管理点（Policy Administration Point）：管理策略的存储、版本和发布
 */
public interface PolicyAdministrationPoint {

    /**
     * 加载所有活动策略
     *
     * @return 策略列表
     */
    List<PolicyDefinition> loadActivePolicies();

    /**
     * 根据资源加载策略
     *
     * @param resource 资源标识
     * @return 策略列表
     */
    List<PolicyDefinition> loadPoliciesByResource(String resource);

    /**
     * 策略定义接口
     */
    interface PolicyDefinition {
        String getPolicyId();
        String getPolicyName();
        String getResource();
        String getEffect();  // ALLOW / DENY
        Integer getPriority();
        Object getPolicyRule();  // 策略规则（JSON/YAML/Rego等）
    }
}

