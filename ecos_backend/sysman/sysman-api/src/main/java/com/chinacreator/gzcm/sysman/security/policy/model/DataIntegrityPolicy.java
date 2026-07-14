package com.chinacreator.gzcm.sysman.security.policy.model;

import java.util.List;
import java.util.Map;

/**
 * 数据完整性保护策略模型
 * 用于解析和构建数据完整性保护策略配置
 */
public class DataIntegrityPolicy {
    
    /**
     * 完整性校验配置
     */
    private IntegrityCheckConfig checkConfig;
    
    /**
     * 防篡改配置
     */
    private AntiTamperConfig antiTamperConfig;
    
    /**
     * 签名配置
     */
    private SignatureConfig signatureConfig;
    
    /**
     * 完整性校验配置
     */
    public static class IntegrityCheckConfig {
        private String algorithm;  // MD5, SHA256, SHA512, SM3等
        private Boolean enableHash;  // 是否启用哈希校验
        private Boolean enableChecksum;  // 是否启用校验和
        private String checkFrequency;  // 校验频率：REAL_TIME, DAILY, WEEKLY等
        private Map<String, Object> parameters;  // 算法参数
        
        // Getters and Setters
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        public Boolean getEnableHash() { return enableHash; }
        public void setEnableHash(Boolean enableHash) { this.enableHash = enableHash; }
        public Boolean getEnableChecksum() { return enableChecksum; }
        public void setEnableChecksum(Boolean enableChecksum) { this.enableChecksum = enableChecksum; }
        public String getCheckFrequency() { return checkFrequency; }
        public void setCheckFrequency(String checkFrequency) { this.checkFrequency = checkFrequency; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    /**
     * 防篡改配置
     */
    public static class AntiTamperConfig {
        private Boolean enableTamperDetection;  // 是否启用篡改检测
        private String detectionMethod;  // HASH_COMPARE, VERSION_CONTROL, AUDIT_LOG等
        private String alertAction;  // ALERT, BLOCK, AUDIT
        private List<String> protectedFields;  // 受保护字段列表
        private Map<String, Object> rules;  // 防篡改规则
        
        // Getters and Setters
        public Boolean getEnableTamperDetection() { return enableTamperDetection; }
        public void setEnableTamperDetection(Boolean enableTamperDetection) { this.enableTamperDetection = enableTamperDetection; }
        public String getDetectionMethod() { return detectionMethod; }
        public void setDetectionMethod(String detectionMethod) { this.detectionMethod = detectionMethod; }
        public String getAlertAction() { return alertAction; }
        public void setAlertAction(String alertAction) { this.alertAction = alertAction; }
        public List<String> getProtectedFields() { return protectedFields; }
        public void setProtectedFields(List<String> protectedFields) { this.protectedFields = protectedFields; }
        public Map<String, Object> getRules() { return rules; }
        public void setRules(Map<String, Object> rules) { this.rules = rules; }
    }
    
    /**
     * 签名配置
     */
    public static class SignatureConfig {
        private Boolean enableSignature;  // 是否启用数字签名
        private String signatureAlgorithm;  // RSA, ECDSA, SM2等
        private String keyId;  // 密钥ID（引用Runtime的密钥管理）
        private String keySource;  // KEY_MANAGEMENT, HSM等
        private Boolean enableTimestamp;  // 是否启用时间戳
        private Map<String, Object> parameters;  // 签名参数
        
        // Getters and Setters
        public Boolean getEnableSignature() { return enableSignature; }
        public void setEnableSignature(Boolean enableSignature) { this.enableSignature = enableSignature; }
        public String getSignatureAlgorithm() { return signatureAlgorithm; }
        public void setSignatureAlgorithm(String signatureAlgorithm) { this.signatureAlgorithm = signatureAlgorithm; }
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getKeySource() { return keySource; }
        public void setKeySource(String keySource) { this.keySource = keySource; }
        public Boolean getEnableTimestamp() { return enableTimestamp; }
        public void setEnableTimestamp(Boolean enableTimestamp) { this.enableTimestamp = enableTimestamp; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    // Getters and Setters
    public IntegrityCheckConfig getCheckConfig() {
        return checkConfig;
    }
    
    public void setCheckConfig(IntegrityCheckConfig checkConfig) {
        this.checkConfig = checkConfig;
    }
    
    public AntiTamperConfig getAntiTamperConfig() {
        return antiTamperConfig;
    }
    
    public void setAntiTamperConfig(AntiTamperConfig antiTamperConfig) {
        this.antiTamperConfig = antiTamperConfig;
    }
    
    public SignatureConfig getSignatureConfig() {
        return signatureConfig;
    }
    
    public void setSignatureConfig(SignatureConfig signatureConfig) {
        this.signatureConfig = signatureConfig;
    }
}

