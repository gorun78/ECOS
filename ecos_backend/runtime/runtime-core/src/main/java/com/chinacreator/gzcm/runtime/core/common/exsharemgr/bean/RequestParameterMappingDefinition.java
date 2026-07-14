package com.chinacreator.gzcm.runtime.core.common.exsharemgr.bean;

import java.io.Serializable;

/**
 * RequestParameterMappingDefinition - 请求参数映射定义Bean类
 * 用于定义请求参数映射的配置
 */
public class RequestParameterMappingDefinition implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String map_def_id;
    private String schedule_id;
    private String object_id;
    private String filter_sql;
    private String description;
    
    // Getters and setters
    public String getMap_def_id() {
        return map_def_id;
    }
    
    public void setMap_def_id(String map_def_id) {
        this.map_def_id = map_def_id;
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
    
    public String getFilter_sql() {
        return filter_sql;
    }
    
    public void setFilter_sql(String filter_sql) {
        this.filter_sql = filter_sql;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
}

