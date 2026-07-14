package com.chinacreator.gzcm.runtime.core.logging.plugin;

import java.util.Map;

import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.LogEntry;

/**
 * 模块日志处理器接口
 * 用于处理特定模块的日志记录
 * 
 * @author CDRC Runtime Team
 */
public interface IModuleLogHandler {
    
    /**
     * 获取模块标识
     */
    String getModuleId();
    
    /**
     * 处理日志记录
     * 
     * @param entry 日志条目
     * @param context 上下文信息
     * @param loggingService 日志服务（用于记录日志）
     */
    void handleLog(LogEntry entry, Map<String, Object> context, ILoggingService loggingService);
    
    /**
     * 获取模块特定的上下文信息
     * 这些信息会被自动添加到日志条目的上下文中
     * 
     * @return 上下文信息
     */
    Map<String, Object> getModuleContext();
}

