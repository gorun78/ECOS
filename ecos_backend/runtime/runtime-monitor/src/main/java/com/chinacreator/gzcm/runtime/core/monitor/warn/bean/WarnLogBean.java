package com.chinacreator.gzcm.runtime.core.monitor.warn.bean;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class WarnLogBean implements Serializable {

	private static final long serialVersionUID = -6935378940347546846L;
	private String log_id; //
	private String warn_message; //
	private String warn_note; //
	private String warn_objid; //
	private String warn_objname; //
	private String warn_result; //
	private Timestamp warn_time; //
	private String warn_type; //
	private String warn_typename;
	private String warn_hand;
	private String warn_handname;
	private Date startTime;
	private Date endTime;
	private String ishanded;
	private String ishandedname;
	private List<String> logids;

	
	
	public String getIshandedname() {
		return ishandedname;
	}

	public void setIshandedname(String ishandedname) {
		this.ishandedname = ishandedname;
	}

	public List<String> getLogids() {
		return logids;
	}

	public void setLogids(List<String> logids) {
		this.logids = logids;
	}

	public String getIshanded() {
		return ishanded;
	}

	public void setIshanded(String ishanded) {
		this.ishanded = ishanded;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public String getWarn_typename() {
		return warn_typename;
	}

	public void setWarn_typename(String warn_typename) {
		this.warn_typename = warn_typename;
	}

	public String getWarn_hand() {
		return warn_hand;
	}

	public void setWarn_hand(String warn_hand) {
		this.warn_hand = warn_hand;
	}

	public String getWarn_handname() {
		return warn_handname;
	}

	public void setWarn_handname(String warn_handname) {
		this.warn_handname = warn_handname;
	}

	public String getLog_id() {
		return log_id;
	}

	public void setLog_id(String log_id) {
		this.log_id = log_id;
	}

	public String getWarn_message() {
		return warn_message;
	}

	public void setWarn_message(String warn_message) {
		this.warn_message = warn_message;
	}

	public String getWarn_note() {
		return warn_note;
	}

	public void setWarn_note(String warn_note) {
		this.warn_note = warn_note;
	}

	public String getWarn_objid() {
		return warn_objid;
	}

	public void setWarn_objid(String warn_objid) {
		this.warn_objid = warn_objid;
	}

	public String getWarn_objname() {
		return warn_objname;
	}

	public void setWarn_objname(String warn_objname) {
		this.warn_objname = warn_objname;
	}

	public String getWarn_result() {
		return warn_result;
	}

	public void setWarn_result(String warn_result) {
		this.warn_result = warn_result;
	}



	public Timestamp getWarn_time() {
		return warn_time;
	}

	public void setWarn_time(Timestamp warn_time) {
		this.warn_time = (warn_time != null ? new Timestamp(warn_time.getTime()) : null);
	}

	public String getWarn_type() {
		return warn_type;
	}

	public void setWarn_type(String warn_type) {
		this.warn_type = warn_type;
	}

}
