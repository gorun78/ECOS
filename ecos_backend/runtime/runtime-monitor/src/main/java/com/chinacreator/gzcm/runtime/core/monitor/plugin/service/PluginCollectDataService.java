package com.chinacreator.gzcm.runtime.core.monitor.plugin.service;

import java.util.List;
import java.util.Map;

public interface PluginCollectDataService {

	/**
	 * 閼惧嘲褰囬惄鎴炲付鐎电钖勯幐鍥ㄧ垼妞よ鏆熼幑?
	 * @param targetId
	 * @param pargrams
	 * @return
	 * @throws Exception
	 */
	public Map<String,String> getDatasByTarget(String targetId,Map<String,String> pargrams) throws Exception;
	
	
	/**
	 * 閼惧嘲褰囬幐鍥ㄧ垼妞ょ懓鐡欓弽鍥х杽娓氬銆?
	 * @param targetPath
	 * @return
	 * @throws Exception
	 */
	public List<String> getTargetChildsName(String targetPath,Map<String,String> params) throws Exception;
	
	
	/**
	 * 濡偓濞村顕挒鈥冲讲閻劍鈧?
	 * @param pargrams
	 * @return
	 * @throws Exception
	 */
	public Map<String,String> getAvailable(Map<String, String> pargrams) throws Exception;
}
