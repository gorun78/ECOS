package com.chinacreator.gzcm.engine.security;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.security.policy.entity.DataSecurityPolicy;

/**
 * 数据安全策略服务接口
 * 提供数据分级分类、DLP、数据完整性保护等策略的配置管理
 * 供BUS-Ge和Runtime子系统调用
 */
public interface IDataSecurityPolicyService {
    
    /**
     * 创建数据安全策略
     * 
     * @param policy 策略对象
     * @param operator 操作者
     * @return 创建的策略
     * @throws DataSecurityPolicyException
     */
    DataSecurityPolicy createPolicy(DataSecurityPolicy policy, String operator) throws DataSecurityPolicyException;
    
    /**
     * 更新数据安全策略
     * 
     * @param policy 策略对象
     * @param operator 操作者
     * @return 更新后的策略
     * @throws DataSecurityPolicyException
     */
    DataSecurityPolicy updatePolicy(DataSecurityPolicy policy, String operator) throws DataSecurityPolicyException;
    
    /**
     * 删除数据安全策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws DataSecurityPolicyException
     */
    void deletePolicy(String policyId, String operator) throws DataSecurityPolicyException;
    
    /**
     * 获取数据安全策略
     * 
     * @param policyId 策略ID
     * @return 策略对象
     * @throws DataSecurityPolicyException
     */
    DataSecurityPolicy getPolicy(String policyId) throws DataSecurityPolicyException;
    
    /**
     * 查询数据安全策略列表
     * 
     * @param policyType 策略类型（可选）
     * @param scope 适用范围（可选）
     * @param scopeId 作用域ID（可选）
     * @param enabled 是否启用（可选）
     * @return 策略列表
     * @throws DataSecurityPolicyException
     */
    List<DataSecurityPolicy> listPolicies(String policyType, String scope, String scopeId, Boolean enabled) throws DataSecurityPolicyException;
    
    /**
     * 获取适用的数据安全策略（供BUS-Ge和Runtime调用）
     * 
     * @param policyType 策略类型
     * @param scopeId 作用域ID（租户ID或资源ID）
     * @return 适用的策略列表（按优先级排序）
     * @throws DataSecurityPolicyException
     */
    List<DataSecurityPolicy> getApplicablePolicies(String policyType, String scopeId) throws DataSecurityPolicyException;
    
    /**
     * 启用策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws DataSecurityPolicyException
     */
    void enablePolicy(String policyId, String operator) throws DataSecurityPolicyException;
    
    /**
     * 禁用策略
     * 
     * @param policyId 策略ID
     * @param operator 操作者
     * @throws DataSecurityPolicyException
     */
    void disablePolicy(String policyId, String operator) throws DataSecurityPolicyException;
    
    /**
     * 验证策略配置（验证policyContent的JSON格式和内容）
     * 
     * @param policyType 策略类型
     * @param policyContent 策略内容（JSON格式）
     * @return 验证结果，包含是否有效和错误信息
     * @throws DataSecurityPolicyException
     */
    ValidationResult validatePolicy(String policyType, String policyContent) throws DataSecurityPolicyException;
    
    /**
     * 验证结果
     */
    class ValidationResult {
        private boolean valid;
        private String errorMessage;
        private Map<String, Object> details;
        
        public ValidationResult(boolean valid) {
            this.valid = valid;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public Map<String, Object> getDetails() {
            return details;
        }
        
        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }
    }
    
    /**
     * 数据安全策略异常
     */
    class DataSecurityPolicyException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public DataSecurityPolicyException(String message) {
            super(message);
        }
        
        public DataSecurityPolicyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
