package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * RestfulRequestHeader - RESTful请求头Bean
 */
public class RestfulRequestHeader implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String key;
    private String value;
    
    public RestfulRequestHeader() {}
    
    public RestfulRequestHeader(String key, String value) {
        this.key = key;
        this.value = value;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    private String object_id;
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    private String request_header_id;
    
    public String getRequest_header_id() {
        return request_header_id;
    }
    
    public void setRequest_header_id(String request_header_id) {
        this.request_header_id = request_header_id;
    }
    
    private String request_header_name;
    
    public String getRequest_header_name() {
        return request_header_name;
    }
    
    public void setRequest_header_name(String request_header_name) {
        this.request_header_name = request_header_name;
    }
    
    private String request_header_value;
    
    public String getRequest_header_value() {
        return request_header_value;
    }
    
    public void setRequest_header_value(String request_header_value) {
        this.request_header_value = request_header_value;
    }
}
