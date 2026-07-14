package com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean;

import java.io.Serializable;
import java.sql.Timestamp;
public class MonitorDataBean implements Serializable {

	private static final long serialVersionUID = 6829075490317982093L;
	private String monitor_object_id;
	private String target_path;
	private String target_child_new;
	private String target_value;
	private Timestamp collect_time;

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

	public Timestamp getCollect_time() {
		return collect_time;
	}

	public void setCollect_time(Timestamp collect_time) {
		this.collect_time = (collect_time != null ? new Timestamp(collect_time.getTime()) : null);
	}

}
