package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IMonitorObjectTargetService;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectTarget;

/**
 * 监控对象目标服务实现
 * 
 * @author CDRC Runtime Team
 */
public class MonitorObjectTargetServiceImpl implements IMonitorObjectTargetService {
    
    // 内存存储：monitor_object_id -> List<MonitorObjectTarget>
    private final Map<String, List<MonitorObjectTarget>> targetStore = new ConcurrentHashMap<>();
    
    @Override
    public void insertTargets(List<MonitorObjectTarget> targets) throws Exception {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        
        for (MonitorObjectTarget target : targets) {
            String monitorObjId = target.getMonitor_object_id();
            if (monitorObjId != null) {
                targetStore.computeIfAbsent(monitorObjId, k -> new ArrayList<>()).add(target);
            }
        }
    }
    
    @Override
    public void updateTargets(List<MonitorObjectTarget> targets) throws Exception {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        
        for (MonitorObjectTarget target : targets) {
            String monitorObjId = target.getMonitor_object_id();
            if (monitorObjId != null) {
                List<MonitorObjectTarget> existing = targetStore.get(monitorObjId);
                if (existing != null) {
                    // 更新现有目标
                    for (int i = 0; i < existing.size(); i++) {
                        if (existing.get(i).getTarget_path().equals(target.getTarget_path())) {
                            existing.set(i, target);
                            break;
                        }
                    }
                } else {
                    // 插入新目标
                    insertTargets(java.util.Arrays.asList(target));
                }
            }
        }
    }
    
    @Override
    public List<MonitorObjectTarget> queryByMonitorObjId(String monitorObjId) throws Exception {
        List<MonitorObjectTarget> targets = targetStore.get(monitorObjId);
        return targets != null ? new ArrayList<>(targets) : new ArrayList<>();
    }
    
    @Override
    public List<MonitorObjectTarget> queryDiskMonitor(String monitor_object_id) throws Exception {
        List<MonitorObjectTarget> allTargets = queryByMonitorObjId(monitor_object_id);
        return allTargets.stream()
            .filter(target -> target.getTarget_path() != null && target.getTarget_path().startsWith("disk."))
            .collect(Collectors.toList());
    }
}

