package com.chinacreator.gzcm.runtime.core.monitor.strategy.bean;

/**
 * 策略Bean
 */
public class StrategyBean {
    
    private String collect_strategy_id;
    private String plugin_name;
    private String target_path;
    private long time_interval_sec;
    private long time_interval_min;
    
    public String getCollect_strategy_id() {
        return collect_strategy_id;
    }
    
    public void setCollect_strategy_id(String collect_strategy_id) {
        this.collect_strategy_id = collect_strategy_id;
    }
    
    public String getPlugin_name() {
        return plugin_name;
    }
    
    public void setPlugin_name(String plugin_name) {
        this.plugin_name = plugin_name;
    }
    
    public String getTarget_path() {
        return target_path;
    }
    
    public void setTarget_path(String target_path) {
        this.target_path = target_path;
    }
    
    public long getTime_interval_sec() {
        return time_interval_sec;
    }
    
    public void setTime_interval_sec(long time_interval_sec) {
        this.time_interval_sec = time_interval_sec;
    }
    
    public long getTime_interval_min() {
        return time_interval_min;
    }
    
    public void setTime_interval_min(long time_interval_min) {
        this.time_interval_min = time_interval_min;
    }
}
