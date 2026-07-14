package com.chinacreator.gzcm.sysman.policy.engine;

import java.util.List;

import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;

/**
 * Policy Administration Point (策略管理点)
 * 负责策略的创建、更新、删除和查询
 */
public interface PAP {
    
    /**
     * 创建策略
     * 
     * @param policy 策略对象
     * @param operator 操作者
     * @return 创建的策略
     * @throws PolicyAdministrationException 策略管理异常
     */
    AbacPolicy createPolicy(AbacPolicy policy, String operator) throws PolicyAdministrationException;
    
    /**
     * 更新策略
     * 
     * @param policy 策略对象
     * @param operator 操作者
     * @return 更新后的策略
     * @throws PolicyAdministrationException 策略管理异常
     */
    AbacPolicy updatePolicy(AbacPolicy policy, String operator) throws PolicyAdministrationException;
    
    /**
     * 删除策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws PolicyAdministrationException 策略管理异常
     */
    void deletePolicy(String policyId, String operator) throws PolicyAdministrationException;
    
    /**
     * 获取策略
     * 
     * @param policyId 策略ID
     * @return 策略对象
     * @throws PolicyAdministrationException 策略管理异常
     */
    AbacPolicy getPolicy(String policyId) throws PolicyAdministrationException;
    
    /**
     * 列出所有策略
     * 
     * @return 策略列表
     * @throws PolicyAdministrationException 策略管理异常
     */
    List<AbacPolicy> listPolicies() throws PolicyAdministrationException;
    
    /**
     * 根据条件查询策略
     * 
     * @param condition 查询条件
     * @return 策略列表
     * @throws PolicyAdministrationException 策略管理异常
     */
    List<AbacPolicy> queryPolicies(java.util.Map<String, Object> condition) throws PolicyAdministrationException;
    
    /**
     * 启用策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws PolicyAdministrationException 策略管理异常
     */
    void enablePolicy(String policyId, String operator) throws PolicyAdministrationException;
    
    /**
     * 禁用策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws PolicyAdministrationException 策略管理异常
     */
    void disablePolicy(String policyId, String operator) throws PolicyAdministrationException;
    
    /**
     * 策略管理异常
     */
    class PolicyAdministrationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public PolicyAdministrationException(String message) {
            super(message);
        }
        
        public PolicyAdministrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

