package com.chinacreator.gzcm.runtime.core.common.dataclean.bean;

import java.io.Serializable;

/**
 * DataCompareField - 数据比对字段Bean类
 * 用于数据清洗规则中的比对字段配置
 */
public class DataCompareField implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String field_id;
    private String column_id;
    private String column_code;
    private String column_name;
    private String data_type;
    private String compare_type;
    private String compare_value;
    private String object_id;
    private String object_name;
    
    // Getters and setters
    public String getField_id() {
        return field_id;
    }
    
    public void setField_id(String field_id) {
        this.field_id = field_id;
    }
    
    public String getColumn_id() {
        return column_id;
    }
    
    public void setColumn_id(String column_id) {
        this.column_id = column_id;
    }
    
    public String getColumn_code() {
        return column_code;
    }
    
    public void setColumn_code(String column_code) {
        this.column_code = column_code;
    }
    
    public String getColumn_name() {
        return column_name;
    }
    
    public void setColumn_name(String column_name) {
        this.column_name = column_name;
    }
    
    public String getData_type() {
        return data_type;
    }
    
    public void setData_type(String data_type) {
        this.data_type = data_type;
    }
    
    public String getCompare_type() {
        return compare_type;
    }
    
    public void setCompare_type(String compare_type) {
        this.compare_type = compare_type;
    }
    
    public String getCompare_value() {
        return compare_value;
    }
    
    public void setCompare_value(String compare_value) {
        this.compare_value = compare_value;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    private String alignment_field_id;
    private String cleanrule_id;
    private Integer sort_sn;
    
    public String getAlignment_field_id() {
        return alignment_field_id;
    }
    
    public void setAlignment_field_id(String alignment_field_id) {
        this.alignment_field_id = alignment_field_id;
    }
    
    public String getCleanrule_id() {
        return cleanrule_id;
    }
    
    public void setCleanrule_id(String cleanrule_id) {
        this.cleanrule_id = cleanrule_id;
    }
    
    public Integer getSort_sn() {
        return sort_sn;
    }
    
    public void setSort_sn(Integer sort_sn) {
        this.sort_sn = sort_sn;
    }
}

