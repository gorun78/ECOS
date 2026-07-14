package com.chinacreator.gzcm.runtime.core.monitor.server.warn.dao;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.common.util.LegacyListInfo;

public interface IWarnLogDao {

	public LegacyListInfo findByPage(Integer offset, Integer pageSize,
			WarnLogBean condition) throws Exception;
	
	public void insertWarnLog(WarnLogBean log) throws Exception;
	
	public Map<String,String> getWarnTypeCount(WarnLogBean condition, String dbname) throws Exception;
	
	public void updateLogResult(String logid,String msg) throws Exception;
	
	public void setLogHanded(String ids) throws Exception ;
}

