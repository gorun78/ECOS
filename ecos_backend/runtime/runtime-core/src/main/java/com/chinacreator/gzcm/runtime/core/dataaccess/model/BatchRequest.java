package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.List;

/**
 * 鎵归噺鎿嶄綔璇锋眰妯″瀷
 * 
 * @param <T> 鏁版嵁绫诲瀷
 * @author CDRC Runtime Team
 */
public class BatchRequest<T> {
    
    /**
     * 鏁版嵁浜у搧ID
     */
    private String dataProductId;
    
    /**
     * 鎻掑叆鏁版嵁鍒楄〃
     */
    private List<T> insertData;
    
    /**
     * 鏇存柊璇锋眰鍒楄〃
     */
    private List<UpdateRequest> updateRequests;
    
    /**
     * 鍒犻櫎璇锋眰鍒楄〃
     */
    private List<DeleteRequest> deleteRequests;
    
    /**
     * 鎻掑叆閫夐」
     */
    private InsertOptions insertOptions;
    
    /**
     * 鏄惁鍚敤浜嬪姟锛堝鏋滀负true锛屾墍鏈夋搷浣滃湪涓€涓簨鍔′腑锛?
     */
    private boolean transactional = true;
    
    /**
     * 鏄惁鍦ㄥけ璐ユ椂鍥炴粴锛堝鏋滀负true锛屼换浣曟搷浣滃け璐ユ椂鍥炴粴鎵€鏈夋搷浣滐級
     */
    private boolean rollbackOnError = true;
    
    // Getters and Setters
    
    public String getDataProductId() {
        return dataProductId;
    }
    
    public void setDataProductId(String dataProductId) {
        this.dataProductId = dataProductId;
    }
    
    public List<T> getInsertData() {
        return insertData;
    }
    
    public void setInsertData(List<T> insertData) {
        this.insertData = insertData;
    }
    
    public List<UpdateRequest> getUpdateRequests() {
        return updateRequests;
    }
    
    public void setUpdateRequests(List<UpdateRequest> updateRequests) {
        this.updateRequests = updateRequests;
    }
    
    public List<DeleteRequest> getDeleteRequests() {
        return deleteRequests;
    }
    
    public void setDeleteRequests(List<DeleteRequest> deleteRequests) {
        this.deleteRequests = deleteRequests;
    }
    
    public InsertOptions getInsertOptions() {
        return insertOptions;
    }
    
    public void setInsertOptions(InsertOptions insertOptions) {
        this.insertOptions = insertOptions;
    }
    
    public boolean isTransactional() {
        return transactional;
    }
    
    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }
    
    public boolean isRollbackOnError() {
        return rollbackOnError;
    }
    
    public void setRollbackOnError(boolean rollbackOnError) {
        this.rollbackOnError = rollbackOnError;
    }
}

