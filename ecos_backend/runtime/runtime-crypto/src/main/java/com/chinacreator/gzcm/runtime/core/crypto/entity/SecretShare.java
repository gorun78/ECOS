package com.chinacreator.gzcm.runtime.core.crypto.entity;

import java.util.Date;

/**
 * 鏈哄瘑鍏变韩瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_SECRET_SHARE
 * 
 * @author CDRC Runtime Team
 */
public class SecretShare {
    
    /**
     * 鍏变韩ID
     */
    private String shareId;
    
    /**
     * 鏈哄瘑ID
     */
    private String secretId;
    
    /**
     * 鍏变韩缁欑殑鐢ㄦ埛ID
     */
    private String sharedToUserId;
    
    /**
     * 鏉冮檺锛圧EAD, WRITE锛?
     */
    private String permission;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    /**
     * 鍒涘缓鑰匢D
     */
    private String createdBy;
    
    /**
     * 杩囨湡鏃堕棿
     */
    private Date expiryTime;
    
    /**
     * 鐘舵€侊紙ACTIVE, REVOKED锛?
     */
    private String status;
    
    // Getters and Setters
    
    public String getShareId() {
        return shareId;
    }
    
    public void setShareId(String shareId) {
        this.shareId = shareId;
    }
    
    public String getSecretId() {
        return secretId;
    }
    
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }
    
    public String getSharedToUserId() {
        return sharedToUserId;
    }
    
    public void setSharedToUserId(String sharedToUserId) {
        this.sharedToUserId = sharedToUserId;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public void setPermission(String permission) {
        this.permission = permission;
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
    
    public Date getExpiryTime() {
        return expiryTime;
    }
    
    public void setExpiryTime(Date expiryTime) {
        this.expiryTime = expiryTime;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}

