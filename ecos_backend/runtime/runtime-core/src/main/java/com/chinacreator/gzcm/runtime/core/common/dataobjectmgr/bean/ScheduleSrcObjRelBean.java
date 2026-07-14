package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * ScheduleSrcObjRelBean - 方案源对象关系Bean
 */
public class ScheduleSrcObjRelBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String REL_ID;
    private String REL_NAME;
    private String REL_OBJECT_ID;
    private String BE_REL_OBJECT_ID;
    private String REL_OBJECT_NAME;
    private String BE_REL_OBJECT_NAME;
    private String REL_TYPE;
    private String REL_OBJECT_COLIDS;
    private String BE_REL_OBJECT_COLIDS;
    private String REL_OBJECT_COLCODES;
    private String BE_REL_OBJECT_COLCODES;
    private String SCHEDULE_ID;
    
    // Getters and setters
    public String getREL_ID() {
        return REL_ID;
    }
    
    public void setREL_ID(String REL_ID) {
        this.REL_ID = REL_ID;
    }
    
    public String getREL_NAME() {
        return REL_NAME;
    }
    
    public void setREL_NAME(String REL_NAME) {
        this.REL_NAME = REL_NAME;
    }
    
    public String getREL_OBJECT_ID() {
        return REL_OBJECT_ID;
    }
    
    public void setREL_OBJECT_ID(String REL_OBJECT_ID) {
        this.REL_OBJECT_ID = REL_OBJECT_ID;
    }
    
    public String getBE_REL_OBJECT_ID() {
        return BE_REL_OBJECT_ID;
    }
    
    public void setBE_REL_OBJECT_ID(String BE_REL_OBJECT_ID) {
        this.BE_REL_OBJECT_ID = BE_REL_OBJECT_ID;
    }
    
    public String getREL_OBJECT_NAME() {
        return REL_OBJECT_NAME;
    }
    
    public void setREL_OBJECT_NAME(String REL_OBJECT_NAME) {
        this.REL_OBJECT_NAME = REL_OBJECT_NAME;
    }
    
    public String getBE_REL_OBJECT_NAME() {
        return BE_REL_OBJECT_NAME;
    }
    
    public void setBE_REL_OBJECT_NAME(String BE_REL_OBJECT_NAME) {
        this.BE_REL_OBJECT_NAME = BE_REL_OBJECT_NAME;
    }
    
    public String getREL_TYPE() {
        return REL_TYPE;
    }
    
    public void setREL_TYPE(String REL_TYPE) {
        this.REL_TYPE = REL_TYPE;
    }
    
    public String getREL_OBJECT_COLIDS() {
        return REL_OBJECT_COLIDS;
    }
    
    public void setREL_OBJECT_COLIDS(String REL_OBJECT_COLIDS) {
        this.REL_OBJECT_COLIDS = REL_OBJECT_COLIDS;
    }
    
    public String getBE_REL_OBJECT_COLIDS() {
        return BE_REL_OBJECT_COLIDS;
    }
    
    public void setBE_REL_OBJECT_COLIDS(String BE_REL_OBJECT_COLIDS) {
        this.BE_REL_OBJECT_COLIDS = BE_REL_OBJECT_COLIDS;
    }
    
    public String getREL_OBJECT_COLCODES() {
        return REL_OBJECT_COLCODES;
    }
    
    public void setREL_OBJECT_COLCODES(String REL_OBJECT_COLCODES) {
        this.REL_OBJECT_COLCODES = REL_OBJECT_COLCODES;
    }
    
    public String getBE_REL_OBJECT_COLCODES() {
        return BE_REL_OBJECT_COLCODES;
    }
    
    public void setBE_REL_OBJECT_COLCODES(String BE_REL_OBJECT_COLCODES) {
        this.BE_REL_OBJECT_COLCODES = BE_REL_OBJECT_COLCODES;
    }
    
    public String getSCHEDULE_ID() {
        return SCHEDULE_ID;
    }
    
    public void setSCHEDULE_ID(String SCHEDULE_ID) {
        this.SCHEDULE_ID = SCHEDULE_ID;
    }
}

