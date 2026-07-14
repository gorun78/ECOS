package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.DxConstans;

/**
 * ScheduleBean - 方案Bean类
 * 注意：此类的完整实现在Bus-Zhi模块的jobschedule.bean包中
 * 此版本用于runtime模块的接口定义
 */
public class ScheduleBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String schedule_id;
    private String schedule_name;
    private String schedule_heath;
    private String schedule_status;
    private String object_name;
    private String schedule_type;
    private String extraction_way;
    private String last_starttime;
    private String dx_type;
    private String ds_type;
    private String status;
    private String remark;
    private String cron;
    private Timestamp create_time;
    private Timestamp modify_time;
    private List<String> preScheduleIds;
    
    // Additional fields for task scheduling
    private Integer minutes;
    private Integer hour;
    private String object_id;
    private Integer interval_seconds;
    private Integer interval_minutes;
    private Integer day_of_month;
    private Integer weekday;
    private String proc_schedule_type;
    
    // Getters and setters
    public String getSchedule_id() {
        return schedule_id;
    }
    
    public void setSchedule_id(String schedule_id) {
        this.schedule_id = schedule_id;
    }
    
    public String getSchedule_name() {
        return schedule_name;
    }
    
    public void setSchedule_name(String schedule_name) {
        this.schedule_name = schedule_name;
    }
    
    public String getSchedule_heath() {
        return schedule_heath;
    }
    
    public void setSchedule_heath(String schedule_heath) {
        this.schedule_heath = schedule_heath;
    }
    
    public String getSchedule_status() {
        return schedule_status;
    }
    
    public void setSchedule_status(String schedule_status) {
        this.schedule_status = schedule_status;
    }
    
    public String getObject_name() {
        return object_name;
    }
    
    public void setObject_name(String object_name) {
        this.object_name = object_name;
    }
    
    public String getSchedule_type() {
        return schedule_type;
    }
    
    public void setSchedule_type(String schedule_type) {
        this.schedule_type = schedule_type;
    }
    
    public String getExtraction_way() {
        return extraction_way;
    }
    
    public void setExtraction_way(String extraction_way) {
        this.extraction_way = extraction_way;
    }
    
    public String getLast_starttime() {
        return last_starttime;
    }
    
    public void setLast_starttime(String last_starttime) {
        this.last_starttime = last_starttime;
    }
    
    public String getDx_type() {
        return dx_type;
    }
    
    public void setDx_type(String dx_type) {
        this.dx_type = dx_type;
    }
    
    public String getDs_type() {
        return ds_type;
    }

    public void setDs_type(String ds_type) {
        this.ds_type = ds_type;
    }
    
    // Alias method for compatibility
    public String getDs_id() {
        return ds_type;
    }
    
    public void setDs_id(String ds_id) {
        this.ds_type = ds_id;
    }

    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public String getCron() {
        return cron;
    }
    
    public void setCron(String cron) {
        this.cron = cron;
    }
    
    public Timestamp getCreate_time() {
        return create_time;
    }
    
    public void setCreate_time(Timestamp create_time) {
        this.create_time = create_time;
    }
    
    public Timestamp getModify_time() {
        return modify_time;
    }
    
    public void setModify_time(Timestamp modify_time) {
        this.modify_time = modify_time;
    }
    
    public List<String> getPreScheduleIds() {
        return preScheduleIds;
    }
    
    public void setPreScheduleIds(List<String> preScheduleIds) {
        this.preScheduleIds = preScheduleIds;
    }
    
    // Additional getters and setters for task scheduling
    public Integer getMinutes() {
        return minutes;
    }
    
    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
    }
    
    public Integer getHour() {
        return hour;
    }
    
    public void setHour(Integer hour) {
        this.hour = hour;
    }
    
    public String getObject_id() {
        return object_id;
    }
    
    public void setObject_id(String object_id) {
        this.object_id = object_id;
    }
    
    public Integer getInterval_seconds() {
        return interval_seconds;
    }
    
    public void setInterval_seconds(Integer interval_seconds) {
        this.interval_seconds = interval_seconds;
    }
    
    public Integer getInterval_minutes() {
        return interval_minutes;
    }
    
    public void setInterval_minutes(Integer interval_minutes) {
        this.interval_minutes = interval_minutes;
    }
    
    public Integer getDay_of_month() {
        return day_of_month;
    }
    
    public void setDay_of_month(Integer day_of_month) {
        this.day_of_month = day_of_month;
    }
    
    public Integer getWeekday() {
        return weekday;
    }
    
    public void setWeekday(Integer weekday) {
        this.weekday = weekday;
    }
    
    public String getProc_schedule_type() {
        return proc_schedule_type;
    }
    
    public void setProc_schedule_type(String proc_schedule_type) {
        this.proc_schedule_type = proc_schedule_type;
    }
    
    // Additional fields for time range
    private String from_time;
    private String to_time;
    
    public String getFrom_time() {
        return from_time;
    }
    
    public void setFrom_time(String from_time) {
        this.from_time = from_time;
    }
    
    public String getTo_time() {
        return to_time;
    }
    
    public void setTo_time(String to_time) {
        this.to_time = to_time;
    }
    
    // Additional fields for schedule management
    private String is_segmented;
    private String is_out_correct_data;
    private String last_excute_log_id;
    private String execute_status;
    private Timestamp next_start_time;
    private Timestamp last_start_time;
    private String inc_start_time;
    private String is_record_breakpoint;
    
    public String getIs_segmented() {
        return is_segmented;
    }
    
    public void setIs_segmented(String is_segmented) {
        this.is_segmented = is_segmented;
    }
    
    public String getIs_out_correct_data() {
        return is_out_correct_data;
    }
    
    public void setIs_out_correct_data(String is_out_correct_data) {
        this.is_out_correct_data = is_out_correct_data;
    }
    
    public String getLast_excute_log_id() {
        return last_excute_log_id;
    }
    
    public void setLast_excute_log_id(String last_excute_log_id) {
        this.last_excute_log_id = last_excute_log_id;
    }
    
    public String getExecute_status() {
        return execute_status;
    }
    
    public void setExecute_status(String execute_status) {
        this.execute_status = execute_status;
    }
    
    public Timestamp getNext_start_time() {
        return next_start_time;
    }
    
    public void setNext_start_time(Timestamp next_start_time) {
        this.next_start_time = next_start_time;
    }
    
    public Timestamp getLast_start_time() {
        return last_start_time;
    }
    
    public void setLast_start_time(Timestamp last_start_time) {
        this.last_start_time = last_start_time;
    }
    
    public String getInc_start_time() {
        return inc_start_time;
    }
    
    public void setInc_start_time(String inc_start_time) {
        this.inc_start_time = inc_start_time;
    }
    
    public String getIs_record_breakpoint() {
        return is_record_breakpoint;
    }
    
    public void setIs_record_breakpoint(String is_record_breakpoint) {
        this.is_record_breakpoint = is_record_breakpoint;
    }
    
    // Flow change tracking
    private String is_flow_changed;
    
    public String getIs_flow_changed() {
        return is_flow_changed;
    }
    
    public void setIs_flow_changed(String is_flow_changed) {
        this.is_flow_changed = is_flow_changed;
    }
    
    // Sort string for SQL ordering
    private String sortStr;
    
    public String getSortStr() {
        return sortStr;
    }
    
    public void setSortStr(String sortStr) {
        this.sortStr = sortStr;
    }
    
    // Custom process node ID
    private String custom_process_node_id;
    
    public String getCustom_process_node_id() {
        return custom_process_node_id;
    }
    
    public void setCustom_process_node_id(String custom_process_node_id) {
        this.custom_process_node_id = custom_process_node_id;
    }
    
    // Process maintenance way
    private String process_maintain_way;
    
    public String getProcess_maintain_way() {
        return process_maintain_way;
    }
    
    public void setProcess_maintain_way(String process_maintain_way) {
        this.process_maintain_way = process_maintain_way;
    }
    
    // Organization names
    private String org_names;
    
    public String getOrg_names() {
        return org_names;
    }
    
    public void setOrg_names(String org_names) {
        this.org_names = org_names;
    }
    
    // Job name
    private String jobName;
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
    
    // Object type
    private String object_type;
    
    public String getObject_type() {
        return object_type;
    }
    
    public void setObject_type(String object_type) {
        this.object_type = object_type;
    }
    
    // Is Restful Plus
    private String isRestfulPlus;
    
    public String getIsRestfulPlus() {
        return isRestfulPlus;
    }
    
    public void setIsRestfulPlus(String isRestfulPlus) {
        this.isRestfulPlus = isRestfulPlus;
    }
    
    // Temp table name
    private String temp_table_name;
    
    public String getTemp_table_name() {
        return temp_table_name;
    }
    
    public void setTemp_table_name(String temp_table_name) {
        this.temp_table_name = temp_table_name;
    }
    
    // Filter SQL
    private String filter_sql;
    
    public String getFilter_sql() {
        return filter_sql;
    }
    
    public void setFilter_sql(String filter_sql) {
        this.filter_sql = filter_sql;
    }
    
    // Increment logo value
    private String inc_logo_val;
    
    public String getInc_logo_val() {
        return inc_logo_val;
    }
    
    public void setInc_logo_val(String inc_logo_val) {
        this.inc_logo_val = inc_logo_val;
    }
    
    // Readed logo value
    private String readed_logo_val;
    
    public String getReaded_logo_val() {
        return readed_logo_val;
    }
    
    public void setReaded_logo_val(String readed_logo_val) {
        this.readed_logo_val = readed_logo_val;
    }
    
    // MDM Schedule flag
    private String isMDMSchedule;
    
    public boolean isMDMSchedule() {
        return DxConstans.DB_TRUE_STRING.equals(isMDMSchedule);
    }
    
    public String getIsMDMSchedule() {
        return isMDMSchedule;
    }
    
    public void setIsMDMSchedule(String isMDMSchedule) {
        this.isMDMSchedule = isMDMSchedule;
    }
    
    // Repeat offset size
    private String repeat_offset_size;
    
    public String getRepeat_offset_size() {
        return repeat_offset_size;
    }
    
    public void setRepeat_offset_size(String repeat_offset_size) {
        this.repeat_offset_size = repeat_offset_size;
    }
    
    // Error data to destination flag
    private String is_errordata_to_dest;
    
    public String getIs_errordata_to_dest() {
        return is_errordata_to_dest;
    }
    
    public void setIs_errordata_to_dest(String is_errordata_to_dest) {
        this.is_errordata_to_dest = is_errordata_to_dest;
    }
    
    // Increment comparison operator
    private String inc_comp_oper;
    
    public String getInc_comp_oper() {
        return inc_comp_oper;
    }
    
    public void setInc_comp_oper(String inc_comp_oper) {
        this.inc_comp_oper = inc_comp_oper;
    }
    
    // Increment REST parameter ID
    private String inc_rest_param_id;
    
    public String getInc_rest_param_id() {
        return inc_rest_param_id;
    }
    
    public void setInc_rest_param_id(String inc_rest_param_id) {
        this.inc_rest_param_id = inc_rest_param_id;
    }
    
    // Is record ETL time flag
    private String is_rec_etltime;
    
    public String getIs_rec_etltime() {
        return is_rec_etltime;
    }
    
    public void setIs_rec_etltime(String is_rec_etltime) {
        this.is_rec_etltime = is_rec_etltime;
    }
    
    // Is record ETL batch flag
    private String is_rec_etlbatch;
    
    public String getIs_rec_etlbatch() {
        return is_rec_etlbatch;
    }
    
    public void setIs_rec_etlbatch(String is_rec_etlbatch) {
        this.is_rec_etlbatch = is_rec_etlbatch;
    }
    
    // Is open flag
    private String isopen;
    
    public String getIsopen() {
        return isopen;
    }
    
    public void setIsopen(String isopen) {
        this.isopen = isopen;
    }
    
    // Thread size
    private Integer thread_Size;
    
    public Integer getThread_Size() {
        return thread_Size;
    }
    
    public void setThread_Size(Integer thread_Size) {
        this.thread_Size = thread_Size;
    }
    
    // Consume way
    private String comsume_way;
    
    public String getComsume_way() {
        return comsume_way;
    }
    
    public void setComsume_way(String comsume_way) {
        this.comsume_way = comsume_way;
    }
    
    // Is retain history data flag
    private String is_retain_history_data;
    
    public String getIs_retain_history_data() {
        return is_retain_history_data;
    }
    
    public void setIs_retain_history_data(String is_retain_history_data) {
        this.is_retain_history_data = is_retain_history_data;
    }
    
    private String node_id;
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
    
    private String param_table_name;
    
    public String getParam_table_name() {
        return param_table_name;
    }
    
    public void setParam_table_name(String param_table_name) {
        this.param_table_name = param_table_name;
    }
    
    private java.sql.Timestamp enabled_time;
    
    public java.sql.Timestamp getEnabled_time() {
        return enabled_time;
    }
    
    public void setEnabled_time(java.sql.Timestamp enabled_time) {
        this.enabled_time = enabled_time;
    }
    
    private java.sql.Timestamp stoped_time;
    
    public java.sql.Timestamp getStoped_time() {
        return stoped_time;
    }
    
    public void setStoped_time(java.sql.Timestamp stoped_time) {
        this.stoped_time = stoped_time;
    }
    
    private Integer thread_size;
    
    public Integer getThread_size() {
        return thread_size;
    }
    
    public void setThread_size(Integer thread_size) {
        this.thread_size = thread_size;
    }
    
    private String is_conv_coding;
    
    public String getIs_conv_coding() {
        return is_conv_coding;
    }
    
    public void setIs_conv_coding(String is_conv_coding) {
        this.is_conv_coding = is_conv_coding;
    }
    
    private String target_encoding;
    
    public String getTarget_encoding() {
        return target_encoding;
    }
    
    public void setTarget_encoding(String target_encoding) {
        this.target_encoding = target_encoding;
    }
    
    private String sampling_rowsize;
    
    public String getSampling_rowsize() {
        return sampling_rowsize;
    }
    
    public void setSampling_rowsize(String sampling_rowsize) {
        this.sampling_rowsize = sampling_rowsize;
    }
    
    private String custom_process_dir;
    
    public String getCustom_process_dir() {
        return custom_process_dir;
    }
    
    public void setCustom_process_dir(String custom_process_dir) {
        this.custom_process_dir = custom_process_dir;
    }
    
    private String custom_process_name;
    
    public String getCustom_process_name() {
        return custom_process_name;
    }
    
    public void setCustom_process_name(String custom_process_name) {
        this.custom_process_name = custom_process_name;
    }
    
    private String custom_process_type;
    
    public String getCustom_process_type() {
        return custom_process_type;
    }
    
    public void setCustom_process_type(String custom_process_type) {
        this.custom_process_type = custom_process_type;
    }
    
    private String swith_column_code;
    
    public String getSwith_column_code() {
        return swith_column_code;
    }
    
    public void setSwith_column_code(String swith_column_code) {
        this.swith_column_code = swith_column_code;
    }
    
    private String swith_column_type;
    
    public String getSwith_column_type() {
        return swith_column_type;
    }
    
    public void setSwith_column_type(String swith_column_type) {
        this.swith_column_type = swith_column_type;
    }
    
    private String request_concurrency;
    
    public String getRequest_concurrency() {
        return request_concurrency;
    }
    
    public void setRequest_concurrency(String request_concurrency) {
        this.request_concurrency = request_concurrency;
    }
    
    private String request_repeat_num;
    
    public String getRequest_repeat_num() {
        return request_repeat_num;
    }
    
    public void setRequest_repeat_num(String request_repeat_num) {
        this.request_repeat_num = request_repeat_num;
    }
    
    private String mdm_incTimes;
    
    public String getMdm_incTimes() {
        return mdm_incTimes;
    }
    
    public void setMdm_incTimes(String mdm_incTimes) {
        this.mdm_incTimes = mdm_incTimes;
    }
    
    /**
     * 设置增量时间Map（重载方法，兼容Map类型）
     * @param mdmIncTimesMap Map格式的增量时间
     */
    public void setMdm_incTimes(java.util.Map<String, String> mdmIncTimesMap) {
        if (mdmIncTimesMap == null || mdmIncTimesMap.isEmpty()) {
            this.mdm_incTimes = null;
            return;
        }
        // Convert Map to string format (key1=value1;key2=value2)
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : mdmIncTimesMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue() : "");
        }
        this.mdm_incTimes = sb.toString();
    }
    
    private String mdm_srcObjs;
    
    public String getMdm_srcObjs() {
        return mdm_srcObjs;
    }
    
    public void setMdm_srcObjs(String mdm_srcObjs) {
        this.mdm_srcObjs = mdm_srcObjs;
    }
    
    /**
     * 设置源对象ID数组（重载方法，兼容String[]类型）
     * @param mdm_srcObjs_array 源对象ID数组
     */
    public void setMdm_srcObjs(String[] mdm_srcObjs_array) {
        if (mdm_srcObjs_array == null || mdm_srcObjs_array.length == 0) {
            this.mdm_srcObjs = null;
            return;
        }
        // Convert String[] to comma-separated string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mdm_srcObjs_array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(mdm_srcObjs_array[i] != null ? mdm_srcObjs_array[i] : "");
        }
        this.mdm_srcObjs = sb.toString();
    }
    
    private String object_names;
    
    public String getObject_names() {
        return object_names;
    }
    
    public void setObject_names(String object_names) {
        this.object_names = object_names;
    }
    
    private String db_type;
    
    public String getDb_type() {
        return db_type;
    }
    
    public void setDb_type(String db_type) {
        this.db_type = db_type;
    }
}

