package com.chinacreator.gzcm.runtime.core.monitor.monitorobject.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectTarget;

/**
 * IMonitorObjectTargetDao
 * <p>
 * Copyright: Chinacreator (c) 2012
 * </p>
 * <p>
 * Company: 濠€鏍у础缁夋垵鍨辨穱鈩冧紖閹垛偓閺堫垵鍋傛禒鑺ユ箒闂勬劕鍙曢崣?
 * </p>
 * 
 */
public interface IMonitorObjectTargetDao {

	/**
	 * 閺屻儴顕楃粭锕€鎮庨弶鈥叉閻ㄥ嫮娲冮幒褍顕挒鈩冨瘹閺嶅洭銆嶉崚妤勩€?
	 * @param condition 閺屻儴顕楅弶鈥叉
	 * @param sortBy 閹烘帒绨弶鈥叉,閻?-"瀵偓婢剁銆冪粈娲鎼村繑甯撻崚妤嬬礉閸氾箑鍨弰顖氬磳鎼村繈鈧倸顩?-name"鐞涖劎銇氶幐澶婃倳缁変即妾锋惔蹇ョ礉"name"鐞涖劎銇氶幐澶婃倳缁夋澘宕屾惔蹇斿笓鎼? 
	 * @return
	 * @throws Exception
	 */
	public List<MonitorObjectTarget> find(MonitorObjectTarget condition) throws Exception;
	
		/**
	 * 閸掔娀娅庨惄鎴炲付鐎电钖勯幐鍥ㄧ垼妞?
	 * @param monitorObjId
	 * @return 閸掔娀娅庣紒鎾寸亯,true娑撶儤鍨氶崝鐕傜礉false娑撳搫銇戠拹?
	 * @throws Exception
	 */
	public boolean delete(String monitorObjId) throws Exception;
	
	public void insertBeans(List<MonitorObjectTarget> targets)throws Exception;
	
}
