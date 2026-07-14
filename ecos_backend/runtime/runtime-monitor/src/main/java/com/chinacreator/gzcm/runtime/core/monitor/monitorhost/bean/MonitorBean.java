package com.chinacreator.gzcm.runtime.core.monitor.monitorhost.bean;

import java.io.Serializable;

public class MonitorBean implements Serializable {

	private static final long serialVersionUID = 8365740021206589007L;
	private String monitor_object_id;
	private String monitor_object_name;
	private String inner_ip;
	private String outor_ip;
	private String org_name;
	private String usable_status;
	private String health_status_detail;
	private String plugin_name;
	private String is_monitor;
	private String health_status;
	private String org_id;
	private String node_name;
	private String mq_port;
	private String monitor_object_ip;
	private String monitor_node_id;
	
	
	public String getMonitor_node_id() {
		return monitor_node_id;
	}
	public void setMonitor_node_id(String monitor_node_id) {
		this.monitor_node_id = monitor_node_id;
	}
	public MonitorBean() {}
	public String getMonitor_object_id() {
		return monitor_object_id;
	}
	public void setMonitor_object_id(String monitor_object_id) {
		this.monitor_object_id = monitor_object_id;
	}
	public String getMonitor_object_ip() {
		return monitor_object_ip;
	}
	public void setMonitor_object_ip(String monitor_object_ip) {
		this.monitor_object_ip = monitor_object_ip;
	}
	public String getMonitor_object_name() {
		return monitor_object_name;
	}
	public void setMonitor_object_name(String monitor_object_name) {
		this.monitor_object_name = monitor_object_name;
	}
	public String getInner_ip() {
		return inner_ip;
	}
	public void setInner_ip(String inner_ip) {
		this.inner_ip = inner_ip;
	}
	public String getOutor_ip() {
		return outor_ip;
	}
	public void setOutor_ip(String outor_ip) {
		this.outor_ip = outor_ip;
	}
	public String getOrg_name() {
		return org_name;
	}
	public String getUsable_status() {
		return usable_status;
	}
	public void setUsable_status(String usable_status) {
		this.usable_status = usable_status;
	}
	public String getHealth_status_detail() {
		return health_status_detail;
	}
	public void setHealth_status_detail(String health_status_detail) {
		this.health_status_detail = health_status_detail;
	}
	public String getPlugin_name() {
		return plugin_name;
	}
	public void setPlugin_name(String plugin_name) {
		this.plugin_name = plugin_name;
	}
	public String getIs_monitor() {
		return is_monitor;
	}
	public void setIs_monitor(String is_monitor) {
		this.is_monitor = is_monitor;
	}
	public String getHealth_status() {
		return health_status;
	}
	public void setHealth_status(String health_status) {
		this.health_status = health_status;
	}
	public String getOrg_id() {
		return org_id;
	}
	public void setOrg_id(String org_id) {
		this.org_id = org_id;
	}
	public String getNode_name() {
		return node_name;
	}
	public void setNode_name(String node_name) {
		this.node_name = node_name;
	}
	public String getMq_port() {
		return mq_port;
	}
	public void setMq_port(String mq_port) {
		this.mq_port = mq_port;
	}
	public void setOrg_name(String org_name) {
		this.org_name = org_name;
	}
	
}
