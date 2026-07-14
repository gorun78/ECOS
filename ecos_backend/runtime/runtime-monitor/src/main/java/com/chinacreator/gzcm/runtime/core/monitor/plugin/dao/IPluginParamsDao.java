package com.chinacreator.gzcm.runtime.core.monitor.plugin.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginParam;

public interface IPluginParamsDao {

	public void insertParams(List<PluginParam> list, String dbname)throws Exception;
	
	public void delete(String pluginName, String dbname)throws Exception;
	
}
