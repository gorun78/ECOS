package com.chinacreator.gzcm.runtime.core.logging.plugin;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.LogEntry;

/**
 * 日志插件接口
 * 允许各模块自定义日志处理逻辑
 * 
 * @author CDRC Runtime Team
 */
public interface ILoggingPlugin {
    
    /**
     * 获取插件名称
     */
    String getName();
    
    /**
     * 获取支持的模块标识
     * 返回null表示支持所有模块
     */
    String[] getSupportedModules();
    
    /**
     * 在日志保存前处理
     * 可以修改日志条目或返回false阻止保存
     * 
     * @param entry 日志条目
     * @param context 上下文信息
     * @return true表示继续处理，false表示阻止保存
     */
    boolean beforeSave(LogEntry entry, Map<String, Object> context);
    
    /**
     * 在日志保存后处理
     * 
     * @param entry 日志条目
     * @param context 上下文信息
     */
    void afterSave(LogEntry entry, Map<String, Object> context);
    
    /**
     * 处理日志查询
     * 可以修改查询条件或结果
     * 
     * @param condition 查询条件
     * @param results 查询结果
     */
    void onQuery(Map<String, Object> condition, java.util.List<LogEntry> results);
}

