package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.Map;

/**
 * 鏇存柊璇锋眰妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class UpdateRequest {
    
    /**
     * 鏁版嵁浜у搧ID
     */
    private String dataProductId;
    
    /**
     * 鏇存柊鏉′欢
     */
    private FilterCondition filter;
    
    /**
     * 鏇存柊瀛楁Map锛堝瓧娈靛悕 -> 鏂板€硷級
     */
    private Map<String, Object> updateFields;
    
    /**
     * 鏄惁浣跨敤涔愯閿侊紙濡傛灉涓簍rue锛岄渶瑕佹彁渚泇ersion瀛楁锛?
     */
    private boolean useOptimisticLock = false;
    
    /**
     * 鐗堟湰瀛楁鍚嶏紙鐢ㄤ簬涔愯閿侊級
     */
    private String versionField = "version";
    
    /**
     * 鏈熸湜鐨勭増鏈€硷紙鐢ㄤ簬涔愯閿侊級
     */
    private Object expectedVersion;
    
    // Getters and Setters
    
    public String getDataProductId() {
        return dataProductId;
    }
    
    public void setDataProductId(String dataProductId) {
        this.dataProductId = dataProductId;
    }
    
    public FilterCondition getFilter() {
        return filter;
    }
    
    public void setFilter(FilterCondition filter) {
        this.filter = filter;
    }
    
    public Map<String, Object> getUpdateFields() {
        return updateFields;
    }
    
    public void setUpdateFields(Map<String, Object> updateFields) {
        this.updateFields = updateFields;
    }
    
    public boolean isUseOptimisticLock() {
        return useOptimisticLock;
    }
    
    public void setUseOptimisticLock(boolean useOptimisticLock) {
        this.useOptimisticLock = useOptimisticLock;
    }
    
    public String getVersionField() {
        return versionField;
    }
    
    public void setVersionField(String versionField) {
        this.versionField = versionField;
    }
    
    public Object getExpectedVersion() {
        return expectedVersion;
    }
    
    public void setExpectedVersion(Object expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}

