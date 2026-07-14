package com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean;

import java.io.Serializable;

public class DatabaseTableBean implements Serializable {

	private static final long serialVersionUID = -6890551943072601347L;
	private String TABLESPACEINFO;
	private String TABLESPACE_TOTAL;
	private String TABLESPACE_UNUSEDSIZE;
	private String TABLESPACE_SUIPIANRATE;
	private String TARGET_CHILD_NEW;
	public String getTABLESPACEINFO() {
		return TABLESPACEINFO;
	}
	public void setTABLESPACEINFO(String tABLESPACEINFO) {
		TABLESPACEINFO = tABLESPACEINFO;
	}
	public String getTABLESPACE_TOTAL() {
		return TABLESPACE_TOTAL;
	}
	public void setTABLESPACE_TOTAL(String tABLESPACE_TOTAL) {
		TABLESPACE_TOTAL = tABLESPACE_TOTAL;
	}
	public String getTABLESPACE_UNUSEDSIZE() {
		return TABLESPACE_UNUSEDSIZE;
	}
	public void setTABLESPACE_UNUSEDSIZE(String tABLESPACE_UNUSEDSIZE) {
		TABLESPACE_UNUSEDSIZE = tABLESPACE_UNUSEDSIZE;
	}
	public String getTABLESPACE_SUIPIANRATE() {
		return TABLESPACE_SUIPIANRATE;
	}
	public void setTABLESPACE_SUIPIANRATE(String tABLESPACE_SUIPIANRATE) {
		TABLESPACE_SUIPIANRATE = tABLESPACE_SUIPIANRATE;
	}
	public String getTARGET_CHILD_NEW() {
		return TARGET_CHILD_NEW;
	}
	public void setTARGET_CHILD_NEW(String tARGET_CHILD_NEW) {
		TARGET_CHILD_NEW = tARGET_CHILD_NEW;
	}
	
}
