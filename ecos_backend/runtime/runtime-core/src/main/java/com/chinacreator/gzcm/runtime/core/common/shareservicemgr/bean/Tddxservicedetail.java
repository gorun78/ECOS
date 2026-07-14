package com.chinacreator.gzcm.runtime.core.common.shareservicemgr.bean;

import java.io.Serializable;

/**
 * Tddxservicedetail - 共享服务详情Bean类
 * 用于数据共享服务详情管理
 */
public class Tddxservicedetail implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String service_detail_id;
    private String service_id;
    private String column_id;
    private String column_code;
    private String column_name;
    private String isselect;
    private String isreturn;
    private String isorder;
    private String pk_flag;
    private String inc_time_flag;
    private String column_type;
    private String object_name;
    private String object_id;
    
    // Getters and setters
    public String getService_detail_id() {
        return service_detail_id;
    }
    
    public void setService_detail_id(String service_detail_id) {
        this.service_detail_id = service_detail_id;
    }
    
    public String getService_id() {
        return service_id;
    }
    
    public void setService_id(String service_id) {
        this.service_id = service_id;
    }
    
    public String getColumn_id() {
        return column_id;
    }
    
    public void setColumn_id(String column_id) {
        this.column_id = column_id;
    }
    
    public String getColumn_code() {
        return column_code;
    }
    
    public void setColumn_code(String column_code) {
        this.column_code = column_code;
    }
    
    public String getColumn_name() {
        return column_name;
    }
    
    public void setColumn_name(String column_name) {
        this.column_name = column_name;
    }
    
    public String getIsselect() {
        return isselect;
    }
    
    public void setIsselect(String isselect) {
        this.isselect = isselect;
    }
    
    public String getIsreturn() {
        return isreturn;
    }
    
    public void setIsreturn(String isreturn) {
        this.isreturn = isreturn;
    }
    
    public String getIsorder() {
        return isorder;
    }
    
    public void setIsorder(String isorder) {
        this.isorder = isorder;
    }
    
    public String getPk_flag() {
        return pk_flag;
    }
    
    public void setPk_flag(String pk_flag) {
        this.pk_flag = pk_flag;
    }
    
    public String getInc_time_flag() {
        return inc_time_flag;
    }
    
    public void setInc_time_flag(String inc_time_flag) {
        this.inc_time_flag = inc_time_flag;
    }
    
    public String getColumn_type() {
        return column_type;
    }
    
    public void setColumn_type(String column_type) {
        this.column_type = column_type;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    // Additional fields for compatibility
    private String select_rule;
    private String return_label;
    private String is_additional_field;
    private String additional_expr;
    private String db_dtata_type;
    
    public String getSelect_rule() {
        return select_rule;
    }
    
    public void setSelect_rule(String select_rule) {
        this.select_rule = select_rule;
    }
    
    public String getReturn_label() {
        return return_label;
    }
    
    public void setReturn_label(String return_label) {
        this.return_label = return_label;
    }
    
    public String getIs_additional_field() {
        return is_additional_field;
    }
    
    public void setIs_additional_field(String is_additional_field) {
        this.is_additional_field = is_additional_field;
    }
    
    public String getAdditional_expr() {
        return additional_expr;
    }
    
    public void setAdditional_expr(String additional_expr) {
        this.additional_expr = additional_expr;
    }
    
    public String getDb_dtata_type() {
        return db_dtata_type;
    }
    
    public void setDb_dtata_type(String db_dtata_type) {
        this.db_dtata_type = db_dtata_type;
    }
    
    // Alias method for compatibility (fix typo)
    public String getDb_data_type() {
        return db_dtata_type;
    }
    
    public void setDb_data_type(String db_data_type) {
        this.db_dtata_type = db_data_type;
    }
    
    // Additional fields for compatibility
    private String schedule_id;
    private Integer sn;
    
    public String getSchedule_id() {
        return schedule_id != null ? schedule_id : service_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
        // Also set service_id if it's null
        if (this.service_id == null) {
            this.service_id = schedule_id;
        }
    }
    
    public Integer getSn() {
        return sn;
    }
    
    public void setSn(Integer sn) {
        this.sn = sn;
    }
    
    // Overloaded setter for int compatibility
    public void setSn(int sn) {
        this.sn = sn;
    }
    
    // Length field for compatibility
    private Integer length;
    
    public Integer getLength() {
        return length;
    }
    
    public void setLength(Integer length) {
        this.length = length;
    }
    
    // Overloaded setter for int compatibility
    public void setLength(int length) {
        this.length = length;
    }
}

