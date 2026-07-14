package com.chinacreator.gzcm.runtime.core.metadata.bean;

import java.io.Serializable;

/**
 * 元数据Bean（占位实现）
 */
public class Metadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String name;
    private String code;
    private String description;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}
