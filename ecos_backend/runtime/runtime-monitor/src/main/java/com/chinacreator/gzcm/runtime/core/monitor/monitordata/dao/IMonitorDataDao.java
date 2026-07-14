package com.chinacreator.gzcm.runtime.core.monitor.monitordata.dao;

import java.sql.Timestamp;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.DatabaseBasicMonitorBean;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.MonitorDataBean;
import com.chinacreator.gzcm.runtime.core.common.util.LegacyListInfo;

/**
 * IMonitorDataDao - 鐩戞帶鏁版嵁DAO鎺ュ彛
 * <p>
 * Copyright: Chinacreator (c) 2012
 * </p>
 * <p>
 * Company: 涓垱杞欢宸ョ▼鑲′唤鏈夐檺鍏徃
 * </p>
 * 
 */
public interface IMonitorDataDao {

	/**
	 * 娣诲姞鐩戞帶鏁版嵁
	 * 
	 * @param item 鐩戞帶鏁版嵁瀵硅薄
	 * @return 鎿嶄綔缁撴灉,true琛ㄧず鎴愬姛,false琛ㄧず澶辫触
	 * @throws Exception
	 */
	public boolean add(MonitorDataBean item) throws Exception;

	/**
	 * 鍒犻櫎鐩戞帶鏁版嵁
	 * 
	 * @param monitorObjId 鐩戞帶瀵硅薄ID
	 * @param itemPath 鏁版嵁椤硅矾寰?
	 * @return 鎿嶄綔缁撴灉,true琛ㄧず鎴愬姛,false琛ㄧず澶辫触
	 * @throws Exception
	 */
	public boolean delete(String monitorObjId,String itemPath) throws Exception;

	/**
	 * 鍒犻櫎鏈夊瓙鑺傜偣鐨勭洃鎺ф暟鎹?
	 * 
	 * @param monitorObjId 鐩戞帶瀵硅薄ID
	 * @param itemPath 鏁版嵁椤硅矾寰?
	 * @param itemPathChild 瀛愭暟鎹」璺緞
	 * @throws Exception
	 */
	public void deleteIfHasChild(String monitorObjId,String itemPath,String itemPathChild)throws Exception;
	
	/**
	 * 鑾峰彇鎸囧畾鏃堕棿鑼冨洿鍐呯殑鐩戞帶鏁版嵁
	 * 
	 * @param objectId 瀵硅薄ID
	 * @param itemPath 鏁版嵁椤硅矾寰?
	 * @param itemChildPath 瀛愭暟鎹」璺緞
	 * @param startTime 寮€濮嬫椂闂?
	 * @param endTime 缁撴潫鏃堕棿
	 * @return 鐩戞帶鏁版嵁鍒楄〃
	 * @throws Exception
	 */
	public List<MonitorDataBean> getMonitorDatasIn(String objectId,
			String itemPath,String itemChildPath, Timestamp startTime, Timestamp endTime)
			throws Exception;

	/**
	 * 鑾峰彇鏈€鍚庝竴娆￠噰闆嗙殑鏁版嵁
	 * 
	 * @param item 鐩戞帶鏁版嵁瀵硅薄
	 * @return 鏈€鍚庝竴娆￠噰闆嗙殑鐩戞帶鏁版嵁
	 * @throws Exception
	 */
	public MonitorDataBean getLastCollectData(MonitorDataBean item)
			throws Exception;

	/**
	 * 鑾峰彇鍘嗗彶鏁版嵁
	 * 
	 * @param item 鐩戞帶鏁版嵁瀵硅薄
	 * @return 鍘嗗彶鏁版嵁鍒楄〃
	 * @throws Exception
	 */
	public List<MonitorDataBean> getHistoryDatas(MonitorDataBean item)throws Exception;
	
	/**
	 * 鏍规嵁鐩戞帶瀵硅薄ID鍒犻櫎鎵€鏈夌浉鍏虫暟鎹?
	 * 
	 * @param monitorObjId 鐩戞帶瀵硅薄ID
	 * @throws Exception
	 */
	public void deleteByMonitorObjId(String monitorObjId)throws Exception;
	
	/**
	 * 鑾峰彇鏁版嵁搴撳熀纭€鐩戞帶鏁版嵁
	 * 
	 * @param monitor_object_id 鐩戞帶瀵硅薄ID
	 * @return 鏁版嵁搴撳熀纭€鐩戞帶鏁版嵁
	 * @throws Exception
	 */
	public DatabaseBasicMonitorBean getDatabaseBasicMonitorData(String monitor_object_id) throws Exception;
	
	/**
	 * 鍒嗛〉鑾峰彇鏁版嵁搴撹〃鐩戞帶鏁版嵁鍒楄〃
	 * 
	 * @param startRow 璧峰琛?
	 * @param pagesize 姣忛〉澶у皬
	 * @param monitor_object_id 鐩戞帶瀵硅薄ID
	 * @return 鍒嗛〉缁撴灉
	 * @throws Exception
	 */
	public LegacyListInfo getDatabaseTableMonitorDataList(int startRow ,int pagesize,String monitor_object_id) throws Exception;
	
	/**
	 * 鑾峰彇鎵€鏈夎〃绌洪棿鍚嶇О
	 * 
	 * @param monitor_object_id 鐩戞帶瀵硅薄ID
	 * @return 琛ㄧ┖闂村悕绉板垪琛?
	 * @throws Exception
	 */
	public List<MonitorDataBean>getAllTableSpaceNames(String monitor_object_id)throws Exception;
	
	/**
	 * 鑾峰彇涓绘満淇℃伅
	 * 
	 * @param monitor_object_id 鐩戞帶瀵硅薄ID
	 * @return 涓绘満淇℃伅鍒楄〃
	 * @throws Exception
	 */
	public List<MonitorDataBean> getHostInfo(String monitor_object_id)throws Exception;
}

