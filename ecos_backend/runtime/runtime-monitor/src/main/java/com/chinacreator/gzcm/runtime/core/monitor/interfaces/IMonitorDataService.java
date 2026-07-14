package com.chinacreator.gzcm.runtime.core.monitor.interfaces;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.DatabaseBasicMonitorBean;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.MonitorDataBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

public interface IMonitorDataService {

	public DatabaseBasicMonitorBean getDatabaseBasicMonitorData(String monitor_object_id)throws Exception;

	public String getDatabaseBasicPicUrl(String monitor_object_id)throws Exception;

	public String getDatabaseBasicPicUrlByDate(String beginTime,String endTime,String monitor_object_id)throws Exception;

	public PageInfo<?> getDatabaseTableInfo(int startRow,int pagesize,String monitor_object_id)throws Exception;

	public List<String>getAllTableSpaceNames(String monitor_object_id)throws Exception;

	public List<MonitorDataBean> getHostInfo(String monitor_object_id)throws Exception;

}
