package com.chinacreator.gzcm.runtime.core.monitor.interfaces;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.monitorhost.bean.MonitorInfoBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

public interface IMonitorHostService {

	public PageInfo<?> getAllMonitors(int startRow,int pagesize) throws Exception;

	public List<MonitorInfoBean> getMonitorInfoById(String monitor_object_id) throws Exception;

	public List<Map<String,String>> getMapUrls(String monitor_object_id) throws Exception;

	public Map<String,String> getDiskMapUrls(String[] disks,String monitor_object_id)throws Exception;

	public Map<String,String> getMapUrlsByDate(String beginTime,String endTime,String monitor_object_id)throws Exception;

	public Map<String,String> getDiskMapUrlsByDate(String beginTime,String endTime,String monitor_object_id,String[] disks)throws Exception;

	public List<MonitorInfoBean> getDiskinfoByDisk(String monitor_object_id,String disk)throws Exception;
}
