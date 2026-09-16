package com.chinacreator.gzcm.runtime.core.monitor.interfaces;

import com.chinacreator.gzcm.runtime.core.monitor.warn.bean.WarnLogBean;
import com.chinacreator.gzcm.runtime.core.util.PageInfo;

import java.util.Map;

/**
 * 告警日志服务契约（runtime-monitor 公共底座）。
 *
 * <p>PMO-59 P4a：新增 {@link #warn(String, String, String, String, String, Map, String)} 七参重载，
 * 默认委托既有五参方法（<b>签名只增不改</b>，既有实现/调用方零破坏）；实现类
 * {@code WarnLogServiceImpl} 覆写该重载以携带 {@code faultContext}/{@code reviewTag}
 * 并落库 {@code ecos_warn_log}（V131），关闭 P2b「告警内存态，进程重启即失」残留风险。</p>
 */
public interface IWarnLogService {

	public PageInfo<WarnLogBean> findByPage(Integer offset, Integer pageSize,
			WarnLogBean condition) throws Exception;

	public void insertWarnLog(WarnLogBean log) throws Exception;

	public void updateLogResult(String logid,String msg) throws Exception;

	public void setLogHanded(String ids) throws Exception ;

	void warn(String type, String objid, String objname, String err, String typeHander) throws Exception;

	/**
	 * 告警（携带结构化故障上下文与复盘标签）— PMO-59 P4a 新增重载。
	 *
	 * <p>默认实现委托五参方法（兼容既有实现类）；{@code WarnLogServiceImpl} 覆写以落库。</p>
	 *
	 * @param faultContext 结构化故障上下文（落 {@code ecos_warn_log.fault_context} JSONB，可空）
	 * @param reviewTag    复盘聚合标签（落 {@code ecos_warn_log.review_tag}，可空）
	 */
	default void warn(String type, String objid, String objname, String err, String typeHander,
			Map<String, Object> faultContext, String reviewTag) throws Exception {
		warn(type, objid, objname, err, typeHander);
	}
}
