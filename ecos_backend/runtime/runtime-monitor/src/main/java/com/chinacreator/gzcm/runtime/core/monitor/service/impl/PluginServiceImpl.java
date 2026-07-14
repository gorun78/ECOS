package com.chinacreator.gzcm.runtime.core.monitor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chinacreator.gzcm.runtime.core.monitor.interfaces.IPluginService;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginBean;
import com.chinacreator.gzcm.runtime.core.monitor.plugin.bean.PluginTargetBean;

/**
 * 监控插件服务实现
 * 
 * @author CDRC Runtime Team
 */
public class PluginServiceImpl implements IPluginService {
    
    // 内存存储：dbname -> pluginName -> PluginBean
    private final Map<String, Map<String, PluginBean>> pluginStore = new ConcurrentHashMap<>();
    
    // 内存存储：pluginName -> type -> List<PluginTargetBean>
    private final Map<String, Map<Integer, List<PluginTargetBean>>> targetStore = new ConcurrentHashMap<>();
    
    @Override
    public void regPlugins(List<PluginBean> listplugins, String dbname) throws Exception {
        if (listplugins == null || listplugins.isEmpty()) {
            return;
        }
        
        Map<String, PluginBean> plugins = pluginStore.computeIfAbsent(dbname, k -> new ConcurrentHashMap<>());
        for (PluginBean plugin : listplugins) {
            if (plugin.getPlugin_name() != null) {
                plugins.put(plugin.getPlugin_name(), plugin);
            }
        }
    }
    
    @Override
    public void updatePlugins(List<PluginBean> listplugins, String dbname) throws Exception {
        regPlugins(listplugins, dbname);
    }
    
    @Override
    public List<PluginBean> getAllPlugins(String dbname) throws Exception {
        Map<String, PluginBean> plugins = pluginStore.get(dbname);
        return plugins != null ? new ArrayList<>(plugins.values()) : new ArrayList<>();
    }
    
    @Override
    public PluginBean getPluginBeanWithName(String pluginName) throws Exception {
        for (Map<String, PluginBean> plugins : pluginStore.values()) {
            PluginBean bean = plugins.get(pluginName);
            if (bean != null) {
                return bean;
            }
        }
        return null;
    }
    
    @Override
    public List<PluginTargetBean> getPluginTargetsWithPluginName(String pluginName, int type) throws Exception {
        Map<Integer, List<PluginTargetBean>> typeMap = targetStore.get(pluginName);
        if (typeMap != null) {
            List<PluginTargetBean> targets = typeMap.get(type);
            return targets != null ? new ArrayList<>(targets) : new ArrayList<>();
        }
        return new ArrayList<>();
    }
    
    @Override
    public void savePluginAll(PluginBean bean, String dbname) throws Exception {
        if (bean != null && bean.getPlugin_name() != null) {
            Map<String, PluginBean> plugins = pluginStore.computeIfAbsent(dbname, k -> new ConcurrentHashMap<>());
            plugins.put(bean.getPlugin_name(), bean);
        }
    }
}

