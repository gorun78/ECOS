package com.chinacreator.gzcm.sysman.kms;

import java.util.List;
import java.util.Map;

/**
 * KMS适配器接口
 * 提供统一的密钥管理服务接口，支持多种KMS实现（AWS KMS、Azure Key Vault、HashiCorp Vault等）
 */
public interface KMSAdapter {
    
    /**
     * 创建密钥
     * 
     * @param keyId 密钥ID
     * @param keySpec 密钥规格（AES_256、RSA_2048等）
     * @param metadata 元数据（可选）
     * @return 密钥信息
     * @throws KMSException 创建失败
     */
    KeyInfo createKey(String keyId, String keySpec, Map<String, Object> metadata) throws KMSException;
    
    /**
     * 获取密钥
     * 
     * @param keyId 密钥ID
     * @param version 版本号（可选，如果为null则获取当前版本）
     * @return 密钥信息
     * @throws KMSException 获取失败
     */
    KeyInfo getKey(String keyId, String version) throws KMSException;
    
    /**
     * 删除密钥
     * 
     * @param keyId 密钥ID
     * @throws KMSException 删除失败
     */
    void deleteKey(String keyId) throws KMSException;
    
    /**
     * 轮换密钥
     * 
     * @param keyId 密钥ID
     * @return 新密钥版本
     * @throws KMSException 轮换失败
     */
    String rotateKey(String keyId) throws KMSException;
    
    /**
     * 加密数据
     * 
     * @param keyId 密钥ID
     * @param plaintext 明文
     * @return 密文
     * @throws KMSException 加密失败
     */
    byte[] encrypt(String keyId, byte[] plaintext) throws KMSException;
    
    /**
     * 解密数据
     * 
     * @param keyId 密钥ID
     * @param ciphertext 密文
     * @return 明文
     * @throws KMSException 解密失败
     */
    byte[] decrypt(String keyId, byte[] ciphertext) throws KMSException;
    
    /**
     * 获取密钥版本列表
     * 
     * @param keyId 密钥ID
     * @return 版本列表
     * @throws KMSException 查询失败
     */
    List<KeyVersion> listKeyVersions(String keyId) throws KMSException;
    
    /**
     * 获取密钥列表
     * 
     * @param filters 过滤条件（可选）
     * @return 密钥ID列表
     * @throws KMSException 查询失败
     */
    List<String> listKeys(Map<String, Object> filters) throws KMSException;
    
    /**
     * KMS异常
     */
    class KMSException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public KMSException(String message) {
            super(message);
        }
        
        public KMSException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 密钥信息
     */
    class KeyInfo {
        private String keyId;
        private String keySpec;
        private String version;
        private String status; // ENABLED, DISABLED, PENDING_DELETION, DELETED
        private long createdTime;
        private Map<String, Object> metadata;
        
        public KeyInfo(String keyId, String keySpec, String version) {
            this.keyId = keyId;
            this.keySpec = keySpec;
            this.version = version;
        }
        
        public String getKeyId() {
            return keyId;
        }
        
        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
        
        public String getKeySpec() {
            return keySpec;
        }
        
        public void setKeySpec(String keySpec) {
            this.keySpec = keySpec;
        }
        
        public String getVersion() {
            return version;
        }
        
        public void setVersion(String version) {
            this.version = version;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public long getCreatedTime() {
            return createdTime;
        }
        
        public void setCreatedTime(long createdTime) {
            this.createdTime = createdTime;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
    
    /**
     * 密钥版本
     */
    class KeyVersion {
        private String keyId;
        private String version;
        private String status;
        private long createdTime;
        
        public KeyVersion(String keyId, String version) {
            this.keyId = keyId;
            this.version = version;
        }
        
        public String getKeyId() {
            return keyId;
        }
        
        public String getVersion() {
            return version;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public long getCreatedTime() {
            return createdTime;
        }
        
        public void setCreatedTime(long createdTime) {
            this.createdTime = createdTime;
        }
    }
}
