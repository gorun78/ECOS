package com.chinacreator.gzcm.sysman.policy.pdp;

import com.chinacreator.gzcm.sysman.policy.model.AccessRequest;
import com.chinacreator.gzcm.sysman.policy.model.PolicyDecision;

/**
 * 策略决策点（Policy Decision Point）：评估访问请求并返回决策结果
 */
public interface PolicyDecisionPoint {

    /**
     * 评估访问请求
     *
     * @param request 访问请求
     * @return 策略决策结果
     * @throws PolicyEvaluationException 评估异常
     */
    PolicyDecision evaluate(AccessRequest request) throws PolicyEvaluationException;

    /**
     * 策略评估异常
     */
    class PolicyEvaluationException extends Exception {
        private static final long serialVersionUID = 1L;

        public PolicyEvaluationException(String message) {
            super(message);
        }

        public PolicyEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

