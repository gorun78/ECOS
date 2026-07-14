package com.chinacreator.gzcm.runtime.core.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ComponentFactory - 组件工厂
 * 用于获取不同组件中的服务Bean
 * 这是一个临时适配器，用于兼容旧代码，建议逐步迁移到Spring依赖注入
 * 
 * 注意：这是一个简单的服务定位器实现，实际的服务获取应该通过Spring依赖注入完成
 * 
 * @author CDRC Runtime Team
 */
public class ComponentFactory {
    
    private static final Map<String, Object> serviceRegistry = new ConcurrentHashMap<>();
    
    /**
     * 注册服务Bean
     * 
     * @param component 组件类型
     * @param serviceName Bean名称
     * @param service 服务实例
     */
    public static void registerService(Component component, String serviceName, Object service) {
        String key = component.getName() + "." + serviceName;
        serviceRegistry.put(key, service);
        // 也注册不带组件前缀的版本
        serviceRegistry.put(serviceName, service);
    }
    
    /**
     * 获取服务Bean
     * 
     * @param component 组件类型
     * @param serviceName Bean名称
     * @return 服务Bean实例
     */
    public static Object getService(Component component, String serviceName) {
        // 尝试通过组件前缀获取
        String key = component.getName() + "." + serviceName;
        Object service = serviceRegistry.get(key);
        if (service != null) {
            return service;
        }
        
        // 尝试直接通过名称获取
        service = serviceRegistry.get(serviceName);
        if (service != null) {
            return service;
        }
        
        // 尝试通过组件前缀的驼峰命名获取
        String prefixedName = component.getName() + serviceName.substring(0, 1).toUpperCase() + serviceName.substring(1);
        service = serviceRegistry.get(prefixedName);
        if (service != null) {
            return service;
        }
        
        throw new IllegalStateException("未找到服务Bean: " + serviceName + " (组件: " + component.getName() + ")。请确保服务已通过ComponentFactory.registerService()注册");
    }
    
    /**
     * 清除所有注册的服务
     */
    public static void clear() {
        serviceRegistry.clear();
    }
}
