package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * DataShareRef bean class
 * TODO: Add proper implementation based on actual requirements
 */
public class DataShareRef implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String share_ref_id;
    private String schedule_id;
    private String object_id;
    private String status;
    private String org_name;
    private String is_create_output_object;
    private String output_object_id;
    
    // Getters and setters
    public String getShare_ref_id() {
        return share_ref_id;
    }
    
    public void setShare_ref_id(String share_ref_id) {
        this.share_ref_id = share_ref_id;
    }
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getOrg_name() {
        return org_name;
    }
    
    public void setOrg_name(String org_name) {
        this.org_name = org_name;
    }
    
    public String getIs_create_output_object() {
        return is_create_output_object;
    }
    
    public void setIs_create_output_object(String is_create_output_object) {
        this.is_create_output_object = is_create_output_object;
    }
    
    public String getOutput_object_id() {
        return output_object_id;
    }
    
    public void setOutput_object_id(String output_object_id) {
        this.output_object_id = output_object_id;
    }
    
    private String out_errorrow;
    
    public String getOut_errorrow() {
        return out_errorrow;
    }
    
    public void setOut_errorrow(String out_errorrow) {
        this.out_errorrow = out_errorrow;
    }
    
    private Integer out_maxerror_size;
    
    public Integer getOut_maxerror_size() {
        return out_maxerror_size;
    }
    
    public void setOut_maxerror_size(Integer out_maxerror_size) {
        this.out_maxerror_size = out_maxerror_size;
    }
    
    private String dboutput_type;
    
    public String getDboutput_type() {
        return dboutput_type;
    }
    
    public void setDboutput_type(String dboutput_type) {
        this.dboutput_type = dboutput_type;
    }
    
    private String input_object_id;
    
    public String getInput_object_id() {
        return input_object_id;
    }
    
    public void setInput_object_id(String input_object_id) {
        this.input_object_id = input_object_id;
    }
    
    private String output_object_name;
    
    public String getOutput_object_name() {
        return output_object_name;
    }
    
    public void setOutput_object_name(String output_object_name) {
        this.output_object_name = output_object_name;
    }
    
    private String ds_id;
    
    public String getDs_id() {
        return ds_id;
    }
    
    public void setDs_id(String ds_id) {
        this.ds_id = ds_id;
    }
    
    private String param_table_name;
    
    public String getParam_table_name() {
        return param_table_name;
    }
    
    public void setParam_table_name(String param_table_name) {
        this.param_table_name = param_table_name;
    }
    
    private String new_flag;
    
    public String getNew_flag() {
        return new_flag;
    }
    
    public void setNew_flag(String new_flag) {
        this.new_flag = new_flag;
    }
    
    private String node_id;
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
    
    private String swith_value;
    
    public String getSwith_value() {
        return swith_value;
    }
    
    public void setSwith_value(String swith_value) {
        this.swith_value = swith_value;
    }
    
    private Integer char_multiple;
    
    public Integer getChar_multiple() {
        return char_multiple;
    }
    
    public void setChar_multiple(Integer char_multiple) {
        this.char_multiple = char_multiple;
    }
    
    private String object_type;
    private String object_name;
    private String input_ds_id;
    
    public String getObject_type() {
        return object_type;
    }
    
    public void setObject_type(String object_type) {
        this.object_type = object_type;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    public String getInput_ds_id() {
        return input_ds_id;
    }
    
    public void setInput_ds_id(String input_ds_id) {
        this.input_ds_id = input_ds_id;
    }
    
    private String recieve_org_id;
    
    public String getRecieve_org_id() {
        return recieve_org_id;
    }
    
    public void setRecieve_org_id(String recieve_org_id) {
        this.recieve_org_id = recieve_org_id;
    }
}

