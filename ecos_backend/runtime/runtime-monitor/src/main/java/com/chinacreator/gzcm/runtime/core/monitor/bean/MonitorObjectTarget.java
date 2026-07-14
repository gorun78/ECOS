package com.chinacreator.gzcm.runtime.core.monitor.bean;

import java.io.Serializable;

public class MonitorObjectTarget implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String plugin_name;
	
	private String target_path;  //閹稿洦鐖ｉ崗銊ㄧ熅瀵板嫬鎮?
	
	private String target_child_item;
	
	private String monitor_object_id;

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

	public String getTarget_child_item() {
		return target_child_item;
	}

	public void setTarget_child_item(String target_child_item) {
		this.target_child_item = target_child_item;
	}

	public String getMonitor_object_id() {
		return monitor_object_id;
	}

	public void setMonitor_object_id(String monitor_object_id) {
		this.monitor_object_id = monitor_object_id;
	}
}
