package com.chinacreator.gzcm.runtime.core.logging;

import java.util.Date;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.logging.archive.ArchivePolicy;
import com.chinacreator.gzcm.runtime.core.logging.archive.ArchiveRecord;
import com.chinacreator.gzcm.runtime.core.logging.archive.ArchiveResult;

/**
 * 日志归档服务接口
 */
public interface ILogArchiveService {
    
    /**
     * 配置归档策略
     * @param logType 日志类型
     * @param policy 策略
     */
    void configureArchivePolicy(String logType, ArchivePolicy policy);
    
    /**
     * 执行归档
     * @param logType 日志类型
     * @param beforeDate 归档此时间之前的日志
     * @return 归档结果
     */
    ArchiveResult archive(String logType, Date beforeDate);
    
    /**
     * 获取归档记录
     * @param logType 日志类型
     * @return 归档记录列表
     */
    List<ArchiveRecord> listArchives(String logType);
    
    /**
     * 恢复归档
     * @param archiveId 归档ID
     * @param targetPath 目标路径（可选，如果为null则恢复到数据库）
     */
    void restore(String archiveId, String targetPath);
}
