package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * FieldMappingBean - 字段映射Bean类
 * 用于数据对象字段映射配置
 */
public class FieldMappingBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String mapping_id;
    private String share_ref_id;
    private String source_column_id;
    private String source_column_code;
    private String source_column_name;
    private String target_column_id;
    private String target_column_code;
    private String target_column_name;
    private String mapping_type;
    private String mapping_expression;
    private String default_value;
    private String description;
    private String field_grant_id;
    private String service_detail_id;
    private String column_id;
    
    // Getters and setters
    public String getMapping_id() {
        return mapping_id;
    }
    
    public void setMapping_id(String mapping_id) {
        this.mapping_id = mapping_id;
    }
    
    public String getShare_ref_id() {
        return share_ref_id;
    }
    
    public void setShare_ref_id(String share_ref_id) {
        this.share_ref_id = share_ref_id;
    }
    
    public String getSource_column_id() {
        return source_column_id;
    }
    
    public void setSource_column_id(String source_column_id) {
        this.source_column_id = source_column_id;
    }
    
    public String getSource_column_code() {
        return source_column_code;
    }
    
    public void setSource_column_code(String source_column_code) {
        this.source_column_code = source_column_code;
    }
    
    public String getSource_column_name() {
        return source_column_name;
    }
    
    public void setSource_column_name(String source_column_name) {
        this.source_column_name = source_column_name;
    }
    
    public String getTarget_column_id() {
        return target_column_id;
    }
    
    public void setTarget_column_id(String target_column_id) {
        this.target_column_id = target_column_id;
    }
    
    public String getTarget_column_code() {
        return target_column_code;
    }
    
    public void setTarget_column_code(String target_column_code) {
        this.target_column_code = target_column_code;
    }
    
    public String getTarget_column_name() {
        return target_column_name;
    }
    
    public void setTarget_column_name(String target_column_name) {
        this.target_column_name = target_column_name;
    }
    
    public String getMapping_type() {
        return mapping_type;
    }
    
    public void setMapping_type(String mapping_type) {
        this.mapping_type = mapping_type;
    }
    
    public String getMapping_expression() {
        return mapping_expression;
    }
    
    public void setMapping_expression(String mapping_expression) {
        this.mapping_expression = mapping_expression;
    }
    
    public String getDefault_value() {
        return default_value;
    }
    
    public void setDefault_value(String default_value) {
        this.default_value = default_value;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getField_grant_id() {
        return field_grant_id;
    }
    
    public void setField_grant_id(String field_grant_id) {
        this.field_grant_id = field_grant_id;
    }
    
    public String getService_detail_id() {
        return service_detail_id;
    }
    
    public void setService_detail_id(String service_detail_id) {
        this.service_detail_id = service_detail_id;
    }
    
    public String getColumn_id() {
        return column_id;
    }
    
    public void setColumn_id(String column_id) {
        this.column_id = column_id;
    }
    
    private String input_column_code;
    private String input_column_id;
    private String output_column_id;
    private String output_column_code;
    private String output_column_name;
    
    public String getInput_column_code() {
        return input_column_code;
    }
    
    public void setInput_column_code(String input_column_code) {
        this.input_column_code = input_column_code;
    }
    
    public String getInput_column_id() {
        return input_column_id;
    }
    
    public void setInput_column_id(String input_column_id) {
        this.input_column_id = input_column_id;
    }
    
    public String getOutput_column_id() {
        return output_column_id;
    }
    
    public void setOutput_column_id(String output_column_id) {
        this.output_column_id = output_column_id;
    }
    
    public String getOutput_column_code() {
        return output_column_code;
    }
    
    public void setOutput_column_code(String output_column_code) {
        this.output_column_code = output_column_code;
    }
    
    public String getOutput_column_name() {
        return output_column_name;
    }
    
    public void setOutput_column_name(String output_column_name) {
        this.output_column_name = output_column_name;
    }
}

