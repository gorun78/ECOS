package com.chinacreator.gzcm.runtime.core.monitor.monitorobject.bean;

import java.io.Serializable;

public class MonitorObjectParams implements Serializable {

	private static final long serialVersionUID = 137835953252127819L;

	private String monitor_object_id;
	
	private String param_code;
	
	private String param_value;
	
	private String param_name;

	public String getMonitor_object_id() {
		return monitor_object_id;
	}

	public void setMonitor_object_id(String monitor_object_id) {
		this.monitor_object_id = monitor_object_id;
	}

	public String getParam_code() {
		return param_code;
	}

	public void setParam_code(String param_code) {
		this.param_code = param_code;
	}

	public String getParam_value() {
		return param_value;
	}

	public void setParam_value(String param_value) {
		this.param_value = param_value;
	}

	public String getParam_name() {
		return param_name;
	}

	public void setParam_name(String param_name) {
		this.param_name = param_name;
	}
}
