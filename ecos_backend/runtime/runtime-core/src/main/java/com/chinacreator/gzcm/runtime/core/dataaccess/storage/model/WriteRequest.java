package com.chinacreator.gzcm.runtime.core.dataaccess.storage.model;

import java.util.List;
import java.util.Map;

/**
 * 鍐欏叆璇锋眰妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class WriteRequest {
    
    /**
     * 璧勬簮鏍囪瘑锛堝琛ㄥ悕銆侀泦鍚堝悕绛夛級
     */
    private String resource;
    
    /**
     * 鏁版嵁鍒楄〃
     */
    private List<Map<String, Object>> data;
    
    /**
     * 鍐欏叆妯″紡锛圛NSERT/UPDATE/UPSERT锛?
     */
    private WriteMode writeMode;
    
    /**
     * 鎵归噺澶у皬
     */
    private Integer batchSize;
    
    /**
     * 鏇存柊鏉′欢锛堢敤浜嶶PDATE妯″紡锛?
     */
    private Map<String, Object> updateCondition;
    
    /**
     * 鏄惁浣跨敤浜嬪姟
     */
    private Boolean useTransaction = true;
    
    /**
     * 涓婁笅鏂囦俊鎭?
     */
    private Map<String, Object> context;
    
    /**
     * 鍐欏叆妯″紡鏋氫妇
     */
    public enum WriteMode {
        /**
         * 鎻掑叆妯″紡
         */
        INSERT,
        
        /**
         * 鏇存柊妯″紡
         */
        UPDATE,
        
        /**
         * 鎻掑叆鎴栨洿鏂版ā寮忥紙濡傛灉瀛樺湪鍒欐洿鏂帮紝涓嶅瓨鍦ㄥ垯鎻掑叆锛?
         */
        UPSERT,
        
        /**
         * 鏇挎崲妯″紡锛堝垹闄ゅ悗鎻掑叆锛?
         */
        REPLACE
    }
    
    // Getters and Setters
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public List<Map<String, Object>> getData() {
        return data;
    }
    
    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
    
    public WriteMode getWriteMode() {
        return writeMode;
    }
    
    public void setWriteMode(WriteMode writeMode) {
        this.writeMode = writeMode;
    }
    
    public Integer getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
    
    public Map<String, Object> getUpdateCondition() {
        return updateCondition;
    }
    
    public void setUpdateCondition(Map<String, Object> updateCondition) {
        this.updateCondition = updateCondition;
    }
    
    public Boolean getUseTransaction() {
        return useTransaction;
    }
    
    public void setUseTransaction(Boolean useTransaction) {
        this.useTransaction = useTransaction;
    }
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}

