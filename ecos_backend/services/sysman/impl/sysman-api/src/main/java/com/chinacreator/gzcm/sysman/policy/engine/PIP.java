package com.chinacreator.gzcm.sysman.policy.engine;

import java.util.Map;

/**
 * Policy Information Point (策略信息点)
 * 负责提供策略评估所需的信息（主体属性、资源属性、环境属性等）
 */
public interface PIP {
    
    /**
     * 获取主体属性
     * 
     * @param subjectId 主体ID（用户ID）
     * @return 主体属性Map
     * @throws PolicyInformationException 策略信息异常
     */
    Map<String, Object> getSubjectAttributes(String subjectId) throws PolicyInformationException;
    
    /**
     * 获取资源属性
     * 
     * @param resourceId 资源ID
     * @return 资源属性Map
     * @throws PolicyInformationException 策略信息异常
     */
    Map<String, Object> getResourceAttributes(String resourceId) throws PolicyInformationException;
    
    /**
     * 获取环境属性
     * 
     * @return 环境属性Map（时间、IP、地理位置等）
     * @throws PolicyInformationException 策略信息异常
     */
    Map<String, Object> getEnvironmentAttributes() throws PolicyInformationException;
    
    /**
     * 获取指定属性的值
     * 
     * @param attributeName 属性名称（支持点号分隔的路径，如 "subject.department"）
     * @param context 上下文信息
     * @return 属性值
     * @throws PolicyInformationException 策略信息异常
     */
    Object getAttribute(String attributeName, Map<String, Object> context) throws PolicyInformationException;
    
    /**
     * 批量获取属性
     * 
     * @param attributeNames 属性名称列表
     * @param context 上下文信息
     * @return 属性Map，key为属性名，value为属性值
     * @throws PolicyInformationException 策略信息异常
     */
    Map<String, Object> getAttributes(java.util.List<String> attributeNames, Map<String, Object> context) throws PolicyInformationException;
    
    /**
     * 策略信息异常
     */
    class PolicyInformationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public PolicyInformationException(String message) {
            super(message);
        }
        
        public PolicyInformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

