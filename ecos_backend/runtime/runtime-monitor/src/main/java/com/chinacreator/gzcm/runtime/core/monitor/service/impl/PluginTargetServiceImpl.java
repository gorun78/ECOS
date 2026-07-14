package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IPluginTargetService;
import com.chinacreator.gzcm.runtime.core.monitor.bean.MonitorObjectBean;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

/**
 * 监控插件目标服务实现
 * 
 * @author CDRC Runtime Team
 */
public class PluginTargetServiceImpl implements IPluginTargetService {
    
    // 内存存储：plugin -> List<PluginTargetBean>
    private final Map<String, List<PluginTargetBean>> targetStore = new ConcurrentHashMap<>();
    
    @Override
    public List<PluginTargetBean> findFirstLevelPluginTargets(PluginTargetBean bean) throws Exception {
        if (bean == null || bean.getPlugin_name() == null) {
            return new ArrayList<>();
        }
        
        List<PluginTargetBean> all = targetStore.get(bean.getPlugin_name());
        if (all == null) {
            return new ArrayList<>();
        }
        
        // 返回第一级目标（target_path不包含"/"或只有一个"/"）
        return all.stream()
            .filter(target -> {
                String path = target.getTarget_path();
                if (path == null) {
                    return false;
                }
                int slashCount = path.length() - path.replace("/", "").length();
                return slashCount <= 1;
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PluginTargetBean> findPluginTargetsByCondition(PluginTargetBean condition) throws Exception {
        if (condition == null) {
            return new ArrayList<>();
        }
        
        List<PluginTargetBean> all = new ArrayList<>();
        if (condition.getPlugin_name() != null) {
            List<PluginTargetBean> pluginTargets = targetStore.get(condition.getPlugin_name());
            if (pluginTargets != null) {
                all.addAll(pluginTargets);
            }
        } else {
            for (List<PluginTargetBean> targets : targetStore.values()) {
                all.addAll(targets);
            }
        }
        
        // 过滤
        return all.stream()
            .filter(target -> {
                if (condition.getTarget_path() != null && 
                    !target.getTarget_path().contains(condition.getTarget_path())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getTargetDataMap(String plugin, MonitorObjectBean obj, String targetname) throws Exception {
        // 占位实现：返回空列表
        return new ArrayList<>();
    }
}

