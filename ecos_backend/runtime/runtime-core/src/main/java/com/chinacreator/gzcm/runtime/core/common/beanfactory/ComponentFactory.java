package com.chinacreator.gzcm.runtime.core.common.beanfactory;

import com.chinacreator.gzcm.runtime.core.core.util.Component;

/**
 * ComponentFactory - 组件工厂（beanfactory包下的别名）
 * 委托给 runtime.core.util.ComponentFactory
 */
public class ComponentFactory {
    
    /**
     * 获取服务Bean
     * @param component 组件类型
     * @param serviceName Bean名称
     * @return 服务Bean实例
     */
    public static Object getService(Component component, String serviceName) {
        return com.chinacreator.gzcm.runtime.core.core.util.ComponentFactory.getService(component, serviceName);
    }
    
    /**
     * 注册服务Bean
     * @param component 组件类型
     * @param serviceName Bean名称
     * @param service 服务实例
     */
    public static void registerService(Component component, String serviceName, Object service) {
        com.chinacreator.gzcm.runtime.core.core.util.ComponentFactory.registerService(component, serviceName, service);
    }
    
    /**
     * 清除所有注册的服务
     */
    public static void clear() {
        com.chinacreator.gzcm.runtime.core.core.util.ComponentFactory.clear();
    }
}
