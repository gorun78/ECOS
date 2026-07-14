package com.chinacreator.gzcm.runtime.core.crypto.entity;

import java.util.Date;

/**
 * 鏈哄瘑瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_SECRET
 * 
 * @author CDRC Runtime Team
 */
public class Secret {
    
    /**
     * 鏈哄瘑ID
     */
    private String secretId;
    
    /**
     * 鏈哄瘑鍚嶇О
     */
    private String secretName;
    
    /**
     * 鏈哄瘑绫诲瀷锛圥ASSWORD, API_KEY, TOKEN绛夛級
     */
    private String secretType;
    
    /**
     * 鍔犲瘑鍚庣殑鏈哄瘑鍊?
     */
    private String secretValueEncrypted;
    
    /**
     * 鍔犲瘑瀵嗛挜ID
     */
    private String keyId;
    
    /**
     * 鎻忚堪
     */
    private String description;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰匢D
     */
    private String createdBy;
    
    /**
     * 鏇存柊鏃堕棿
     */
    private Date updatedTime;
    
    /**
     * 鏇存柊鑰匢D
     */
    private String updatedBy;
    
    /**
     * 鐘舵€侊紙ACTIVE, INACTIVE, DELETED锛?
     */
    private String status;
    
    /**
     * 澶囨敞
     */
    private String remark;
    
    // Getters and Setters
    
    public String getSecretId() {
        return secretId;
    }
    
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }
    
    public String getSecretName() {
        return secretName;
    }
    
    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }
    
    public String getSecretType() {
        return secretType;
    }
    
    public void setSecretType(String secretType) {
        this.secretType = secretType;
    }
    
    public String getSecretValueEncrypted() {
        return secretValueEncrypted;
    }
    
    public void setSecretValueEncrypted(String secretValueEncrypted) {
        this.secretValueEncrypted = secretValueEncrypted;
    }
    
    public String getKeyId() {
        return keyId;
    }
    
    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
}

