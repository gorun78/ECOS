package com.chinacreator.gzcm.runtime.core.security.policy.pap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.policy.pap.PolicyAdministrationPoint;
import com.chinacreator.gzcm.runtime.core.security.policy.cache.DecisionCacheService;

/**
 * 策略管理点实现：从ABAC策略服务加载策略
 * P1-2: 策略变更时主动清除决策缓存
 */
public class PolicyAdministrationPointImpl implements PolicyAdministrationPoint {

    private final IAbacPolicyService abacPolicyService;
    private final DecisionCacheService decisionCache;

    public PolicyAdministrationPointImpl(IAbacPolicyService abacPolicyService, DecisionCacheService decisionCache) {
        this.abacPolicyService = abacPolicyService;
        this.decisionCache = decisionCache;
    }

    /**
     * P1-2: 策略变更后清除全部决策缓存，防止已撤销权限仍然生效
     */
    public void onPolicyChanged() {
        if (decisionCache != null) {
            decisionCache.evictAll();
        }
    }

    @Override
    public List<PolicyDefinition> loadActivePolicies() {
        try {
            List<AbacPolicy> abacPolicies = abacPolicyService.listPolicies();
            return abacPolicies.stream()
                    .map(this::convertToPolicyDefinition)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public List<PolicyDefinition> loadPoliciesByResource(String resource) {
        try {
            List<AbacPolicy> abacPolicies = abacPolicyService.listPolicies();
            return abacPolicies.stream()
                    .filter(p -> matchesResource(p, resource))
                    .map(this::convertToPolicyDefinition)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private PolicyDefinition convertToPolicyDefinition(AbacPolicy abacPolicy) {
        return new PolicyDefinition() {
            @Override
            public String getPolicyId() {
                return abacPolicy.getPolicyId();
            }

            @Override
            public String getPolicyName() {
                return abacPolicy.getPolicyName();
            }

            @Override
            public String getResource() {
                // 从resourceCondition中提取资源标识（简化实现）
                return abacPolicy.getResourceCondition();
            }

            @Override
            public String getEffect() {
                return abacPolicy.getEffect();
            }

            @Override
            public Integer getPriority() {
                return abacPolicy.getPriority();
            }

            @Override
            public Object getPolicyRule() {
                // 返回ABAC策略对象作为规则
                return abacPolicy;
            }
        };
    }

    private boolean matchesResource(AbacPolicy policy, String resource) {
        // 简化匹配：检查resourceCondition是否包含资源标识
        String resourceCondition = policy.getResourceCondition();
        return resourceCondition != null && resourceCondition.contains(resource);
    }
}

