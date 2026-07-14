package com.chinacreator.gzcm.runtime.core.monitor.plugin.bean;

import java.io.Serializable;

/**
 * 閹绘帊娆㈤幐鍥ㄧ垼妞?
 * 
 * @author sunzhiyong
 * 
 */
public class PluginTargetBean implements Serializable {

	private static final long serialVersionUID = -5969346126455599177L;
	private String plugin_name;
	private String target_path;
	private String target_name;
	private String target_detail_name;
	private String parent_target_path;
	private String is_target_child;
	private String is_record_history;
	private String is_show_trend;
	private String unit;
	private String target_child_item;
	private String is_warn;				//閺勵垰鎯佹０鍕劅閿?妫板嫯顒熼敍?娑撳秹顣╃拃?

	public String getIs_warn() {
		return is_warn;
	}

	public void setIs_warn(String is_warn) {
		this.is_warn = is_warn;
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

	public String getTarget_detail_name() {
		return target_detail_name;
	}

	public void setTarget_detail_name(String target_detail_name) {
		this.target_detail_name = target_detail_name;
	}

	public String getIs_record_history() {
		return is_record_history;
	}

	public void setIs_record_history(String is_record_history) {
		this.is_record_history = is_record_history;
	}

	public String getTarget_name() {
		return target_name;
	}

	public void setTarget_name(String target_name) {
		this.target_name = target_name;
	}

	public String getParent_target_path() {
		return parent_target_path;
	}

	public void setParent_target_path(String parent_target_path) {
		this.parent_target_path = parent_target_path;
	}

	public String getIs_target_child() {
		return is_target_child;
	}

	public void setIs_target_child(String is_target_child) {
		this.is_target_child = is_target_child;
	}

	public String getIs_show_trend() {
		return is_show_trend;
	}

	public void setIs_show_trend(String is_show_trend) {
		this.is_show_trend = is_show_trend;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getTarget_child_item() {
		return target_child_item;
	}

	public void setTarget_child_item(String target_child_item) {
		this.target_child_item = target_child_item;
	}

}
