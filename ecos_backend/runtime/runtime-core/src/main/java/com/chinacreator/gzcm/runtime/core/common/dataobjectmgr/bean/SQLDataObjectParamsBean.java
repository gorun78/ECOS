package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * SQLDataObjectParamsBean class
 * TODO: Add proper implementation based on actual requirements
 */
public class SQLDataObjectParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String sql;
    
    public String getSql() {
        return sql;
    }
    
    public void setSql(String sql) {
        this.sql = sql;
    }
    
    private String paramId;
    
    public String getParamId() {
        return paramId;
    }
    
    public void setParamId(String paramId) {
        this.paramId = paramId;
    }
}

