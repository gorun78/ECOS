package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.List;

/**
 * 鎵归噺鎿嶄綔缁撴灉妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class BatchResult {
    
    /**
     * 鎻掑叆鎴愬姛鐨勮褰曟暟
     */
    private int insertSuccessCount;
    
    /**
     * 鎻掑叆澶辫触鐨勮褰曟暟
     */
    private int insertFailureCount;
    
    /**
     * 鏇存柊鎴愬姛鐨勮褰曟暟
     */
    private int updateSuccessCount;
    
    /**
     * 鏇存柊澶辫触鐨勮褰曟暟
     */
    private int updateFailureCount;
    
    /**
     * 鍒犻櫎鎴愬姛鐨勮褰曟暟
     */
    private int deleteSuccessCount;
    
    /**
     * 鍒犻櫎澶辫触鐨勮褰曟暟
     */
    private int deleteFailureCount;
    
    /**
     * 閿欒淇℃伅鍒楄〃
     */
    private List<String> errors;
    
    /**
     * 鏄惁鍏ㄩ儴鎴愬姛
     */
    private boolean allSuccess;
    
    /**
     * 鎿嶄綔鑰楁椂锛堟绉掞級
     */
    private Long duration;
    
    // Getters and Setters
    
    public int getInsertSuccessCount() {
        return insertSuccessCount;
    }
    
    public void setInsertSuccessCount(int insertSuccessCount) {
        this.insertSuccessCount = insertSuccessCount;
    }
    
    public int getInsertFailureCount() {
        return insertFailureCount;
    }
    
    public void setInsertFailureCount(int insertFailureCount) {
        this.insertFailureCount = insertFailureCount;
    }
    
    public int getUpdateSuccessCount() {
        return updateSuccessCount;
    }
    
    public void setUpdateSuccessCount(int updateSuccessCount) {
        this.updateSuccessCount = updateSuccessCount;
    }
    
    public int getUpdateFailureCount() {
        return updateFailureCount;
    }
    
    public void setUpdateFailureCount(int updateFailureCount) {
        this.updateFailureCount = updateFailureCount;
    }
    
    public int getDeleteSuccessCount() {
        return deleteSuccessCount;
    }
    
    public void setDeleteSuccessCount(int deleteSuccessCount) {
        this.deleteSuccessCount = deleteSuccessCount;
    }
    
    public int getDeleteFailureCount() {
        return deleteFailureCount;
    }
    
    public void setDeleteFailureCount(int deleteFailureCount) {
        this.deleteFailureCount = deleteFailureCount;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public boolean isAllSuccess() {
        return allSuccess;
    }
    
    public void setAllSuccess(boolean allSuccess) {
        this.allSuccess = allSuccess;
    }
    
    public Long getDuration() {
        return duration;
    }
    
    public void setDuration(Long duration) {
        this.duration = duration;
    }
    
    /**
     * 鑾峰彇鎬绘垚鍔熸暟
     */
    public int getTotalSuccessCount() {
        return insertSuccessCount + updateSuccessCount + deleteSuccessCount;
    }
    
    /**
     * 鑾峰彇鎬诲け璐ユ暟
     */
    public int getTotalFailureCount() {
        return insertFailureCount + updateFailureCount + deleteFailureCount;
    }
}

