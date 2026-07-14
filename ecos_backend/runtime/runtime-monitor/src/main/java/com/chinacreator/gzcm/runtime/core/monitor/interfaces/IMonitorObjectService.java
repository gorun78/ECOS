package com.chinacreator.gzcm.runtime.core.monitor.interfaces;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.NodeProcessBean;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;
public interface IMonitorObjectService {

	public PageInfo<MonitorObjectBean> queryMonitorObjectByPage(Integer offset, Integer pageSize, MonitorObjectBean condition)throws Exception;

	public void saveMonitorObject(MonitorObjectBean bean)throws Exception;

	public void updateMonitorObject(MonitorObjectBean bean)throws Exception;

	public void removeMonitorObject(String monitorObjId)throws Exception;

	public MonitorObjectBean findMonitorObjectById(String monitorObjId)throws Exception;

	public Map<String,String> getMonitorObjectParams(String monitorObjId) throws Exception;

	public List<String> getHavenHostOrDB(String plugin_name)throws Exception;

	public Map<String,String> getMonitorTargetsDetail(String plugin_name,String monitor_object_id) throws Exception;

	public List<MonitorObjectBean> findAllMonitorObject()throws Exception;

	public List<String> getObjectMonitorTargets(String obj_id,String plugin_name) throws Exception;

	public List<String> getRunningNode() throws Exception;

	public List<String> getRunningSchedules() throws Exception;

	public List<NodeProcessBean> getAllProcess() throws Exception;

	public boolean isOutStartTimeLimit(long time) throws Exception;

	public NodeProcessBean getProcessBeanByName(String processName) throws Exception;

}
