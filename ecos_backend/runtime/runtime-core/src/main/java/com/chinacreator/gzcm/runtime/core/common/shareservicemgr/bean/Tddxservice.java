package com.chinacreator.gzcm.runtime.core.common.shareservicemgr.bean;

import java.io.Serializable;

/**
 * Tddxservice - 共享服务Bean类
 * 用于数据共享服务管理
 */
public class Tddxservice implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String service_id;
    private String service_name;
    private String service_code;
    private String service_type;
    private String service_status;
    private String data_object_id;
    private String data_object_name;
    private String description;
    private String create_time;
    private String modify_time;
    private String creator;
    private String modifier;
    
    // Getters and setters
    public String getService_id() {
        return service_id;
    }
    
    public void setService_id(String service_id) {
        this.service_id = service_id;
    }
    
    public String getService_name() {
        return service_name;
    }
    
    public void setService_name(String service_name) {
        this.service_name = service_name;
    }
    
    public String getService_code() {
        return service_code;
    }
    
    public void setService_code(String service_code) {
        this.service_code = service_code;
    }
    
    public String getService_type() {
        return service_type;
    }
    
    public void setService_type(String service_type) {
        this.service_type = service_type;
    }
    
    public String getService_status() {
        return service_status;
    }
    
    public void setService_status(String service_status) {
        this.service_status = service_status;
    }
    
    /**
     * 获取注册状态（兼容方法）
     * @return 注册状态
     */
    public String getRegist_status() {
        return service_status;
    }
    
    /**
     * 设置注册状态（兼容方法）
     * @param regist_status 注册状态
     */
    public void setRegist_status(String regist_status) {
        this.service_status = regist_status;
    }
    
    public String getData_object_id() {
        return data_object_id;
    }
    
    public void setData_object_id(String data_object_id) {
        this.data_object_id = data_object_id;
    }
    
    /**
     * Alias for getData_object_id() for compatibility
     */
    public String getObject_id() {
        return data_object_id;
    }
    
    /**
     * Alias for setData_object_id() for compatibility
     */
    public void setObject_id(String object_id) {
        this.data_object_id = object_id;
    }
    
    public String getData_object_name() {
        return data_object_name;
    }
    
    public void setData_object_name(String data_object_name) {
        this.data_object_name = data_object_name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getCreate_time() {
        return create_time;
    }
    
    public void setCreate_time(String create_time) {
        this.create_time = create_time;
    }
    
    public String getModify_time() {
        return modify_time;
    }
    
    public void setModify_time(String modify_time) {
        this.modify_time = modify_time;
    }
    
    public String getCreator() {
        return creator;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public String getModifier() {
        return modifier;
    }
    
    public void setModifier(String modifier) {
        this.modifier = modifier;
    }
    
    // Schedule ID field (for compatibility with legacy code)
    private String schedule_id;
    
    /**
     * Get schedule ID (alias for service_id for compatibility)
     */
    public String getSchedule_id() {
        return schedule_id != null ? schedule_id : service_id;
    }
    
    /**
     * Set schedule ID
     */
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
        // Also set service_id if it's null
        if (this.service_id == null) {
            this.service_id = schedule_id;
        }
    }
    
    // Additional fields for compatibility
    private String query_type;
    private String ds_id;
    private String status;
    private String filter_sql;
    
    public String getQuery_type() {
        return query_type;
    }
    
    public void setQuery_type(String query_type) {
        this.query_type = query_type;
    }
    
    public String getDs_id() {
        return ds_id;
    }
    
    public void setDs_id(String ds_id) {
        this.ds_id = ds_id;
    }
    
    public String getStatus() {
        return status != null ? status : service_status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.service_status = status;
    }
    
    public String getFilter_sql() {
        return filter_sql;
    }
    
    public void setFilter_sql(String filter_sql) {
        this.filter_sql = filter_sql;
    }
    
    // Additional field for schedule name
    private String schedule_name;
    
    public String getSchedule_name() {
        return schedule_name;
    }
    
    public void setSchedule_name(String schedule_name) {
        this.schedule_name = schedule_name;
    }
    
    // Additional field for isopen
    private String isopen;
    
    public String getIsopen() {
        return isopen;
    }
    
    public void setIsopen(String isopen) {
        this.isopen = isopen;
    }
    
    // Overloaded setters for Timestamp compatibility
    public void setCreate_time(java.sql.Timestamp timestamp) {
        if (timestamp != null) {
            this.create_time = timestamp.toString();
        }
    }
    
    public void setModify_time(java.sql.Timestamp timestamp) {
        if (timestamp != null) {
            this.modify_time = timestamp.toString();
        }
    }
}

