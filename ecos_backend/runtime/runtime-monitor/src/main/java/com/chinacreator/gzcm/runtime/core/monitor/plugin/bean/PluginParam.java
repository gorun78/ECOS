package com.chinacreator.gzcm.runtime.core.monitor.plugin.bean;

import java.io.Serializable;

/**
 * 閹绘帊娆㈤崣鍌涙殶
 * @author sunzhiyong
 *
 */
public class PluginParam implements Serializable {
	
	private static final long serialVersionUID = -5264569255383771747L;

	private String plugin_name;
	
	private String param_code;
	
	private String param_name;
	
	private String param_value;

	public String getPlugin_name() {
		return plugin_name;
	}

	public void setPlugin_name(String plugin_name) {
		this.plugin_name = plugin_name;
	}

	public String getParam_code() {
		return param_code;
	}

	public void setParam_code(String param_code) {
		this.param_code = param_code;
	}

	public String getParam_name() {
		return param_name;
	}

	public void setParam_name(String param_name) {
		this.param_name = param_name;
	}

	public String getParam_value() {
		return param_value;
	}

	public void setParam_value(String param_value) {
		this.param_value = param_value;
	}
}
