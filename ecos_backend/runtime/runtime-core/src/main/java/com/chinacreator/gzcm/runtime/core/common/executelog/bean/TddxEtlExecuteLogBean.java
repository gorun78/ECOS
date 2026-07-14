package com.chinacreator.gzcm.runtime.core.common.executelog.bean;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * TddxEtlExecuteLogBean - ETL执行日志Bean
 */
public class TddxEtlExecuteLogBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String excute_log_id;
    private String etl_excute_log_id;
    private String schedule_id;
    private Timestamp STARTDATE;
    private Timestamp ENDDATE;
    private String success_flag;
    private String error_detail_msg;
    private String inc_start_time;
    private String health_status;
    private Long input_lines;
    private Long error_lines;
    private Long output_lines;
    private Long READ_ERROR_LINES;
    private Long INPUT_ERROR_LINES;
    private Long INSERT_LINES;
    private Long UPDATE_LINES;
    private Long MERGER_COUNT;
    private Long SUSP_COUNT;
    private String is_exception_stoped;
    private String exception_dtl;
    private String is_finieshed;
    private String is_db_out;
    
    // Getters and setters
    public String getExcute_log_id() {
        return excute_log_id;
    }
    
    public void setExcute_log_id(String excute_log_id) {
        this.excute_log_id = excute_log_id;
    }
    
    public String getEtl_excute_log_id() {
        return etl_excute_log_id;
    }
    
    public void setEtl_excute_log_id(String etl_excute_log_id) {
        this.etl_excute_log_id = etl_excute_log_id;
    }
    
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public Timestamp getSTARTDATE() {
        return STARTDATE;
    }
    
    public void setSTARTDATE(Timestamp STARTDATE) {
        this.STARTDATE = STARTDATE;
    }
    
    public Timestamp getENDDATE() {
        return ENDDATE;
    }
    
    public void setENDDATE(Timestamp ENDDATE) {
        this.ENDDATE = ENDDATE;
    }
    
    public String getSuccess_flag() {
        return success_flag;
    }
    
    public void setSuccess_flag(String success_flag) {
        this.success_flag = success_flag;
    }
    
    public String getError_detail_msg() {
        return error_detail_msg;
    }
    
    public void setError_detail_msg(String error_detail_msg) {
        this.error_detail_msg = error_detail_msg;
    }
    
    public String getInc_start_time() {
        return inc_start_time;
    }
    
    public void setInc_start_time(String inc_start_time) {
        this.inc_start_time = inc_start_time;
    }
    
    public String getHealth_status() {
        return health_status;
    }
    
    public void setHealth_status(String health_status) {
        this.health_status = health_status;
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
    
    public Long getOutput_lines() {
        return output_lines;
    }
    
    public void setOutput_lines(Long output_lines) {
        this.output_lines = output_lines;
    }
    
    public Long getREAD_ERROR_LINES() {
        return READ_ERROR_LINES;
    }
    
    public void setREAD_ERROR_LINES(Long READ_ERROR_LINES) {
        this.READ_ERROR_LINES = READ_ERROR_LINES;
    }
    
    public Long getINPUT_ERROR_LINES() {
        return INPUT_ERROR_LINES;
    }
    
    public void setINPUT_ERROR_LINES(Long INPUT_ERROR_LINES) {
        this.INPUT_ERROR_LINES = INPUT_ERROR_LINES;
    }
    
    public Long getINSERT_LINES() {
        return INSERT_LINES;
    }
    
    public void setINSERT_LINES(Long INSERT_LINES) {
        this.INSERT_LINES = INSERT_LINES;
    }
    
    public Long getUPDATE_LINES() {
        return UPDATE_LINES;
    }
    
    public void setUPDATE_LINES(Long UPDATE_LINES) {
        this.UPDATE_LINES = UPDATE_LINES;
    }
    
    public Long getMERGER_COUNT() {
        return MERGER_COUNT;
    }
    
    public void setMERGER_COUNT(Long MERGER_COUNT) {
        this.MERGER_COUNT = MERGER_COUNT;
    }
    
    public Long getSUSP_COUNT() {
        return SUSP_COUNT;
    }
    
    public void setSUSP_COUNT(Long SUSP_COUNT) {
        this.SUSP_COUNT = SUSP_COUNT;
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
    
    public String getIs_finieshed() {
        return is_finieshed;
    }
    
    public void setIs_finieshed(String is_finieshed) {
        this.is_finieshed = is_finieshed;
    }
    
    public String getIs_db_out() {
        return is_db_out;
    }
    
    public void setIs_db_out(String is_db_out) {
        this.is_db_out = is_db_out;
    }
}

