package com.chinacreator.gzcm.runtime.core.crypto.model;

import java.util.Date;

/**
 * 瀵嗛挜鍏冩暟鎹?
 * 
 * @author CDRC Runtime Team
 */
public class KeyMetadata {
    
    /**
     * 瀵嗛挜ID
     */
    private String keyId;
    
    /**
     * 瀵嗛挜绫诲瀷锛圖EK/KEK锛?
     */
    private String keyType;
    
    /**
     * 鍔犲瘑绠楁硶锛圓ES/RSA绛夛級
     */
    private String algorithm;
    
    /**
     * 鐗堟湰鍙?
     */
    private Integer version;
    
    /**
     * 瀵嗛挜闀垮害锛堜綅锛?
     */
    private Integer keySize;
    
    /**
     * 涓诲瘑閽D锛堢敤浜庡姞瀵嗘瀵嗛挜锛?
     */
    private String masterKeyId;
    
    /**
     * 鐘舵€侊紙ACTIVE/INACTIVE/DELETED锛?
     */
    private String status;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰?
     */
    private String createdBy;
    
    /**
     * 鏇存柊鏃堕棿
     */
    private Date updatedTime;
    
    /**
     * 鏇存柊鑰?
     */
    private String updatedBy;
    
    /**
     * 杩囨湡鏃堕棿
     */
    private Date expiryTime;
    
    /**
     * 杞崲鍛ㄦ湡锛堝ぉ锛?
     */
    private Integer rotationPeriod;
    
    /**
     * 涓婃杞崲鏃堕棿
     */
    private Date lastRotationTime;
    
    /**
     * 涓嬫杞崲鏃堕棿
     */
    private Date nextRotationTime;
    
    /**
     * 澶囨敞
     */
    private String remark;
    
    // Getters and Setters
    
    public String getKeyId() {
        return keyId;
    }
    
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
    
    public String getKeyType() {
        return keyType;
    }
    
    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
    
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
    
    public Integer getVersion() {
        return version;
    }
    
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    public Integer getKeySize() {
        return keySize;
    }
    
    public void setKeySize(Integer keySize) {
        this.keySize = keySize;
    }
    
    public String getMasterKeyId() {
        return masterKeyId;
    }
    
    public void setMasterKeyId(String masterKeyId) {
        this.masterKeyId = masterKeyId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Date getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public Date getUpdatedTime() {
        return updatedTime;
    }
    
    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
    
    public Date getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(Date expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    public Integer getRotationPeriod() {
        return rotationPeriod;
    }
    
    public void setRotationPeriod(Integer rotationPeriod) {
        this.rotationPeriod = rotationPeriod;
    }
    
    public Date getLastRotationTime() {
        return lastRotationTime;
    }
    
    public void setLastRotationTime(Date lastRotationTime) {
        this.lastRotationTime = lastRotationTime;
    }
    
    public Date getNextRotationTime() {
        return nextRotationTime;
    }
    
    public void setNextRotationTime(Date nextRotationTime) {
        this.nextRotationTime = nextRotationTime;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
}

