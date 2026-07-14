package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * DataObjectColumn bean class
 * TODO: Add proper implementation based on actual requirements
 */
public class DataObjectColumn implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String object_id;
    private String column_id;
    private String column_code;
    private String column_name;
    private String logic_data_type;
    private String pk_flag;
    
    // Getters and setters
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
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
    
    public String getLogic_data_type() {
        return logic_data_type;
    }
    
    public void setLogic_data_type(String logic_data_type) {
        this.logic_data_type = logic_data_type;
    }
    
    private String db_dtata_type;
    
    public String getDb_dtata_type() {
        return db_dtata_type;
    }
    
    public void setDb_dtata_type(String db_dtata_type) {
        this.db_dtata_type = db_dtata_type;
    }
    
    public String getPk_flag() {
        return pk_flag;
    }
    
    public void setPk_flag(String pk_flag) {
        this.pk_flag = pk_flag;
    }
    
    private String inc_time_flag;
    
    public String getInc_time_flag() {
        return inc_time_flag;
    }
    
    public void setInc_time_flag(String inc_time_flag) {
        this.inc_time_flag = inc_time_flag;
    }
    
    private String data_format;
    
    public String getData_format() {
        return data_format;
    }
    
    public void setData_format(String data_format) {
        this.data_format = data_format;
    }
    
    private String column_path;
    
    public String getColumn_path() {
        return column_path;
    }
    
    public void setColumn_path(String column_path) {
        this.column_path = column_path;
    }
    
    private Integer length;
    
    public Integer getLength() {
        return length;
    }
    
    public void setLength(Integer length) {
        this.length = length;
    }
    
    private Integer precision;
    
    public Integer getPrecision() {
        return precision;
    }
    
    public void setPrecision(Integer precision) {
        this.precision = precision;
    }
    
    private Integer sort_sn;
    
    public Integer getSort_sn() {
        return sort_sn;
    }
    
    public void setSort_sn(Integer sort_sn) {
        this.sort_sn = sort_sn;
    }
    
    private String column_code_rename;
    
    public String getColumn_code_rename() {
        return column_code_rename;
    }
    
    public void setColumn_code_rename(String column_code_rename) {
        this.column_code_rename = column_code_rename;
    }
    
    private String column_family;
    
    public String getColumn_family() {
        return column_family;
    }
    
    public void setColumn_family(String column_family) {
        this.column_family = column_family;
    }
    
    private String remark;
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    private String is_additional_field;
    
    public String getIs_additional_field() {
        return is_additional_field;
    }
    
    public void setIs_additional_field(String is_additional_field) {
        this.is_additional_field = is_additional_field;
    }
    
    private String additional_expr;
    
    public String getAdditional_expr() {
        return additional_expr;
    }
    
    public void setAdditional_expr(String additional_expr) {
        this.additional_expr = additional_expr;
    }
    
    private String initial_provider_flag;
    
    public String getInitial_provider_flag() {
        return initial_provider_flag;
    }
    
    public void setInitial_provider_flag(String initial_provider_flag) {
        this.initial_provider_flag = initial_provider_flag;
    }
    
    private String data_element_id;
    
    public String getData_element_id() {
        return data_element_id;
    }
    
    public void setData_element_id(String data_element_id) {
        this.data_element_id = data_element_id;
    }
    
    private String service_detail_id;
    
    public String getService_detail_id() {
        return service_detail_id;
    }
    
    public void setService_detail_id(String service_detail_id) {
        this.service_detail_id = service_detail_id;
    }
    
    private String fk_flag;
    
    public String getFk_flag() {
        return fk_flag;
    }
    
    public void setFk_flag(String fk_flag) {
        this.fk_flag = fk_flag;
    }
}

