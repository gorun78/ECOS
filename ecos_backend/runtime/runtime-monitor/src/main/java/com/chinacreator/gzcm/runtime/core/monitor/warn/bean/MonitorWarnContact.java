package com.chinacreator.gzcm.runtime.core.monitor.warn.bean;

import java.io.Serializable;

import jakarta.xml.bind.annotation.XmlRootElement;


/**
 * MonitorWarnContact MonitorWarnContact
 * <p>
 * Copyright: Chinacreator (c) 2013-01-14
 * </p>
 * <p>
 * Company: 濠€鏍у础缁夋垵鍨辨穱鈩冧紖閹垛偓閺堫垵鍋傛禒鑺ユ箒闂勬劕鍙曢崣?
 * </p>
 * 
 */
@XmlRootElement
public class MonitorWarnContact implements Serializable {
	
	private static final long serialVersionUID = -7383057456504058896L;
	private String contact_id;
	private String isuse_detail;
	
	public String getIsuse_detail() {
		return isuse_detail;
	}

	public void setIsuse_detail(String isuse_detail) {
		this.isuse_detail = isuse_detail;
	}

	private String isuse;
	
	public String getIsuse() {
		return isuse;
	}

	public void setIsuse(String isuse) {
		this.isuse = isuse;
	}

	private String contact_email;

	
	private String contact_name;

	
	private String contact_note;

	
	private String contact_mobilephone;
	

	private String sortStr; // 閺屻儴顕桽QL閹烘帒绨€涙劕褰?

	
	private String contact_abort;
	
	public String getSortStr() {
		return sortStr;
	}

	public String getContact_id() {
		return contact_id;
	}

	public void setContact_id(String contact_id) {
		this.contact_id = contact_id;
	}

	public String getContact_email() {
		return contact_email;
	}

	public void setContact_email(String contact_email) {
		this.contact_email = contact_email;
	}

	public String getContact_name() {
		return contact_name;
	}

	public void setContact_name(String contact_name) {
		this.contact_name = contact_name;
	}

	public String getContact_note() {
		return contact_note;
	}

	public void setContact_note(String contact_note) {
		this.contact_note = contact_note;
	}

	

	public String getContact_mobilephone() {
		return contact_mobilephone;
	}

	public void setContact_mobilephone(String contact_mobilephone) {
		this.contact_mobilephone = contact_mobilephone;
	}

	public String getContact_abort() {
		return contact_abort;
	}

	public void setContact_abort(String contact_abort) {
		this.contact_abort = contact_abort;
	}

	/**
	 * @param sortStr
	 *            閹烘帒绨€涙劕褰?
	 * @generated
	 */
	public void setSortStr(String sortStr) {
		this.sortStr = sortStr;
	}

}

