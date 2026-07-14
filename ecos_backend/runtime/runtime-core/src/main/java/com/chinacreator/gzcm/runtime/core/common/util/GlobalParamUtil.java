package com.chinacreator.gzcm.runtime.core.common.util;

import com.chinacreator.gzcm.runtime.core.common.DxConstans;

/**
 * 全局参数工具类
 * 用于获取系统全局参数值
 * 
 * 注意：这是一个占位实现，实际应该从系统配置服务中获取参数值
 * TODO: 需要集成Sys-Man的配置服务来获取真实的参数值
 */
public class GlobalParamUtil {
    
    /**
     * 获取全局参数值
     * @param paramName 参数名称
     * @param defaultValue 默认值
     * @return 参数值，如果不存在则返回默认值
     */
    public static String getProperty(String paramName, String defaultValue) {
        // TODO: 从系统配置服务中获取参数值
        // 临时实现：返回默认值
        return defaultValue;
    }
    
    /**
     * 获取布尔类型的全局参数值
     * @param paramName 参数名称
     * @return 参数值，如果不存在或为false则返回false
     */
    public static boolean getBooleanProperty(String paramName) {
        String value = getProperty(paramName, DxConstans.DB_FLASE_STRING);
        return DxConstans.DB_TRUE_STRING.equals(value) || "true".equalsIgnoreCase(value);
    }
    
    /**
     * 获取整数类型的全局参数值
     * @param paramName 参数名称
     * @param defaultValue 默认值
     * @return 参数值，如果不存在或解析失败则返回默认值
     */
    public static int getIntProperty(String paramName, int defaultValue) {
        String value = getProperty(paramName, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
