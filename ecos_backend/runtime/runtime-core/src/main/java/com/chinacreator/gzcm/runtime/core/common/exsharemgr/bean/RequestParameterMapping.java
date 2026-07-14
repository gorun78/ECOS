package com.chinacreator.gzcm.runtime.core.common.exsharemgr.bean;

import java.io.Serializable;

/**
 * RequestParameterMapping - 请求参数映射Bean类
 * 用于定义请求参数与数据列之间的映射关系
 */
public class RequestParameterMapping implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String map_id;
    private String map_def_id;
    private String request_param_name;
    private String column_code;
    private String column_id;
    private String column_name;
    private String data_type;
    private String default_value;
    
    // Getters and setters
    public String getMap_id() {
        return map_id;
    }
    
    public void setMap_id(String map_id) {
        this.map_id = map_id;
    }
    
    public String getMap_def_id() {
        return map_def_id;
    }
    
    public void setMap_def_id(String map_def_id) {
        this.map_def_id = map_def_id;
    }
    
    public String getRequest_param_name() {
        return request_param_name;
    }
    
    public void setRequest_param_name(String request_param_name) {
        this.request_param_name = request_param_name;
    }
    
    public String getColumn_code() {
        return column_code;
    }
    
    public void setColumn_code(String column_code) {
        this.column_code = column_code;
    }
    
    public String getColumn_id() {
        return column_id;
    }
    
    public void setColumn_id(String column_id) {
        this.column_id = column_id;
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
    
    public String getDefault_value() {
        return default_value;
    }
    
    public void setDefault_value(String default_value) {
        this.default_value = default_value;
    }
    
    private String request_param_id;
    
    public String getRequest_param_id() {
        return request_param_id;
    }
    
    public void setRequest_param_id(String request_param_id) {
        this.request_param_id = request_param_id;
    }
}

