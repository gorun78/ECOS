package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginBean;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

public interface IPluginService {
	
	/**
	 * 濞夈劌鍞介幓鎺嶆
	 * @param listplugins
	 * @param dbname 
	 * @throws Exception
	 */
	public void regPlugins(List<PluginBean> listplugins, String dbname) throws Exception;

	/**
	 * 閺囧瓨鏌婇幓鎺嶆
	 * @param listplugins
	 * @param dbname 
	 * @throws Exception
	 */
	public void updatePlugins(List<PluginBean> listplugins, String dbname) throws Exception;
	
	/**
	 * 瀵版鍩岄幍鈧張澶嬪絻娴犲爼娉﹂崥?
	 * @param dbname 
	 * @throws Exception
	 */
	public List<PluginBean> getAllPlugins(String dbname) throws Exception;
	
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
	 * @param type  0鏉╂柨娲栭幍鈧張澶涚礉1鏉╂柨娲栫粭?缁狙勫瘹閺?
	 * @return
	 * @throws Exception
	 */
	public List<PluginTargetBean> getPluginTargetsWithPluginName(String pluginName,int type) throws Exception;

	public void savePluginAll(PluginBean bean, String dbname)throws Exception;
	
}
