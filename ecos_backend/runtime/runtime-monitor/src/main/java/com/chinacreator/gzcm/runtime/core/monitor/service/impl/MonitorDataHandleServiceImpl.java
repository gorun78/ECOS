package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorDataHandleService;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.MonitorDataBean;

/**
 * 监控数据处理服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorDataHandleServiceImpl implements IMonitorDataHandleService {
    
    // 内存存储：objectId_itemPath -> List<MonitorDataBean>
    private final Map<String, List<MonitorDataBean>> dataStore = new ConcurrentHashMap<>();
    
    @Override
    public void persistentMonitorData(List<MonitorDataBean> datas) throws Exception {
        if (datas == null || datas.isEmpty()) {
            return;
        }
        
        for (MonitorDataBean data : datas) {
            String key = data.getMonitor_object_id() + "_" + data.getTarget_path();
            dataStore.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(data);
        }
    }
    
    @Override
    public byte[] creatorImg(String objectId, String itemPath, String itemChildPath, Date startTime, Date endTime) throws Exception {
        // 占位实现：返回空的图片字节数组
        // 实际应该根据监控数据生成图表
        return new byte[0];
    }
}

