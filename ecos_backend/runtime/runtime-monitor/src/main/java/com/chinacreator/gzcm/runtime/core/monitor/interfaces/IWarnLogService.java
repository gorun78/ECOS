package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

public interface IWarnLogService {

	public PageInfo<WarnLogBean> findByPage(Integer offset, Integer pageSize,
			WarnLogBean condition) throws Exception;

	public void insertWarnLog(WarnLogBean log) throws Exception;

	public void updateLogResult(String logid,String msg) throws Exception;

	public void setLogHanded(String ids) throws Exception ;

	void warn(String type, String objid, String objname, String err, String typeHander) throws Exception;
}
