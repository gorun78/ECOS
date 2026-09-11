package com.chinacreator.gzcm.sysman.policy.engine;

import java.util.Map;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;

/**
 * Policy Decision Point (策略决策点)
 * 负责评估策略并做出访问控制决策
 */
public interface PDP {
    
    /**
     * 决策结果枚举
     */
    enum Decision {
        PERMIT,    // 允许
        DENY,      // 拒绝
        NOT_APPLICABLE,  // 不适用
        INDETERMINATE    // 不确定（评估出错）
    }
    
    /**
     * 评估访问请求并做出决策
     * 
     * @param context 访问上下文（包含主体、资源、操作、环境信息）
     * @return 决策结果
     * @throws PolicyEvaluationException 策略评估异常
     */
    Decision evaluate(AbacContext context) throws PolicyEvaluationException;
    
    /**
     * 评估访问请求并返回详细决策信息
     * 
     * @param context 访问上下文
     * @return 决策结果，包含决策、适用的策略ID、原因等
     * @throws PolicyEvaluationException 策略评估异常
     */
    DecisionResult evaluateWithDetails(AbacContext context) throws PolicyEvaluationException;
    
    /**
     * 决策结果
     */
    class DecisionResult {
        private Decision decision;
        private String policyId;  // 适用的策略ID
        private String reason;     // 决策原因
        private Map<String, Object> attributes;  // 附加属性
        
        public DecisionResult(Decision decision) {
            this.decision = decision;
        }
        
        public Decision getDecision() {
            return decision;
        }
        
        public void setDecision(Decision decision) {
            this.decision = decision;
        }
        
        public String getPolicyId() {
            return policyId;
        }
        
        public void setPolicyId(String policyId) {
            this.policyId = policyId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
        
        public Map<String, Object> getAttributes() {
            return attributes;
        }
        
        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }
    }
    
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

