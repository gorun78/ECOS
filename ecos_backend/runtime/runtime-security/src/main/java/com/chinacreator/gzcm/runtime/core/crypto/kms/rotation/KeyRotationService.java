package com.chinacreator.gzcm.runtime.core.crypto.kms.rotation;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.kms.KMSAdapter;

/**
 * 密钥轮换服务接口
 * 提供密钥自动轮换和手动轮换功能
 */
public interface KeyRotationService {
    
    /**
     * 自动轮换密钥
     * 
     * @param keyId 密钥ID
     * @param rotationPolicy 轮换策略
     * @return 新密钥版本
     * @throws KeyRotationException 轮换失败
     */
    String autoRotate(String keyId, RotationPolicy rotationPolicy) throws KeyRotationException;
    
    /**
     * 手动轮换密钥
     * 
     * @param keyId 密钥ID
     * @return 新密钥版本
     * @throws KeyRotationException 轮换失败
     */
    String manualRotate(String keyId) throws KeyRotationException;
    
    /**
     * 获取轮换历史
     * 
     * @param keyId 密钥ID
     * @return 轮换历史列表
     * @throws KeyRotationException 查询失败
     */
    List<RotationRecord> getRotationHistory(String keyId) throws KeyRotationException;
    
    /**
     * 清理旧密钥版本
     * 
     * @param keyId 密钥ID
     * @param keepVersions 保留的版本数
     * @throws KeyRotationException 清理失败
     */
    void cleanupOldVersions(String keyId, int keepVersions) throws KeyRotationException;
    
    /**
     * 密钥轮换异常
     */
    class KeyRotationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public KeyRotationException(String message) {
            super(message);
        }
        
        public KeyRotationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 轮换策略
     */
    class RotationPolicy {
        private String type; // TIME_BASED, USAGE_BASED, MANUAL
        private long rotationInterval; // 轮换间隔（毫秒，用于TIME_BASED）
        private long maxUsageCount; // 最大使用次数（用于USAGE_BASED）
        private int keepVersions; // 保留的版本数
        
        public RotationPolicy(String type) {
            this.type = type;
        }
        
        public String getType() {
            return type;
        }
        
        public long getRotationInterval() {
            return rotationInterval;
        }
        
        public void setRotationInterval(long rotationInterval) {
            this.rotationInterval = rotationInterval;
        }
        
        public long getMaxUsageCount() {
            return maxUsageCount;
        }
        
        public void setMaxUsageCount(long maxUsageCount) {
            this.maxUsageCount = maxUsageCount;
        }
        
        public int getKeepVersions() {
            return keepVersions;
        }
        
        public void setKeepVersions(int keepVersions) {
            this.keepVersions = keepVersions;
        }
    }
    
    /**
     * 轮换记录
     */
    class RotationRecord {
        private String keyId;
        private String oldVersion;
        private String newVersion;
        private long rotationTime;
        private String rotationType; // AUTO, MANUAL
        private String reason;
        
        public RotationRecord(String keyId, String oldVersion, String newVersion, long rotationTime, String rotationType) {
            this.keyId = keyId;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.rotationTime = rotationTime;
            this.rotationType = rotationType;
        }
        
        public String getKeyId() {
            return keyId;
        }
        
        public String getOldVersion() {
            return oldVersion;
        }
        
        public String getNewVersion() {
            return newVersion;
        }
        
        public long getRotationTime() {
            return rotationTime;
        }
        
        public String getRotationType() {
            return rotationType;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
