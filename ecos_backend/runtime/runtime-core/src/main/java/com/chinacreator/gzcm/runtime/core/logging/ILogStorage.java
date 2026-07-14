package com.chinacreator.gzcm.runtime.core.logging;

import java.util.Date;
import java.util.List;

/**
 * 日志存储接口
 * 定义日志的存储、查询、归档和删除操作
 * 
 * @author CDRC Runtime Team
 */
public interface ILogStorage {
    
    /**
     * 保存日志条目
     * 
     * @param entry 日志条目
     */
    void save(LogEntry entry);
    
    /**
     * 批量保存日志条目
     * 
     * @param entries 日志条目列表
     */
    void batchSave(List<LogEntry> entries);
    
    /**
     * 查询日志
     * 
     * @param condition 查询条件
     * @return 日志列表
     */
    List<LogEntry> query(LogQueryCondition condition);
    
    /**
     * 归档日志
     * 
     * @param beforeDate 归档此日期之前的数据
     * @param targetPath 归档目标路径
     */
    void archive(Date beforeDate, String targetPath);
    
    /**
     * 删除日志
     * 
     * @param beforeDate 删除此日期之前的数据
     */
    void delete(Date beforeDate);
}

