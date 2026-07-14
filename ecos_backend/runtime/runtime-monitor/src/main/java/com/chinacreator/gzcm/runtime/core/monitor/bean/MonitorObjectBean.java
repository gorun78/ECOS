package com.chinacreator.gzcm.runtime.core.monitor.bean;

/**
 * 监控对象Bean
 */
public class MonitorObjectBean {
    
    private String id;
    private String name;
    
    private String monitor_object_id;
    private String monitor_object_name;
    private String plugin_name;
    private String usable_status;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
        this.monitor_object_id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.monitor_object_name = name;
    }

    public String getMonitor_object_id() {
        return monitor_object_id;
    }

    public void setMonitor_object_id(String monitor_object_id) {
        this.monitor_object_id = monitor_object_id;
        this.id = monitor_object_id;
    }

    public String getMonitor_object_name() {
        return monitor_object_name;
    }

    public void setMonitor_object_name(String monitor_object_name) {
        this.monitor_object_name = monitor_object_name;
        this.name = monitor_object_name;
    }

    public String getPlugin_name() {
        return plugin_name;
    }

    public void setPlugin_name(String plugin_name) {
        this.plugin_name = plugin_name;
    }

    public String getUsable_status() {
        return usable_status;
    }

    public void setUsable_status(String usable_status) {
        this.usable_status = usable_status;
    }
}
