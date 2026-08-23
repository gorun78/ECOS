package com.chinacreator.gzcm.runtime.core.logging.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 日志插件注册表
 * 管理所有注册的日志插件和模块处理器
 * 
 * @author CDRC Runtime Team
 */
public class LoggingPluginRegistry {
    
    private static final LoggingPluginRegistry instance = new LoggingPluginRegistry();
    
    private final Map<String, IModuleLogHandler> moduleHandlers = new ConcurrentHashMap<>();
    private final List<ILoggingPlugin> plugins = new ArrayList<>();
    private final Map<String, List<ILoggingPlugin>> modulePlugins = new ConcurrentHashMap<>();
    
    private LoggingPluginRegistry() {
        // Singleton
    }
    
    public static LoggingPluginRegistry getInstance() {
        return instance;
    }
    
    /**
     * 注册模块日志处理器
     */
    public void registerModuleHandler(IModuleLogHandler handler) {
        if (handler != null && handler.getModuleId() != null) {
            moduleHandlers.put(handler.getModuleId(), handler);
        }
    }
    
    /**
     * 注销模块日志处理器
     */
    public void unregisterModuleHandler(String moduleId) {
        moduleHandlers.remove(moduleId);
    }
    
    /**
     * 获取模块日志处理器
     */
    public IModuleLogHandler getModuleHandler(String moduleId) {
        return moduleHandlers.get(moduleId);
    }
    
    /**
     * 注册日志插件
     */
    public void registerPlugin(ILoggingPlugin plugin) {
        if (plugin != null && !plugins.contains(plugin)) {
            plugins.add(plugin);
            
            // 按模块索引插件
            String[] supportedModules = plugin.getSupportedModules();
            if (supportedModules != null) {
                for (String module : supportedModules) {
                    modulePlugins.computeIfAbsent(module, k -> new ArrayList<>()).add(plugin);
                }
            } else {
                // 支持所有模块的插件
                modulePlugins.computeIfAbsent("*", k -> new ArrayList<>()).add(plugin);
            }
        }
    }
    
    /**
     * 注销日志插件
     */
    public void unregisterPlugin(ILoggingPlugin plugin) {
        if (plugin != null) {
            plugins.remove(plugin);
            
            // 从模块索引中移除
            for (List<ILoggingPlugin> pluginList : modulePlugins.values()) {
                pluginList.remove(plugin);
            }
        }
    }
    
    /**
     * 获取指定模块的插件列表
     */
    public List<ILoggingPlugin> getPluginsForModule(String moduleId) {
        List<ILoggingPlugin> result = new ArrayList<>();
        
        // 添加模块特定的插件
        List<ILoggingPlugin> moduleSpecific = modulePlugins.get(moduleId);
        if (moduleSpecific != null) {
            result.addAll(moduleSpecific);
        }
        
        // 添加通用插件（支持所有模块）
        List<ILoggingPlugin> universal = modulePlugins.get("*");
        if (universal != null) {
            result.addAll(universal);
        }
        
        return result;
    }
    
    /**
     * 获取所有注册的插件
     */
    public List<ILoggingPlugin> getAllPlugins() {
        return new ArrayList<>(plugins);
    }
    
    /**
     * 清除所有注册
     */
    public void clear() {
        moduleHandlers.clear();
        plugins.clear();
        modulePlugins.clear();
    }
}

