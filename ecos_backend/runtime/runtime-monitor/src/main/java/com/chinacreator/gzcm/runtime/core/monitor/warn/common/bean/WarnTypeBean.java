package com.chinacreator.gzcm.runtime.core.monitor.warn.common.bean;

import java.io.Serializable;

public class WarnTypeBean implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String warntype_datatable; //
	private String warntype_handtype; //
	private String warntype_id; //
	private String warntype_name; //
	private String warntype_version; //
	private String warntype_num="0";
	
	public String getWarntype_num() {
		return warntype_num;
	}
	public void setWarntype_num(String warntype_num) {
		this.warntype_num = warntype_num;
	}
	public String getWarntype_datatable() {
		return warntype_datatable;
	}
	public void setWarntype_datatable(String warntype_datatable) {
		this.warntype_datatable = warntype_datatable;
	}
	public String getWarntype_handtype() {
		return warntype_handtype;
	}
	public void setWarntype_handtype(String warntype_handtype) {
		this.warntype_handtype = warntype_handtype;
	}
	public String getWarntype_id() {
		return warntype_id;
	}
	public void setWarntype_id(String warntype_id) {
		this.warntype_id = warntype_id;
	}
	public String getWarntype_name() {
		return warntype_name;
	}
	public void setWarntype_name(String warntype_name) {
		this.warntype_name = warntype_name;
	}
	public String getWarntype_version() {
		return warntype_version;
	}
	public void setWarntype_version(String warntype_version) {
		this.warntype_version = warntype_version;
	}
	
	
}
