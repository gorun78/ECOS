package com.chinacreator.gzcm.runtime.core.common.exsharemgr.bean;

import java.io.Serializable;

/**
 * ScheduleFieldItem - 方案字段项Bean
 */
public class ScheduleFieldItem implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String service_detail_id;
    private String schedule_id;
    private String column_id;
    private String column_code;
    private String column_name;
    private String column_code_rename;
    private String data_element_id;
    private String isselect;
    private String isreturn;
    private String isorder;
    private String pk_flag;
    private String inc_time_flag;
    private String column_type;
    private String object_name;
    private String object_id;
    
    // Getters and setters
    public String getService_detail_id() {
        return service_detail_id;
    }
    
    public void setService_detail_id(String service_detail_id) {
        this.service_detail_id = service_detail_id;
    }
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
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
    
    public String getColumn_code_rename() {
        return column_code_rename;
    }
    
    public void setColumn_code_rename(String column_code_rename) {
        this.column_code_rename = column_code_rename;
    }
    
    public String getData_element_id() {
        return data_element_id;
    }
    
    public void setData_element_id(String data_element_id) {
        this.data_element_id = data_element_id;
    }
    
    public String getIsselect() {
        return isselect;
    }
    
    public void setIsselect(String isselect) {
        this.isselect = isselect;
    }
    
    public String getIsreturn() {
        return isreturn;
    }
    
    public void setIsreturn(String isreturn) {
        this.isreturn = isreturn;
    }
    
    public String getIsorder() {
        return isorder;
    }
    
    public void setIsorder(String isorder) {
        this.isorder = isorder;
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
    
    public String getColumn_type() {
        return column_type;
    }
    
    public void setColumn_type(String column_type) {
        this.column_type = column_type;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
}

