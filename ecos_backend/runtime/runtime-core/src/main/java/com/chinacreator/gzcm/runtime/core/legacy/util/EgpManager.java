package com.chinacreator.gzcm.runtime.core.legacy.util;

/**
 * EgpManager - 企业级平台管理器占位类
 * 用于兼容旧代码中的 EgpManager 功能
 * 
 * 注意：此实现为占位实现，实际应使用 Runtime 的配置管理服务
 */
public class EgpManager {
    
    /**
     * 获取应用数据库名称
     * @return 数据库名称
     */
    public static String getAppDBName() {
        // Placeholder implementation
        // TODO: 实现实际的数据库名称获取逻辑
        return "default";
    }
}
