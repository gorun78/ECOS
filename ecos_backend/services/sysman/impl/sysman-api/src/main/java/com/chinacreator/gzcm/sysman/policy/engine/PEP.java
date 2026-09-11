package com.chinacreator.gzcm.sysman.policy.engine;

import java.util.Map;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;

/**
 * Policy Enforcement Point (策略执行点)
 * 负责在访问点执行策略决策
 */
public interface PEP {
    
    /**
     * 执行访问控制检查
     * 
     * @param context 访问上下文
     * @return true表示允许访问，false表示拒绝
     * @throws PolicyEnforcementException 策略执行异常
     */
    boolean enforce(AbacContext context) throws PolicyEnforcementException;
    
    /**
     * 执行访问控制检查并返回详细结果
     * 
     * @param context 访问上下文
     * @return 执行结果，包含是否允许、决策详情等
     * @throws PolicyEnforcementException 策略执行异常
     */
    EnforcementResult enforceWithDetails(AbacContext context) throws PolicyEnforcementException;
    
    /**
     * 执行结果
     */
    class EnforcementResult {
        private boolean permitted;
        private PDP.Decision decision;
        private String reason;
        private Map<String, Object> attributes;
        
        public EnforcementResult(boolean permitted, PDP.Decision decision) {
            this.permitted = permitted;
            this.decision = decision;
        }
        
        public boolean isPermitted() {
            return permitted;
        }
        
        public void setPermitted(boolean permitted) {
            this.permitted = permitted;
        }
        
        public PDP.Decision getDecision() {
            return decision;
        }
        
        public void setDecision(PDP.Decision decision) {
            this.decision = decision;
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

