package com.chinacreator.gzcm.runtime.core.security.policy.engine.impl;


import java.util.HashMap;
import java.util.Map;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.policy.engine.PDP;
import com.chinacreator.gzcm.sysman.policy.engine.PEP;

/**
 * Policy Enforcement Point 实现
 * 在访问点执行策略决策
 */
public class PEPImpl implements PEP {
    
    private final PDP pdp;
    
    public PEPImpl(PDP pdp) {
        this.pdp = pdp;
    }
    
    @Override
    public boolean enforce(AbacContext context) throws PolicyEnforcementException {
        try {
            PDP.Decision decision = pdp.evaluate(context);
            return decision == PDP.Decision.PERMIT;
        } catch (PDP.PolicyEvaluationException e) {
            throw new PolicyEnforcementException("策略执行失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEnforcementException("策略执行异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public EnforcementResult enforceWithDetails(AbacContext context) throws PolicyEnforcementException {
        try {
            PDP.DecisionResult decisionResult = pdp.evaluateWithDetails(context);
            
            EnforcementResult result = new EnforcementResult(
                decisionResult.getDecision() == PDP.Decision.PERMIT,
                decisionResult.getDecision()
            );
            
            result.setReason(decisionResult.getReason());
            // 注意：EnforcementResult没有policyId字段，如果需要可以添加到attributes中
            if (decisionResult.getPolicyId() != null && result.getAttributes() != null) {
                result.getAttributes().put("policyId", decisionResult.getPolicyId());
            }
            
            // 复制属性
            if (decisionResult.getAttributes() != null) {
                result.setAttributes(new HashMap<>(decisionResult.getAttributes()));
            }
            
            return result;
        } catch (PDP.PolicyEvaluationException e) {
            throw new PolicyEnforcementException("策略执行失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEnforcementException("策略执行异常: " + e.getMessage(), e);
        }
    }
}

