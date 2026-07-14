package com.chinacreator.gzcm.runtime.core.common.media.bean;

import java.io.Serializable;

/**
 * MediaLogExtBean - 媒体日志扩展Bean
 */
public class MediaLogExtBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String ext_id;
    private String log_id;
    private String password;
    private String service_identity;
    private String is_page;
    private String result_content;
    
    // Getters and setters
    public String getExt_id() {
        return ext_id;
    }
    
    public void setExt_id(String ext_id) {
        this.ext_id = ext_id;
    }
    
    public String getLog_id() {
        return log_id;
    }
    
    public void setLog_id(String log_id) {
        this.log_id = log_id;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getService_identity() {
        return service_identity;
    }
    
    public void setService_identity(String service_identity) {
        this.service_identity = service_identity;
    }
    
    public String getIs_page() {
        return is_page;
    }
    
    public void setIs_page(String is_page) {
        this.is_page = is_page;
    }
    
    public String getResult_content() {
        return result_content;
    }
    
    public void setResult_content(String result_content) {
        this.result_content = result_content;
    }
}

