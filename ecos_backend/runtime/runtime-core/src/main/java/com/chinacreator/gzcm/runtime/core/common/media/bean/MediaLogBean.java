package com.chinacreator.gzcm.runtime.core.common.media.bean;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * MediaLogBean - 媒体日志Bean
 */
public class MediaLogBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String log_id;
    private String schedule_name;
    private String schedule_id;
    private String request_id;
    private Timestamp start_time;
    private Timestamp end_time;
    private String success_flag;
    private String error_code;
    private String error_detail_msg;
    private String request_account;
    private String conditions;
    private String order_by;
    private String required_items;
    private Integer page_index;
    private Integer page_size;
    private Integer result_size;
    private Integer total_size;
    private String org_name;
    private String org_id;
    
    // Getters and setters
    public String getLog_id() {
        return log_id;
    }
    
    public void setLog_id(String log_id) {
        this.log_id = log_id;
    }
    
    public String getSchedule_name() {
        return schedule_name;
    }
    
    public void setSchedule_name(String schedule_name) {
        this.schedule_name = schedule_name;
    }
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public String getRequest_id() {
        return request_id;
    }
    
    public void setRequest_id(String request_id) {
        this.request_id = request_id;
    }
    
    public Timestamp getStart_time() {
        return start_time;
    }
    
    public void setStart_time(Timestamp start_time) {
        this.start_time = start_time;
    }
    
    public Timestamp getEnd_time() {
        return end_time;
    }
    
    public void setEnd_time(Timestamp end_time) {
        this.end_time = end_time;
    }
    
    public String getSuccess_flag() {
        return success_flag;
    }
    
    public void setSuccess_flag(String success_flag) {
        this.success_flag = success_flag;
    }
    
    public String getError_code() {
        return error_code;
    }
    
    public void setError_code(String error_code) {
        this.error_code = error_code;
    }
    
    public String getError_detail_msg() {
        return error_detail_msg;
    }
    
    public void setError_detail_msg(String error_detail_msg) {
        this.error_detail_msg = error_detail_msg;
    }
    
    public String getRequest_account() {
        return request_account;
    }
    
    public void setRequest_account(String request_account) {
        this.request_account = request_account;
    }
    
    public String getConditions() {
        return conditions;
    }
    
    public void setConditions(String conditions) {
        this.conditions = conditions;
    }
    
    public String getOrder_by() {
        return order_by;
    }
    
    public void setOrder_by(String order_by) {
        this.order_by = order_by;
    }
    
    public String getRequired_items() {
        return required_items;
    }
    
    public void setRequired_items(String required_items) {
        this.required_items = required_items;
    }
    
    public Integer getPage_index() {
        return page_index;
    }
    
    public void setPage_index(Integer page_index) {
        this.page_index = page_index;
    }
    
    public Integer getPage_size() {
        return page_size;
    }
    
    public void setPage_size(Integer page_size) {
        this.page_size = page_size;
    }
    
    public Integer getResult_size() {
        return result_size;
    }
    
    public void setResult_size(Integer result_size) {
        this.result_size = result_size;
    }
    
    public Integer getTotal_size() {
        return total_size;
    }
    
    public void setTotal_size(Integer total_size) {
        this.total_size = total_size;
    }
    
    public String getOrg_name() {
        return org_name;
    }
    
    public void setOrg_name(String org_name) {
        this.org_name = org_name;
    }
    
    public String getOrg_id() {
        return org_id;
    }
    
    public void setOrg_id(String org_id) {
        this.org_id = org_id;
    }
}

