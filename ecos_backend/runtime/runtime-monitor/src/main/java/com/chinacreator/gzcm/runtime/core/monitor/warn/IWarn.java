package com.chinacreator.gzcm.runtime.core.monitor.warn;

public interface IWarn {
	public void warn(String type, String objid,String objname, String err,String logid) throws Exception;
	
	public Runnable createTask() throws Exception;
}
