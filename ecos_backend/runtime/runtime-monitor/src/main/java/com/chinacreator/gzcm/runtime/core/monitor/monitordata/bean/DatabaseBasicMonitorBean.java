package com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean;

import java.io.Serializable;

public class DatabaseBasicMonitorBean implements Serializable {

	private static final long serialVersionUID = -2096307315941634444L;
	private String DBINFO;
	private String DBINFO_MAXIMUM_SESSION;
	private String DBINFO_CURRENT_SESSION;
	private String DBINFO_CACHE_HITRATE;
	private String LOCKINFO;
	private String LOCKINFO_TABLE;
	private String LOCKINFO_ROW;
	private String collect_time;
	public String getDBINFO() {
		return DBINFO;
	}
	public void setDBINFO(String dBINFO) {
		DBINFO = dBINFO;
	}
	public String getDBINFO_MAXIMUM_SESSION() {
		return DBINFO_MAXIMUM_SESSION;
	}
	public void setDBINFO_MAXIMUM_SESSION(String dBINFO_MAXIMUM_SESSION) {
		DBINFO_MAXIMUM_SESSION = dBINFO_MAXIMUM_SESSION;
	}
	public String getDBINFO_CURRENT_SESSION() {
		return DBINFO_CURRENT_SESSION;
	}
	public void setDBINFO_CURRENT_SESSION(String dBINFO_CURRENT_SESSION) {
		DBINFO_CURRENT_SESSION = dBINFO_CURRENT_SESSION;
	}
	public String getDBINFO_CACHE_HITRATE() {
		return DBINFO_CACHE_HITRATE;
	}
	public void setDBINFO_CACHE_HITRATE(String dBINFO_CACHE_HITRATE) {
		DBINFO_CACHE_HITRATE = dBINFO_CACHE_HITRATE;
	}
	public String getLOCKINFO() {
		return LOCKINFO;
	}
	public void setLOCKINFO(String lOCKINFO) {
		LOCKINFO = lOCKINFO;
	}
	public String getCollect_time() {
		return collect_time;
	}
	public void setCollect_time(String collect_time) {
		this.collect_time = collect_time;
	}
	public String getLOCKINFO_TABLE() {
		return LOCKINFO_TABLE;
	}
	public void setLOCKINFO_TABLE(String lOCKINFO_TABLE) {
		LOCKINFO_TABLE = lOCKINFO_TABLE;
	}
	public String getLOCKINFO_ROW() {
		return LOCKINFO_ROW;
	}
	public void setLOCKINFO_ROW(String lOCKINFO_ROW) {
		LOCKINFO_ROW = lOCKINFO_ROW;
	}
	
}
