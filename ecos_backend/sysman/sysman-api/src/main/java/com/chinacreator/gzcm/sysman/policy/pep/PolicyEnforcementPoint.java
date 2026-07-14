package com.chinacreator.gzcm.sysman.policy.pep;

import com.chinacreator.gzcm.sysman.policy.model.AccessRequest;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;

/**
 * 策略执行点（Policy Enforcement Point）：拦截请求并执行策略决策
 */
public interface PolicyEnforcementPoint {

    /**
     * 执行策略检查
     *
     * @param request 访问请求
     * @return 策略决策结果
     * @throws PolicyEnforcementException 执行异常
     */
    PolicyDecision enforce(AccessRequest request) throws PolicyEnforcementException;

    /**
     * 执行义务（如脱敏、审计）
     *
     * @param decision 策略决策结果
     * @param request  访问请求
     */
    void fulfillObligations(PolicyDecision decision, AccessRequest request);

    /**
     * 策略执行异常
     */
    class PolicyEnforcementException extends Exception {
        private static final long serialVersionUID = 1L;

        public PolicyEnforcementException(String message) {
            super(message);
        }

        public PolicyEnforcementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

