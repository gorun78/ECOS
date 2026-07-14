package com.chinacreator.gzcm.runtime.core.common.dataclean.bean;

import java.io.Serializable;

/**
 * DataCleanRuleField - 数据清洗规则字段Bean
 */
public class DataCleanRuleField implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String field_id;
    private String field_name;
    private String field_code;
    private String field_type;
    private String rule_id;
    private String column_id;
    private String column_code;
    private String column_name;
    
    // Getters and setters
    public String getField_id() {
        return field_id;
    }
    
    public void setField_id(String field_id) {
        this.field_id = field_id;
    }
    
    public String getField_name() {
        return field_name;
    }
    
    public void setField_name(String field_name) {
        this.field_name = field_name;
    }
    
    public String getField_code() {
        return field_code;
    }
    
    public void setField_code(String field_code) {
        this.field_code = field_code;
    }
    
    public String getField_type() {
        return field_type;
    }
    
    public void setField_type(String field_type) {
        this.field_type = field_type;
    }
    
    public String getRule_id() {
        return rule_id;
    }
    
    public void setRule_id(String rule_id) {
        this.rule_id = rule_id;
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
    
    private String cleanrule_id;
    private String cleanrule_field_id;
    private String is_error_field;
    private Integer sort_sn;
    
    public String getCleanrule_id() {
        return cleanrule_id;
    }
    
    public void setCleanrule_id(String cleanrule_id) {
        this.cleanrule_id = cleanrule_id;
    }
    
    public String getCleanrule_field_id() {
        return cleanrule_field_id;
    }
    
    public void setCleanrule_field_id(String cleanrule_field_id) {
        this.cleanrule_field_id = cleanrule_field_id;
    }
    
    public String getIs_error_field() {
        return is_error_field;
    }
    
    public void setIs_error_field(String is_error_field) {
        this.is_error_field = is_error_field;
    }
    
    public Integer getSort_sn() {
        return sort_sn;
    }
    
    public void setSort_sn(Integer sort_sn) {
        this.sort_sn = sort_sn;
    }
    
    private String ref_outputfield_id;
    
    public String getRef_outputfield_id() {
        return ref_outputfield_id;
    }
    
    public void setRef_outputfield_id(String ref_outputfield_id) {
        this.ref_outputfield_id = ref_outputfield_id;
    }
}

