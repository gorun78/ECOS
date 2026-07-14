package com.chinacreator.gzcm.runtime.core.monitor.rpc;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.monitor.strategy.bean.StrategyBean;

public interface IAgentRpcCaller {

	/**
	 * 娑撳﹥濮ら弫鐗堝祦
	 * 
	 * @throws Exception
	 */
	public void uploadDatas(String plugin,
			Map<String, Map<String, String>> datas) throws Exception;

	public List<StrategyBean> getAllStrategy() throws Exception;

}
