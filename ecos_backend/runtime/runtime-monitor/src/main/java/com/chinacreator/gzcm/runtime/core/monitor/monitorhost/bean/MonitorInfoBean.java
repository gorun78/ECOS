package com.chinacreator.gzcm.runtime.core.monitor.monitorhost.bean;

import java.io.Serializable;

public class MonitorInfoBean implements Serializable {

	private static final long serialVersionUID = -9049082857144557940L;
	private String monitor_object_id;
	private String usable_status;
	private String save_longest_time;
	private String last_monitor_time;
	private String collect_time;
	
	public String getLast_monitor_time() {
		return last_monitor_time;
	}
	public void setLast_monitor_time(String last_monitor_time) {
		this.last_monitor_time = last_monitor_time;
	}
	public String getMonitor_object_id() {
		return monitor_object_id;
	}
	public void setMonitor_object_id(String monitor_object_id) {
		this.monitor_object_id = monitor_object_id;
	}
	public String getTarget_path() {
		return target_path;
	}
	public void setTarget_path(String target_path) {
		this.target_path = target_path;
	}
	public String getTarget_child() {
		return target_child;
	}
	public void setTarget_child(String target_child) {
		this.target_child = target_child;
	}
	public String getTarget_child_new() {
		return target_child_new;
	}
	public void setTarget_child_new(String target_child_new) {
		this.target_child_new = target_child_new;
	}
	public String getTarget_value() {
		return target_value;
	}
	public void setTarget_value(String target_value) {
		this.target_value = target_value;
	}
	public String getUsable_status() {
		return usable_status;
	}
	public void setUsable_status(String usable_status) {
		this.usable_status = usable_status;
	}
	public String getSave_longest_time() {
		return save_longest_time;
	}
	public void setSave_longest_time(String save_longest_time) {
		this.save_longest_time = save_longest_time;
	}

	public String getCollect_time() {
		return collect_time;
	}
	public void setCollect_time(String collect_time) {
		this.collect_time = collect_time;
	}

	private String target_path;
	private String target_child;
	private String target_child_new;
	private String target_value;
	public MonitorInfoBean() {
		super();
	}
	
	
}
