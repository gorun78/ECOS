package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorObjectService;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean.NodeProcessBean;
import com.chinacreator.gzcm.runtime.core.common.util.PageInfo;

/**
 * 监控对象服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorObjectServiceImpl implements IMonitorObjectService {
    
    // 内存存储：monitor_object_id -> MonitorObjectBean
    private final Map<String, MonitorObjectBean> monitorObjectStore = new ConcurrentHashMap<>();
    
    // 内存存储：monitor_object_id -> Map<String, String> (参数)
    private final Map<String, Map<String, String>> paramsStore = new ConcurrentHashMap<>();
    
    // 内存存储：plugin_name -> List<String> (监控对象ID列表)
    private final Map<String, List<String>> pluginObjectMap = new ConcurrentHashMap<>();
    
    // 内存存储：obj_id + plugin_name -> List<String> (目标列表)
    private final Map<String, List<String>> targetMap = new ConcurrentHashMap<>();
    
    // 运行中的节点列表
    private final List<String> runningNodes = new ArrayList<>();
    
    // 运行中的调度列表
    private final List<String> runningSchedules = new ArrayList<>();
    
    // 进程列表
    private final Map<String, NodeProcessBean> processMap = new ConcurrentHashMap<>();
    
    @Override
    public PageInfo<MonitorObjectBean> queryMonitorObjectByPage(Integer offset, Integer pageSize, MonitorObjectBean condition) throws Exception {
        // 过滤监控对象
        List<MonitorObjectBean> allObjects = new ArrayList<>(monitorObjectStore.values());
        List<MonitorObjectBean> filtered = allObjects;

        if (condition != null) {
            filtered = allObjects.stream()
                .filter(obj -> {
                    if (condition.getMonitor_object_name() != null &&
                        !obj.getMonitor_object_name().contains(condition.getMonitor_object_name())) {
                        return false;
                    }
                    if (condition.getPlugin_name() != null &&
                        !obj.getPlugin_name().equals(condition.getPlugin_name())) {
                        return false;
                    }
                    if (condition.getUsable_status() != null &&
                        !obj.getUsable_status().equals(condition.getUsable_status())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }

        // 分页
        int start = offset != null ? offset : 0;
        int size = pageSize != null ? pageSize : 10;
        int end = Math.min(start + size, filtered.size());
        List<MonitorObjectBean> datas = new ArrayList<>();
        for (int i = start; i < end; i++) {
            datas.add(filtered.get(i));
        }

        // 计算页码 (offset从0开始)
        int pageNum = (start / size) + 1;
        PageInfo<MonitorObjectBean> pageInfo = new PageInfo<>(datas, filtered.size(), pageNum, size);

        return pageInfo;
    }
    
    @Override
    public void saveMonitorObject(MonitorObjectBean bean) throws Exception {
        if (bean.getMonitor_object_id() == null) {
            bean.setMonitor_object_id("monitor_" + System.currentTimeMillis());
        }
        monitorObjectStore.put(bean.getMonitor_object_id(), bean);
        
        // 更新插件映射
        if (bean.getPlugin_name() != null) {
            pluginObjectMap.computeIfAbsent(bean.getPlugin_name(), k -> new ArrayList<>())
                .add(bean.getMonitor_object_id());
        }
    }
    
    @Override
    public void updateMonitorObject(MonitorObjectBean bean) throws Exception {
        if (bean.getMonitor_object_id() == null) {
            throw new IllegalArgumentException("Monitor object ID cannot be null");
        }
        monitorObjectStore.put(bean.getMonitor_object_id(), bean);
    }
    
    @Override
    public void removeMonitorObject(String monitorObjId) throws Exception {
        MonitorObjectBean bean = monitorObjectStore.remove(monitorObjId);
        if (bean != null && bean.getPlugin_name() != null) {
            List<String> objects = pluginObjectMap.get(bean.getPlugin_name());
            if (objects != null) {
                objects.remove(monitorObjId);
            }
        }
        paramsStore.remove(monitorObjId);
    }
    
    @Override
    public MonitorObjectBean findMonitorObjectById(String monitorObjId) throws Exception {
        return monitorObjectStore.get(monitorObjId);
    }
    
    @Override
    public Map<String, String> getMonitorObjectParams(String monitorObjId) throws Exception {
        Map<String, String> params = paramsStore.get(monitorObjId);
        return params != null ? new HashMap<>(params) : new HashMap<>();
    }
    
    @Override
    public List<String> getHavenHostOrDB(String plugin_name) throws Exception {
        List<String> objects = pluginObjectMap.get(plugin_name);
        return objects != null ? new ArrayList<>(objects) : new ArrayList<>();
    }
    
    @Override
    public Map<String, String> getMonitorTargetsDetail(String plugin_name, String monitor_object_id) throws Exception {
        String key = monitor_object_id + "_" + plugin_name;
        List<String> targets = targetMap.get(key);
        Map<String, String> result = new HashMap<>();
        if (targets != null) {
            for (String target : targets) {
                result.put(target, "target_detail_" + target);
            }
        }
        return result;
    }
    
    @Override
    public List<MonitorObjectBean> findAllMonitorObject() throws Exception {
        return new ArrayList<>(monitorObjectStore.values());
    }
    
    @Override
    public List<String> getObjectMonitorTargets(String obj_id, String plugin_name) throws Exception {
        String key = obj_id + "_" + plugin_name;
        List<String> targets = targetMap.get(key);
        return targets != null ? new ArrayList<>(targets) : new ArrayList<>();
    }
    
    @Override
    public List<String> getRunningNode() throws Exception {
        return new ArrayList<>(runningNodes);
    }
    
    @Override
    public List<String> getRunningSchedules() throws Exception {
        return new ArrayList<>(runningSchedules);
    }
    
    @Override
    public List<NodeProcessBean> getAllProcess() throws Exception {
        return new ArrayList<>(processMap.values());
    }
    
    @Override
    public boolean isOutStartTimeLimit(long time) throws Exception {
        // 默认限制为24小时
        long limit = 24 * 60 * 60 * 1000L;
        return (System.currentTimeMillis() - time) > limit;
    }
    
    @Override
    public NodeProcessBean getProcessBeanByName(String processName) throws Exception {
        return processMap.get(processName);
    }
}

