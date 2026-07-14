package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorDataService;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.DatabaseBasicMonitorBean;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.MonitorDataBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 监控数据服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorDataServiceImpl implements IMonitorDataService {
    
    // 内存存储：monitor_object_id -> DatabaseBasicMonitorBean
    private final Map<String, DatabaseBasicMonitorBean> databaseMonitorCache = new ConcurrentHashMap<>();
    
    // 内存存储：monitor_object_id -> List<MonitorDataBean>
    private final Map<String, List<MonitorDataBean>> hostInfoCache = new ConcurrentHashMap<>();
    
    // 内存存储：monitor_object_id -> List<String> (表空间名称)
    private final Map<String, List<String>> tableSpaceCache = new ConcurrentHashMap<>();
    
    @Override
    public DatabaseBasicMonitorBean getDatabaseBasicMonitorData(String monitor_object_id) throws Exception {
        DatabaseBasicMonitorBean bean = databaseMonitorCache.get(monitor_object_id);
        if (bean == null) {
            // 创建默认的监控数据
            bean = new DatabaseBasicMonitorBean();
            bean.setDBINFO("N/A");
            bean.setDBINFO_MAXIMUM_SESSION("0");
            bean.setDBINFO_CURRENT_SESSION("0");
            bean.setDBINFO_CACHE_HITRATE("0%");
            bean.setLOCKINFO("N/A");
            bean.setLOCKINFO_TABLE("0");
            bean.setLOCKINFO_ROW("0");
            bean.setCollect_time(String.valueOf(System.currentTimeMillis()));
            databaseMonitorCache.put(monitor_object_id, bean);
        }
        return bean;
    }
    
    @Override
    public String getDatabaseBasicPicUrl(String monitor_object_id) throws Exception {
        // 占位实现：返回占位URL
        return "/api/monitor/chart/database-basic?monitor_object_id=" + monitor_object_id;
    }
    
    @Override
    public String getDatabaseBasicPicUrlByDate(String beginTime, String endTime, String monitor_object_id) throws Exception {
        // 占位实现：返回带时间范围的URL
        return "/api/monitor/chart/database-basic?monitor_object_id=" + monitor_object_id 
            + "&beginTime=" + beginTime + "&endTime=" + endTime;
    }
    
    @Override
    public PageInfo<?> getDatabaseTableInfo(int startRow, int pagesize, String monitor_object_id) throws Exception {
        // 创建分页结果
        List<Object> datas = new ArrayList<>();
        for (int i = 0; i < Math.min(pagesize, 10); i++) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("table_name", "table_" + i);
            row.put("row_count", 1000 + i * 100);
            row.put("size_mb", 10 + i);
            datas.add(row);
        }

        // 计算页码 (startRow从0开始)
        int pageNum = (startRow / pagesize) + 1;
        PageInfo<Object> pageInfo = new PageInfo<>(datas, datas.size(), pageNum, pagesize);

        return pageInfo;
    }
    
    @Override
    public List<String> getAllTableSpaceNames(String monitor_object_id) throws Exception {
        List<String> names = tableSpaceCache.get(monitor_object_id);
        if (names == null) {
            names = new ArrayList<>();
            names.add("SYSTEM");
            names.add("USERS");
            names.add("TEMP");
            tableSpaceCache.put(monitor_object_id, names);
        }
        return new ArrayList<>(names);
    }
    
    @Override
    public List<MonitorDataBean> getHostInfo(String monitor_object_id) throws Exception {
        List<MonitorDataBean> beans = hostInfoCache.get(monitor_object_id);
        if (beans == null) {
            beans = new ArrayList<>();
            // 创建默认主机信息
            MonitorDataBean bean = new MonitorDataBean();
            bean.setMonitor_object_id(monitor_object_id);
            bean.setTarget_path("system.cpu.usage");
            bean.setTarget_value("50.0");
            bean.setCollect_time(new java.sql.Timestamp(System.currentTimeMillis()));
            beans.add(bean);
            
            MonitorDataBean bean2 = new MonitorDataBean();
            bean2.setMonitor_object_id(monitor_object_id);
            bean2.setTarget_path("system.memory.usage");
            bean2.setTarget_value("60.0");
            bean2.setCollect_time(new java.sql.Timestamp(System.currentTimeMillis()));
            beans.add(bean2);
            
            hostInfoCache.put(monitor_object_id, beans);
        }
        return new ArrayList<>(beans);
    }
}

