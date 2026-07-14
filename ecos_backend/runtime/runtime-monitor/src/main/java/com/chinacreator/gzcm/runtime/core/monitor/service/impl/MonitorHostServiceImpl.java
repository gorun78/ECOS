package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorHostService;
import com.chinacreator.gzcm.runtime.core.monitor.monitorhost.bean.MonitorInfoBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 监控主机服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorHostServiceImpl implements IMonitorHostService {
    
    // 内存存储：monitor_object_id -> List<MonitorInfoBean>
    private final Map<String, List<MonitorInfoBean>> monitorInfoCache = new ConcurrentHashMap<>();
    
    // 内存存储：monitor_object_id -> List<Map<String, String>> (图表URL)
    private final Map<String, List<Map<String, String>>> chartUrlCache = new ConcurrentHashMap<>();
    
    @Override
    public PageInfo<?> getAllMonitors(int startRow, int pagesize) throws Exception {
        // 模拟数据
        List<MonitorInfoBean> datas = new ArrayList<>();
        for (int i = 0; i < Math.min(pagesize, 10); i++) {
            MonitorInfoBean bean = new MonitorInfoBean();
            bean.setMonitor_object_id("monitor_" + i);
            bean.setUsable_status("ACTIVE");
            bean.setCollect_time(String.valueOf(System.currentTimeMillis()));
            datas.add(bean);
        }

        // 计算页码 (startRow从0开始)
        int pageNum = (startRow / pagesize) + 1;
        PageInfo<MonitorInfoBean> pageInfo = new PageInfo<>(datas, datas.size(), pageNum, pagesize);

        return pageInfo;
    }
    
    @Override
    public List<MonitorInfoBean> getMonitorInfoById(String monitor_object_id) throws Exception {
        List<MonitorInfoBean> beans = monitorInfoCache.get(monitor_object_id);
        if (beans == null) {
            beans = new ArrayList<>();
            // 创建默认监控信息
            MonitorInfoBean bean = new MonitorInfoBean();
            bean.setMonitor_object_id(monitor_object_id);
            bean.setUsable_status("ACTIVE");
            bean.setSave_longest_time("30");
            bean.setLast_monitor_time(String.valueOf(System.currentTimeMillis()));
            bean.setCollect_time(String.valueOf(System.currentTimeMillis()));
            bean.setTarget_path("system");
            bean.setTarget_value("100");
            beans.add(bean);
            
            monitorInfoCache.put(monitor_object_id, beans);
        }
        return new ArrayList<>(beans);
    }
    
    @Override
    public List<Map<String, String>> getMapUrls(String monitor_object_id) throws Exception {
        List<Map<String, String>> urls = chartUrlCache.get(monitor_object_id);
        if (urls == null) {
            urls = new ArrayList<>();
            // 创建默认图表URL
            Map<String, String> url1 = new HashMap<>();
            url1.put("name", "CPU使用率");
            url1.put("url", "/api/monitor/chart/cpu?monitor_object_id=" + monitor_object_id);
            urls.add(url1);
            
            Map<String, String> url2 = new HashMap<>();
            url2.put("name", "内存使用率");
            url2.put("url", "/api/monitor/chart/memory?monitor_object_id=" + monitor_object_id);
            urls.add(url2);
            
            chartUrlCache.put(monitor_object_id, urls);
        }
        return new ArrayList<>(urls);
    }
    
    @Override
    public Map<String, String> getDiskMapUrls(String[] disks, String monitor_object_id) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (disks != null) {
            for (String disk : disks) {
                result.put(disk, "/api/monitor/chart/disk?monitor_object_id=" + monitor_object_id + "&disk=" + disk);
            }
        }
        return result;
    }
    
    @Override
    public Map<String, String> getMapUrlsByDate(String beginTime, String endTime, String monitor_object_id) throws Exception {
        Map<String, String> result = new HashMap<>();
        result.put("cpu", "/api/monitor/chart/cpu?monitor_object_id=" + monitor_object_id 
            + "&beginTime=" + beginTime + "&endTime=" + endTime);
        result.put("memory", "/api/monitor/chart/memory?monitor_object_id=" + monitor_object_id 
            + "&beginTime=" + beginTime + "&endTime=" + endTime);
        return result;
    }
    
    @Override
    public Map<String, String> getDiskMapUrlsByDate(String beginTime, String endTime, String monitor_object_id, String[] disks) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (disks != null) {
            for (String disk : disks) {
                result.put(disk, "/api/monitor/chart/disk?monitor_object_id=" + monitor_object_id 
                    + "&disk=" + disk + "&beginTime=" + beginTime + "&endTime=" + endTime);
            }
        }
        return result;
    }
    
    @Override
    public List<MonitorInfoBean> getDiskinfoByDisk(String monitor_object_id, String disk) throws Exception {
        List<MonitorInfoBean> beans = new ArrayList<>();
        MonitorInfoBean bean = new MonitorInfoBean();
        bean.setMonitor_object_id(monitor_object_id);
        bean.setTarget_path("disk." + disk);
        bean.setTarget_value("50.0");
        bean.setCollect_time(String.valueOf(System.currentTimeMillis()));
        beans.add(bean);
        return beans;
    }
}

