package com.chinacreator.gzcm.runtime.core.monitor.rpc;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;
public interface IServerMonitorRpcCaller {
	public Map<String,String>  getMonitorData(String pluginName,MonitorObjectBean obj, String Target) throws Exception;

	public void sendmonitorObject(MonitorObjectBean objbean, int type) throws Exception;

	public void sendStrategys(StrategyBean strategy) throws Exception;
	
	public List<String> getTargetChildsName(String pluginName,MonitorObjectBean obj, String Target) throws Exception;
}
