package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectTarget;

public interface IMonitorObjectTargetService {

	public void insertTargets(List<MonitorObjectTarget> targets)throws Exception;
	
	public void updateTargets(List<MonitorObjectTarget> targets)throws Exception;
	
	public List<MonitorObjectTarget> queryByMonitorObjId(String monitorObjId)throws Exception;
	
	public List<MonitorObjectTarget> queryDiskMonitor(String monitor_object_id)throws Exception;
	
}
