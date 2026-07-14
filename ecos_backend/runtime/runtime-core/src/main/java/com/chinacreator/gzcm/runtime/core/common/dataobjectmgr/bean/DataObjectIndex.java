package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * 数据对象索引 Bean
 */
public class DataObjectIndex implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String index_id;
    private String object_id;
    private String index_name;
    private String index_col_ids;
    private String index_col_codes;
    private String index_type;
    private String index_remark;
    private String index_creator;
    private Timestamp index_createtime;
    
    public String getIndex_id() {
        return index_id;
    }
    
    public void setIndex_id(String index_id) {
        this.index_id = index_id;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public String getIndex_name() {
        return index_name;
    }
    
    public void setIndex_name(String index_name) {
        this.index_name = index_name;
    }
    
    public String getIndex_col_ids() {
        return index_col_ids;
    }
    
    public void setIndex_col_ids(String index_col_ids) {
        this.index_col_ids = index_col_ids;
    }
    
    public String getIndex_col_codes() {
        return index_col_codes;
    }
    
    public void setIndex_col_codes(String index_col_codes) {
        this.index_col_codes = index_col_codes;
    }
    
    public String getIndex_type() {
        return index_type;
    }
    
    public void setIndex_type(String index_type) {
        this.index_type = index_type;
    }
    
    public String getIndex_remark() {
        return index_remark;
    }
    
    public void setIndex_remark(String index_remark) {
        this.index_remark = index_remark;
    }
    
    public String getIndex_creator() {
        return index_creator;
    }
    
    public void setIndex_creator(String index_creator) {
        this.index_creator = index_creator;
    }
    
    public Timestamp getIndex_createtime() {
        return index_createtime;
    }
    
    public void setIndex_createtime(Timestamp index_createtime) {
        this.index_createtime = index_createtime;
    }
}
