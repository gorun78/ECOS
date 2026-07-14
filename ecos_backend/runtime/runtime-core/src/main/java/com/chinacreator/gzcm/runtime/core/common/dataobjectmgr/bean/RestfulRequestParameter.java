package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;
import java.util.Map;

/**
 * RestfulRequestParameter - RESTful请求参数Bean
 */
public class RestfulRequestParameter implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String url;
    private String method;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private String body;
    private String contentType;
    private Integer timeout;
    private String authentication;
    private String username;
    private String password;
    
    // Getters and setters
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public Map<String, String> getQueryParams() {
        return queryParams;
    }
    
    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }
    
    public String getBody() {
        return body;
    }
    
    public void setBody(String body) {
        this.body = body;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public Integer getTimeout() {
        return timeout;
    }
    
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
    
    public String getAuthentication() {
        return authentication;
    }
    
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    private String object_id;
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    private String request_param_type;
    
    public String getRequest_param_type() {
        return request_param_type;
    }
    
    public void setRequest_param_type(String request_param_type) {
        this.request_param_type = request_param_type;
    }
    
    private String request_param_id;
    
    public String getRequest_param_id() {
        return request_param_id;
    }
    
    public void setRequest_param_id(String request_param_id) {
        this.request_param_id = request_param_id;
    }
    
    private String request_param_value;
    
    public String getRequest_param_value() {
        return request_param_value;
    }
    
    public void setRequest_param_value(String request_param_value) {
        this.request_param_value = request_param_value;
    }
    
    private String request_param_format;
    
    public String getRequest_param_format() {
        return request_param_format;
    }
    
    public void setRequest_param_format(String request_param_format) {
        this.request_param_format = request_param_format;
    }
    
    private String request_param_name;
    
    public String getRequest_param_name() {
        return request_param_name;
    }
    
    public void setRequest_param_name(String request_param_name) {
        this.request_param_name = request_param_name;
    }
    
    private String request_param_path;
    
    public String getRequest_param_path() {
        return request_param_path;
    }
    
    public void setRequest_param_path(String request_param_path) {
        this.request_param_path = request_param_path;
    }
}

