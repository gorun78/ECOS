package com.chinacreator.gzcm.runtime.core.dataaccess.model;

/**
 * 鍒犻櫎璇锋眰妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class DeleteRequest {
    
    /**
     * 鏁版嵁浜у搧ID
     */
    private String dataProductId;
    
    /**
     * 鍒犻櫎鏉′欢
     */
    private FilterCondition filter;
    
    /**
     * 鏄惁杞垹闄わ紙濡傛灉涓簍rue锛屽彧鏍囪鍒犻櫎鑰屼笉鐪熸鍒犻櫎鏁版嵁锛?
     */
    private boolean softDelete = true;
    
    /**
     * 杞垹闄ゅ瓧娈靛悕锛堥粯璁や负"deleted"锛?
     */
    private String softDeleteField = "deleted";
    
    /**
     * 杞垹闄ゆ爣璁板€硷紙榛樿涓簍rue锛?
     */
    private Object softDeleteValue = true;
    
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
    
    public boolean isSoftDelete() {
        return softDelete;
    }
    
    public void setSoftDelete(boolean softDelete) {
        this.softDelete = softDelete;
    }
    
    public String getSoftDeleteField() {
        return softDeleteField;
    }
    
    public void setSoftDeleteField(String softDeleteField) {
        this.softDeleteField = softDeleteField;
    }
    
    public Object getSoftDeleteValue() {
        return softDeleteValue;
    }
    
    public void setSoftDeleteValue(Object softDeleteValue) {
        this.softDeleteValue = softDeleteValue;
    }
}

