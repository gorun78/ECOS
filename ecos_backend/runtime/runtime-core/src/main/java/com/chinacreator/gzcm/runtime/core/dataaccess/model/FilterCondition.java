package com.chinacreator.gzcm.runtime.core.dataaccess.model;

import java.util.List;

/**
 * 杩囨护鏉′欢妯″瀷
 * 鏀寔澶氱鏉′欢绫诲瀷鍜岄€昏緫缁勫悎
 * 
 * @author CDRC Runtime Team
 */
public class FilterCondition {
    
    /**
     * 鏉′欢绫诲瀷鏋氫妇
     */
    public enum ConditionType {
        EQUALS("绛変簬"),
        NOT_EQUALS("涓嶇瓑浜?"),
        GREATER_THAN("澶т簬"),
        GREATER_THAN_OR_EQUAL("澶т簬绛変簬"),
        LESS_THAN("灏忎簬"),
        LESS_THAN_OR_EQUAL("灏忎簬绛変簬"),
        IN("鍖呭惈"),
        NOT_IN("涓嶅寘鍚?"),
        LIKE("妯＄硦鍖归厤"),
        NOT_LIKE("涓嶆ā绯婂尮閰?"),
        IS_NULL("涓虹┖"),
        IS_NOT_NULL("涓嶄负绌?"),
        BETWEEN("鑼冨洿"),
        AND("閫昏緫涓?"),
        OR("閫昏緫鎴?"),
        NOT("閫昏緫闈?");
        
        private String description;
        
        ConditionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 鏉′欢绫诲瀷
     */
    private ConditionType type;
    
    /**
     * 瀛楁鍚?
     */
    private String field;
    
    /**
     * 瀛楁鍊硷紙鐢ㄤ簬鍗曞€兼潯浠讹級
     */
    private Object value;
    
    /**
     * 瀛楁鍊煎垪琛紙鐢ㄤ簬IN銆丯OT_IN绛夋潯浠讹級
     */
    private List<Object> values;
    
    /**
     * 璧峰鍊硷紙鐢ㄤ簬BETWEEN鏉′欢锛?
     */
    private Object startValue;
    
    /**
     * 缁撴潫鍊硷紙鐢ㄤ簬BETWEEN鏉′欢锛?
     */
    private Object endValue;
    
    /**
     * 瀛愭潯浠跺垪琛紙鐢ㄤ簬AND銆丱R銆丯OT閫昏緫缁勫悎锛?
     */
    private List<FilterCondition> conditions;
    
    // Getters and Setters
    
    public ConditionType getType() {
        return type;
    }
    
    public void setType(ConditionType type) {
        this.type = type;
    }
    
    public String getField() {
        return field;
    }
    
    public void setField(String field) {
        this.field = field;
    }
    
    public Object getValue() {
        return value;
    }
    
    public void setValue(Object value) {
        this.value = value;
    }
    
    public List<Object> getValues() {
        return values;
    }
    
    public void setValues(List<Object> values) {
        this.values = values;
    }
    
    public Object getStartValue() {
        return startValue;
    }
    
    public void setStartValue(Object startValue) {
        this.startValue = startValue;
    }
    
    public Object getEndValue() {
        return endValue;
    }
    
    public void setEndValue(Object endValue) {
        this.endValue = endValue;
    }
    
    public List<FilterCondition> getConditions() {
        return conditions;
    }
    
    public void setConditions(List<FilterCondition> conditions) {
        this.conditions = conditions;
    }
    
    /**
     * 鍒涘缓绛変簬鏉′欢
     */
    public static FilterCondition equals(String field, Object value) {
        FilterCondition condition = new FilterCondition();
        condition.setType(ConditionType.EQUALS);
        condition.setField(field);
        condition.setValue(value);
        return condition;
    }
    
    /**
     * 鍒涘缓AND閫昏緫缁勫悎
     */
    public static FilterCondition and(FilterCondition... conditions) {
        FilterCondition condition = new FilterCondition();
        condition.setType(ConditionType.AND);
        condition.setConditions(java.util.Arrays.asList(conditions));
        return condition;
    }
    
    /**
     * 鍒涘缓OR閫昏緫缁勫悎
     */
    public static FilterCondition or(FilterCondition... conditions) {
        FilterCondition condition = new FilterCondition();
        condition.setType(ConditionType.OR);
        condition.setConditions(java.util.Arrays.asList(conditions));
        return condition;
    }
}

