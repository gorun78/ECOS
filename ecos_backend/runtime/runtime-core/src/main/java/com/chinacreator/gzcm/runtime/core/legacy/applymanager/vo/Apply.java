package com.chinacreator.gzcm.runtime.core.legacy.applymanager.vo;

import java.io.Serializable;

/**
 * 应用Bean
 */
public class Apply implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String dbname;
    private int enablestatus;
    private String apply_id;
    private String apply_name;
    
    public String getDbname() {
        return dbname;
    }
    
    public void setDbname(String dbname) {
        this.dbname = dbname;
    }
    
    public int getEnablestatus() {
        return enablestatus;
    }
    
    public void setEnablestatus(int enablestatus) {
        this.enablestatus = enablestatus;
    }
    
    public String getApply_id() {
        return apply_id;
    }
    
    public void setApply_id(String apply_id) {
        this.apply_id = apply_id;
    }
    
    public String getApply_name() {
        return apply_name;
    }
    
    public void setApply_name(String apply_name) {
        this.apply_name = apply_name;
    }
}
