package com.chinacreator.gzcm.sysman.security.policy.model;

import java.util.List;
import java.util.Map;

/**
 * 加密策略模型
 * 用于配置加密策略（实际加密执行在Runtime）
 */
public class EncryptionPolicy {
    
    /**
     * 加密算法配置
     */
    private EncryptionAlgorithmConfig algorithmConfig;
    
    /**
     * 密钥配置（引用Runtime的密钥管理）
     */
    private KeyConfig keyConfig;
    
    /**
     * 加密字段配置
     */
    private List<EncryptionFieldConfig> fieldConfigs;
    
    /**
     * 加密算法配置
     */
    public static class EncryptionAlgorithmConfig {
        private String algorithm;  // AES, RSA, SM4等
        private String mode;  // CBC, GCM等（AES）
        private Integer keyLength;  // 密钥长度
        private String padding;  // PKCS5Padding等
        private Map<String, Object> parameters;  // 算法参数
        
        // Getters and Setters
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Integer getKeyLength() { return keyLength; }
        public void setKeyLength(Integer keyLength) { this.keyLength = keyLength; }
        public String getPadding() { return padding; }
        public void setPadding(String padding) { this.padding = padding; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
    
    /**
     * 密钥配置
     */
    public static class KeyConfig {
        private String keyId;  // 密钥ID（Runtime密钥管理的密钥ID）
        private String keySource;  // KEY_MANAGEMENT, HSM
        private String rotationPolicy;  // 密钥轮换策略：MANUAL, AUTO, SCHEDULED
        private Integer rotationInterval;  // 轮换间隔（天）
        private Boolean enableAutoRotation;  // 是否自动轮换
        
        // Getters and Setters
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getKeySource() { return keySource; }
        public void setKeySource(String keySource) { this.keySource = keySource; }
        public String getRotationPolicy() { return rotationPolicy; }
        public void setRotationPolicy(String rotationPolicy) { this.rotationPolicy = rotationPolicy; }
        public Integer getRotationInterval() { return rotationInterval; }
        public void setRotationInterval(Integer rotationInterval) { this.rotationInterval = rotationInterval; }
        public Boolean getEnableAutoRotation() { return enableAutoRotation; }
        public void setEnableAutoRotation(Boolean enableAutoRotation) { this.enableAutoRotation = enableAutoRotation; }
    }
    
    /**
     * 加密字段配置
     */
    public static class EncryptionFieldConfig {
        private String resourceId;  // 资源ID（表名、字段名等）
        private String resourceType;  // TABLE, FIELD, FILE等
        private String fieldName;  // 字段名
        private String encryptionType;  // FIELD_LEVEL, COLUMN_LEVEL, FILE_LEVEL
        private Boolean enableEncryption;  // 是否启用加密
        private Map<String, Object> metadata;  // 元数据
        
        // Getters and Setters
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getEncryptionType() { return encryptionType; }
        public void setEncryptionType(String encryptionType) { this.encryptionType = encryptionType; }
        public Boolean getEnableEncryption() { return enableEncryption; }
        public void setEnableEncryption(Boolean enableEncryption) { this.enableEncryption = enableEncryption; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    // Getters and Setters
    public EncryptionAlgorithmConfig getAlgorithmConfig() {
        return algorithmConfig;
    }
    
    public void setAlgorithmConfig(EncryptionAlgorithmConfig algorithmConfig) {
        this.algorithmConfig = algorithmConfig;
    }
    
    public KeyConfig getKeyConfig() {
        return keyConfig;
    }
    
    public void setKeyConfig(KeyConfig keyConfig) {
        this.keyConfig = keyConfig;
    }
    
    public List<EncryptionFieldConfig> getFieldConfigs() {
        return fieldConfigs;
    }
    
    public void setFieldConfigs(List<EncryptionFieldConfig> fieldConfigs) {
        this.fieldConfigs = fieldConfigs;
    }
}

