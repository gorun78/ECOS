package com.chinacreator.gzcm.runtime.core.common.datamusterdefine.bean;

import java.io.Serializable;

/**
 * DSCollectRecBean - 数据集采集记录Bean
 */
public class DSCollectRecBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String rec_id;
    private String dsdefine_id;
    private String resource_id;
    private String resource_name;
    private String resource_type;
    private String remark;
    
    // Getters and setters
    public String getRec_id() {
        return rec_id;
    }
    
    public void setRec_id(String rec_id) {
        this.rec_id = rec_id;
    }
    
    public String getDsdefine_id() {
        return dsdefine_id;
    }
    
    public void setDsdefine_id(String dsdefine_id) {
        this.dsdefine_id = dsdefine_id;
    }
    
    public String getResource_id() {
        return resource_id;
    }
    
    public void setResource_id(String resource_id) {
        this.resource_id = resource_id;
    }
    
    public String getResource_name() {
        return resource_name;
    }
    
    public void setResource_name(String resource_name) {
        this.resource_name = resource_name;
    }
    
    public String getResource_type() {
        return resource_type;
    }
    
    public void setResource_type(String resource_type) {
        this.resource_type = resource_type;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    private String collectrec_id;
    private String objectrec_id;
    private String rec_type;
    private String join_fieldname;
    private String extraction_way;
    private String schedule_type;
    private String interval_seconds;
    private String interval_minutes;
    private String day_of_month;
    private String weekday;
    private String minutes;
    private String hour;
    private String is_mainsource;
    private String match_seq;
    
    public String getCollectrec_id() {
        return collectrec_id;
    }
    
    public void setCollectrec_id(String collectrec_id) {
        this.collectrec_id = collectrec_id;
    }
    
    public String getObjectrec_id() {
        return objectrec_id;
    }
    
    public void setObjectrec_id(String objectrec_id) {
        this.objectrec_id = objectrec_id;
    }
    
    public String getRec_type() {
        return rec_type;
    }
    
    public void setRec_type(String rec_type) {
        this.rec_type = rec_type;
    }
    
    public String getJoin_fieldname() {
        return join_fieldname;
    }
    
    public void setJoin_fieldname(String join_fieldname) {
        this.join_fieldname = join_fieldname;
    }
    
    public String getExtraction_way() {
        return extraction_way;
    }
    
    public void setExtraction_way(String extraction_way) {
        this.extraction_way = extraction_way;
    }
    
    public String getSchedule_type() {
        return schedule_type;
    }
    
    public void setSchedule_type(String schedule_type) {
        this.schedule_type = schedule_type;
    }
    
    public String getInterval_seconds() {
        return interval_seconds;
    }
    
    public void setInterval_seconds(String interval_seconds) {
        this.interval_seconds = interval_seconds;
    }
    
    public String getInterval_minutes() {
        return interval_minutes;
    }
    
    public void setInterval_minutes(String interval_minutes) {
        this.interval_minutes = interval_minutes;
    }
    
    public String getDay_of_month() {
        return day_of_month;
    }
    
    public void setDay_of_month(String day_of_month) {
        this.day_of_month = day_of_month;
    }
    
    public String getWeekday() {
        return weekday;
    }
    
    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }
    
    public String getMinutes() {
        return minutes;
    }
    
    public void setMinutes(String minutes) {
        this.minutes = minutes;
    }
    
    public String getHour() {
        return hour;
    }
    
    public void setHour(String hour) {
        this.hour = hour;
    }
    
    public String getIs_mainsource() {
        return is_mainsource;
    }
    
    public void setIs_mainsource(String is_mainsource) {
        this.is_mainsource = is_mainsource;
    }
    
    public String getMatch_seq() {
        return match_seq;
    }
    
    public void setMatch_seq(String match_seq) {
        this.match_seq = match_seq;
    }
    
    private String rec_collect_schedule_id;
    
    public String getRec_collect_schedule_id() {
        return rec_collect_schedule_id;
    }
    
    public void setRec_collect_schedule_id(String rec_collect_schedule_id) {
        this.rec_collect_schedule_id = rec_collect_schedule_id;
    }
}

