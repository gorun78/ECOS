package com.chinacreator.gzcm.runtime.core.security.policy.engine.impl;


import java.util.HashMap;
import java.util.Map;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.policy.engine.PDP;

/**
 * Policy Decision Point 实现
 * 基于ABAC权限检查器实现策略决策
 */
public class PDPImpl implements PDP {
    
    private final IAbacPermissionChecker abacChecker;
    
    public PDPImpl(IAbacPermissionChecker abacChecker) {
        this.abacChecker = abacChecker;
    }
    
    @Override
    public Decision evaluate(AbacContext context) throws PolicyEvaluationException {
        try {
            IAbacPermissionChecker.Decision decision = abacChecker.check(context);
            return convertDecision(decision);
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            throw new PolicyEvaluationException("策略评估失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEvaluationException("策略评估异常: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DecisionResult evaluateWithDetails(AbacContext context) throws PolicyEvaluationException {
        try {
            IAbacPermissionChecker.Decision decision = abacChecker.check(context);
            DecisionResult result = new DecisionResult(convertDecision(decision));
            
            // 设置决策原因
            switch (result.getDecision()) {
                case PERMIT:
                    result.setReason("策略评估允许访问");
                    break;
                case DENY:
                    result.setReason("策略评估拒绝访问");
                    break;
                case NOT_APPLICABLE:
                    result.setReason("没有适用的策略");
                    break;
                default:
                    result.setReason("策略评估结果不确定");
            }
            
            // 可以添加更多详细信息
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("subject", context.getSubject());
            attributes.put("resource", context.getResource());
            attributes.put("action", context.getAction());
            attributes.put("environment", context.getEnvironment());
            result.setAttributes(attributes);
            
            return result;
        } catch (IAbacPermissionChecker.PolicyEvaluationException e) {
            throw new PolicyEvaluationException("策略评估失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new PolicyEvaluationException("策略评估异常: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换决策类型
     */
    private Decision convertDecision(IAbacPermissionChecker.Decision decision) {
        if (decision == null) {
            return Decision.NOT_APPLICABLE;
        }
        switch (decision) {
            case PERMIT:
                return Decision.PERMIT;
            case DENY:
                return Decision.DENY;
            case NOT_APPLICABLE:
            default:
                return Decision.NOT_APPLICABLE;
        }
    }
}

