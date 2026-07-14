package com.chinacreator.gzcm.runtime.core.monitor.plugin.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginBean;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

public interface IPluginDao {

	/**
	 * 閺嶈宓侀幓鎺嶆name瀵版鍩岄幓鎺嶆娣団剝浼?
	 * @param pluginId
	 * @return
	 * @throws Exception
	 */
	public PluginBean getPluginBeanWithName(String pluginName) throws Exception;
	
	
	/**
	 * 閺嶈宓侀幓鎺嶆閸氬秶袨閿涘苯绶遍崚鐗堝絻娴犺埖澧嶉張澶嬪瘹閺嶅洭銆?
	 * @param pluginName
	 * @return
	 * @throws Exception
	 */
	public List<PluginTargetBean> getPluginTargetsWithPluginName(String pluginName) throws Exception;
	
	public List<PluginBean>getAllPlugins(String dbname)throws Exception;
	
	public void addPlugin(PluginBean bean, String dbname)throws Exception;
	
	public void update(PluginBean bean, String dbname)throws Exception;

}
