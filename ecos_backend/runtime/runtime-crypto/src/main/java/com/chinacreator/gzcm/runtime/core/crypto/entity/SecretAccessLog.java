package com.chinacreator.gzcm.runtime.core.crypto.entity;

import java.util.Date;

/**
 * 鏈哄瘑璁块棶鏃ュ織瀹炰綋绫?
 * 瀵瑰簲鏁版嵁搴撹〃锛歍D_SECRET_ACCESS_LOG
 * 
 * @author CDRC Runtime Team
 */
public class SecretAccessLog {
    
    /**
     * 鏃ュ織ID
     */
    private String logId;
    
    /**
     * 鏈哄瘑ID
     */
    private String secretId;
    
    /**
     * 鐢ㄦ埛ID
     */
    private String userId;
    
    /**
     * 鎿嶄綔绫诲瀷锛圕REATE, READ, UPDATE, DELETE, ROTATE锛?
     */
    private String action;
    
    /**
     * IP鍦板潃
     */
    private String ipAddress;
    
    /**
     * 鐢ㄦ埛浠ｇ悊
     */
    private String userAgent;
    
    /**
     * 鎿嶄綔缁撴灉锛圫UCCESS, FAILED锛?
     */
    private String result;
    
    /**
     * 閿欒淇℃伅
     */
    private String errorMessage;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private Date createdTime;
    
    // Getters and Setters
    
    public String getLogId() {
        return logId;
    }
    
    public void setLogId(String logId) {
        this.logId = logId;
    }
    
    public String getSecretId() {
        return secretId;
    }
    
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Date getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }
}

