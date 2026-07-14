package com.chinacreator.gzcm.runtime.core.monitor.rpc;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.NodeProcessBean;

public interface INodeFlowRpcCaller {

	/**
	 * 濞翠胶鈻奸惄鎴炲付閺佺増宓佹稉濠佺炊
	 * 
	 * @throws Exception
	 */
	public void uploadFlowsData(List<NodeProcessBean> list) throws Exception;

}
