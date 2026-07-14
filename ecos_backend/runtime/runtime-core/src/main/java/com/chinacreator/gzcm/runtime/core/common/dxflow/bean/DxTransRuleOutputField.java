package com.chinacreator.gzcm.runtime.core.common.dxflow.bean;

import java.io.Serializable;

/**
 * DxTransRuleOutputField - 数据转换规则输出字段Bean
 */
public class DxTransRuleOutputField implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String field_id;
    private String field_name;
    private String field_code;
    private String field_type;
    private String rule_id;
    private String output_object_id;
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
    
    public String getOutput_object_id() {
        return output_object_id;
    }
    
    public void setOutput_object_id(String output_object_id) {
        this.output_object_id = output_object_id;
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
    
    private String outputfield_id;
    private String cleanrule_id;
    private Integer precision;
    private Integer length;
    private String row_xpath;
    private String logic_data_type;
    private String pk_flag;
    private String inc_time_flag;
    private String ref_outputfield_id;
    
    public String getOutputfield_id() {
        return outputfield_id;
    }
    
    public void setOutputfield_id(String outputfield_id) {
        this.outputfield_id = outputfield_id;
    }
    
    public String getCleanrule_id() {
        return cleanrule_id;
    }
    
    public void setCleanrule_id(String cleanrule_id) {
        this.cleanrule_id = cleanrule_id;
    }
    
    public Integer getPrecision() {
        return precision;
    }
    
    public void setPrecision(Integer precision) {
        this.precision = precision;
    }
    
    public Integer getLength() {
        return length;
    }
    
    public void setLength(Integer length) {
        this.length = length;
    }
    
    public String getRow_xpath() {
        return row_xpath;
    }
    
    public void setRow_xpath(String row_xpath) {
        this.row_xpath = row_xpath;
    }
    
    public String getLogic_data_type() {
        return logic_data_type;
    }
    
    public void setLogic_data_type(String logic_data_type) {
        this.logic_data_type = logic_data_type;
    }
    
    public String getPk_flag() {
        return pk_flag;
    }
    
    public void setPk_flag(String pk_flag) {
        this.pk_flag = pk_flag;
    }
    
    public String getInc_time_flag() {
        return inc_time_flag;
    }
    
    public void setInc_time_flag(String inc_time_flag) {
        this.inc_time_flag = inc_time_flag;
    }
    
    public String getRef_outputfield_id() {
        return ref_outputfield_id;
    }
    
    public void setRef_outputfield_id(String ref_outputfield_id) {
        this.ref_outputfield_id = ref_outputfield_id;
    }
}

