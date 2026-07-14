package com.chinacreator.gzcm.runtime.core.common.logupload.bean;

import java.io.Serializable;

/**
 * EtlExcuteLog bean class
 * TODO: Add proper implementation based on actual requirements
 */
public class EtlExcuteLog implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String etl_excute_log_id;
    private String excute_log_id;
    private String schedule_id;
    private String share_ref_id;
    private String object_id;
    private String output_object_id;
    
    // Getters and setters
    public String getEtl_excute_log_id() {
        return etl_excute_log_id;
    }
    
    public void setEtl_excute_log_id(String etl_excute_log_id) {
        this.etl_excute_log_id = etl_excute_log_id;
    }
    
    public String getExcute_log_id() {
        return excute_log_id;
    }
    
    public void setExcute_log_id(String excute_log_id) {
        this.excute_log_id = excute_log_id;
    }
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public String getShare_ref_id() {
        return share_ref_id;
    }
    
    public void setShare_ref_id(String share_ref_id) {
        this.share_ref_id = share_ref_id;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public String getOutput_object_id() {
        return output_object_id;
    }
    
    public void setOutput_object_id(String output_object_id) {
        this.output_object_id = output_object_id;
    }
    
    // Additional fields for ETL execution log
    private String is_db_input;
    private String is_db_out;
    private Long input_error_lines;
    private Long output_lines;
    private String is_exception_stoped;
    private String exception_dtl;
    private Long input_lines;
    private Long error_lines;
    private Long read_error_lines;
    private Long insert_lines;
    private Long update_lines;
    private String flow_name;
    private String enddate;
    private String dx_type;
    
    public String getDx_type() {
        return dx_type;
    }
    
    public void setDx_type(String dx_type) {
        this.dx_type = dx_type;
    }
    
    public String getIs_db_input() {
        return is_db_input;
    }
    
    public void setIs_db_input(String is_db_input) {
        this.is_db_input = is_db_input;
    }
    
    public String getIs_db_out() {
        return is_db_out;
    }
    
    public void setIs_db_out(String is_db_out) {
        this.is_db_out = is_db_out;
    }
    
    public Long getInput_error_lines() {
        return input_error_lines;
    }
    
    public void setInput_error_lines(Long input_error_lines) {
        this.input_error_lines = input_error_lines;
    }
    
    public Long getOutput_lines() {
        return output_lines;
    }
    
    public void setOutput_lines(Long output_lines) {
        this.output_lines = output_lines;
    }
    
    public String getIs_exception_stoped() {
        return is_exception_stoped;
    }
    
    public void setIs_exception_stoped(String is_exception_stoped) {
        this.is_exception_stoped = is_exception_stoped;
    }
    
    public String getException_dtl() {
        return exception_dtl;
    }
    
    public void setException_dtl(String exception_dtl) {
        this.exception_dtl = exception_dtl;
    }
    
    public Long getInput_lines() {
        return input_lines;
    }
    
    public void setInput_lines(Long input_lines) {
        this.input_lines = input_lines;
    }
    
    public Long getError_lines() {
        return error_lines;
    }
    
    public void setError_lines(Long error_lines) {
        this.error_lines = error_lines;
    }
    
    public Long getReadErrorLines() {
        return read_error_lines;
    }
    
    public void setReadErrorLines(Long read_error_lines) {
        this.read_error_lines = read_error_lines;
    }
    
    public Long getInsert_lines() {
        return insert_lines;
    }
    
    public void setInsert_lines(Long insert_lines) {
        this.insert_lines = insert_lines;
    }
    
    public Long getUpdate_lines() {
        return update_lines;
    }
    
    public void setUpdate_lines(Long update_lines) {
        this.update_lines = update_lines;
    }
    
    public String getFlow_name() {
        return flow_name;
    }
    
    public void setFlow_name(String flow_name) {
        this.flow_name = flow_name;
    }
    
    public String getEnddate() {
        return enddate;
    }
    
    public void setEnddate(String enddate) {
        this.enddate = enddate;
    }
    
    private String current_log_step;
    private String log_step_last_pkvalue;
    
    public String getCurrent_log_step() {
        return current_log_step;
    }
    
    public void setCurrent_log_step(String current_log_step) {
        this.current_log_step = current_log_step;
    }
    
    public String getLog_step_last_pkvalue() {
        return log_step_last_pkvalue;
    }
    
    public void setLog_step_last_pkvalue(String log_step_last_pkvalue) {
        this.log_step_last_pkvalue = log_step_last_pkvalue;
    }
}

