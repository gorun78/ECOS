package com.chinacreator.gzcm.runtime.core.monitor.plugin.bean;

import java.io.Serializable;
import java.util.List;

import jakarta.xml.bind.annotation.XmlRootElement;

import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;

@XmlRootElement
public class PluginBean implements Serializable {

	private static final long serialVersionUID = -4016211528611717592L;
	private String plugin_name;
	private String plugin_detailname;
	private String data_collector_class;
	private String collect_param_class;
	private String plugin_version;
	private String monitor_detail_main_class;
	private List<PluginParam> params;
	private List<PluginTargetBean> items;
	private List<StrategyBean> strategys;

	public List<PluginParam> getParams() {
		return params;
	}

	public void setParams(List<PluginParam> params) {
		this.params = params;
	}

	public List<PluginTargetBean> getItems() {
		return items;
	}

	public void setItems(List<PluginTargetBean> items) {
		this.items = items;
	}

	public String getPlugin_name() {
		return plugin_name;
	}

	public void setPlugin_name(String plugin_name) {
		this.plugin_name = plugin_name;
	}

	public String getPlugin_detailname() {
		return plugin_detailname;
	}

	public void setPlugin_detailname(String plugin_detailname) {
		this.plugin_detailname = plugin_detailname;
	}

	public String getData_collector_class() {
		return data_collector_class;
	}

	public void setData_collector_class(String data_collector_class) {
		this.data_collector_class = data_collector_class;
	}


	public String getCollect_param_class() {
		return collect_param_class;
	}

	public void setCollect_param_class(String collect_param_class) {
		this.collect_param_class = collect_param_class;
	}

	public String getMonitor_detail_main_class() {
		return monitor_detail_main_class;
	}

	public void setMonitor_detail_main_class(String monitor_detail_main_class) {
		this.monitor_detail_main_class = monitor_detail_main_class;
	}

	public String getPlugin_version() {
		return plugin_version;
	}

	public void setPlugin_version(String plugin_version) {
		this.plugin_version = plugin_version;
	}

	public List<StrategyBean> getStrategys() {
		return strategys;
	}

	public void setStrategys(List<StrategyBean> strategys) {
		this.strategys = strategys;
	}

}
