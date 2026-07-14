package com.chinacreator.gzcm.runtime.core.monitor.plugin.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

/**
 * IPluginTargetDao
 * <p>
 * Copyright: Chinacreator (c) 2012
 * </p>
 * <p>
 * Company: 濠€鏍у础缁夋垵鍨辨穱鈩冧紖閹垛偓閺堫垵鍋傛禒鑺ユ箒闂勬劕鍙曢崣?
 * </p>
 * 
 */
public interface IPluginTargetDao {

	/**
	 * 閺屻儴顕楃粭锕€鎮庨弶鈥叉閻ㄥ嫭褰冩禒鍫曞櫚闂嗗棙瀵氶弽鍥х湴缁狙冨灙鐞?
	 * @param condition 閺屻儴顕楅弶鈥叉
	 * @param sortBy 閹烘帒绨弶鈥叉,閻?-"瀵偓婢剁銆冪粈娲鎼村繑甯撻崚妤嬬礉閸氾箑鍨弰顖氬磳鎼村繈鈧倸顩?-name"鐞涖劎銇氶幐澶婃倳缁変即妾锋惔蹇ョ礉"name"鐞涖劎銇氶幐澶婃倳缁夋澘宕屾惔蹇斿笓鎼? 
	 * @return
	 * @throws Exception
	 */
	public List<PluginTargetBean> find(PluginTargetBean condition) throws Exception;
	
	public void insertBeans(List<PluginTargetBean> beans, String dbname)throws Exception;
	
		/**
	 * 閸掔娀娅庨幓鎺嶆闁插洭娉﹂幐鍥ㄧ垼鐏炲倻楠?
	 * @param pluginName 閹绘帊娆㈤柌鍥肠閹稿洦鐖ｇ仦鍌滈獓pluginName
		 * @param dbname 
	 * @return 閸掔娀娅庣紒鎾寸亯,true娑撶儤鍨氶崝鐕傜礉false娑撳搫銇戠拹?
	 * @throws Exception
	 */
	public boolean delete(String pluginName, String dbname) throws Exception;
	
	public PluginTargetBean findBeanByPKValue(String pluginName, String itemPath)throws Exception;
	
	public List<PluginTargetBean> findRootAndFirstLevelTargets(PluginTargetBean condition)throws Exception;
}
