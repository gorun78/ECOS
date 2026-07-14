package com.chinacreator.gzcm.runtime.core.monitor.interfaces;
import java.util.Date;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.MonitorDataBean;

/**
 * 閻╂垶甯堕張宥呭缁旑垱鏆熼幑顔碱槱閻炲棙甯撮崣?
 * 
 * @author sunzhiyong
 * 
 */
public interface IMonitorDataHandleService {

	/**
	 * 闁插洭娉﹂弫鐗堝祦閻ㄥ嫭瀵旀稊鍛
	 * 
	 * @param datas
	 * @throws Exception
	 */
	public void persistentMonitorData(List<MonitorDataBean> datas) throws Exception;

	/**
	 * 閻㈢喐鍨氶弻鎰嚋閹稿洦鐖ｆい瑙勭厙濞堝灚妞傞梻瀵告畱鐡掑濞嶉崶?
	 * @param objectId
	 * @param itemPath
	 * @param itemChildPath
	 * @param startTime
	 * @param endTime
	 * @return
	 * @throws Exception
	 */
	public byte[] creatorImg(String objectId, String itemPath,String itemChildPath, Date startTime,
			Date endTime) throws Exception;
	
}
