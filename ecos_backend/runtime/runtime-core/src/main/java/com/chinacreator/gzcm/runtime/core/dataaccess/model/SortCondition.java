package com.chinacreator.gzcm.runtime.core.dataaccess.model;

/**
 * 鎺掑簭鏉′欢妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class SortCondition {
    
    /**
     * 鎺掑簭鏂瑰悜鏋氫妇
     */
    public enum SortDirection {
        ASC("鍗囧簭"),
        DESC("闄嶅簭");
        
        private String description;
        
        SortDirection(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 鎺掑簭瀛楁
     */
    private String field;
    
    /**
     * 鎺掑簭鏂瑰悜
     */
    private SortDirection direction = SortDirection.ASC;
    
    /**
     * 鏋勯€犲嚱鏁?
     */
    public SortCondition() {
    }
    
    /**
     * 鏋勯€犲嚱鏁?
     * 
     * @param field 鎺掑簭瀛楁
     * @param direction 鎺掑簭鏂瑰悜
     */
    public SortCondition(String field, SortDirection direction) {
        this.field = field;
        this.direction = direction;
    }
    
    // Getters and Setters
    
    public String getField() {
        return field;
    }
    
    public void setField(String field) {
        this.field = field;
    }
    
    public SortDirection getDirection() {
        return direction;
    }
    
    public void setDirection(SortDirection direction) {
        this.direction = direction;
    }
}

