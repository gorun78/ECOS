package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean;

import java.io.Serializable;

/**
 * ScheduleSrcObjBean - 方案源对象Bean
 */
public class ScheduleSrcObjBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String srcobject_ref_id;
    private String schedule_id;
    private String object_id;
    private String object_names;
    private String extraction_way;
    private String filter_sql;
    private Integer thread_size;
    private String inc_start_time;
    private String max_increment_pks;
    private String object_type;
    private String match_regex;
    private String inc_comp_oper;
    private String repeat_offset_size;
    
    // Getters and setters
    public String getSrcobject_ref_id() {
        return srcobject_ref_id;
    }
    
    public void setSrcobject_ref_id(String srcobject_ref_id) {
        this.srcobject_ref_id = srcobject_ref_id;
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
    
    public String getObject_names() {
        return object_names;
    }
    
    public void setObject_names(String object_names) {
        this.object_names = object_names;
    }
    
    public String getExtraction_way() {
        return extraction_way;
    }
    
    public void setExtraction_way(String extraction_way) {
        this.extraction_way = extraction_way;
    }
    
    public String getFilter_sql() {
        return filter_sql;
    }
    
    public void setFilter_sql(String filter_sql) {
        this.filter_sql = filter_sql;
    }
    
    public Integer getThread_size() {
        return thread_size;
    }
    
    public void setThread_size(Integer thread_size) {
        this.thread_size = thread_size;
    }
    
    public String getInc_start_time() {
        return inc_start_time;
    }
    
    public void setInc_start_time(String inc_start_time) {
        this.inc_start_time = inc_start_time;
    }
    
    public String getMax_increment_pks() {
        return max_increment_pks;
    }
    
    public void setMax_increment_pks(String max_increment_pks) {
        this.max_increment_pks = max_increment_pks;
    }
    
    public String getObject_type() {
        return object_type;
    }
    
    public void setObject_type(String object_type) {
        this.object_type = object_type;
    }
    
    public String getMatch_regex() {
        return match_regex;
    }
    
    public void setMatch_regex(String match_regex) {
        this.match_regex = match_regex;
    }
    
    public String getInc_comp_oper() {
        return inc_comp_oper;
    }
    
    public void setInc_comp_oper(String inc_comp_oper) {
        this.inc_comp_oper = inc_comp_oper;
    }
    
    public String getRepeat_offset_size() {
        return repeat_offset_size;
    }
    
    public void setRepeat_offset_size(String repeat_offset_size) {
        this.repeat_offset_size = repeat_offset_size;
    }
    
    private String pkId;
    
    public String getPkId() {
        return pkId;
    }
    
    public void setPkId(String pkId) {
        this.pkId = pkId;
    }
    
    private String incTimeId;
    
    public String getIncTimeId() {
        return incTimeId;
    }
    
    public void setIncTimeId(String incTimeId) {
        this.incTimeId = incTimeId;
    }
    
    private String pkShow;
    private String incTimeShow;
    
    public String getPkShow() {
        return pkShow;
    }
    
    public void setPkShow(String pkShow) {
        this.pkShow = pkShow;
    }
    
    public String getIncTimeShow() {
        return incTimeShow;
    }
    
    public void setIncTimeShow(String incTimeShow) {
        this.incTimeShow = incTimeShow;
    }
}

