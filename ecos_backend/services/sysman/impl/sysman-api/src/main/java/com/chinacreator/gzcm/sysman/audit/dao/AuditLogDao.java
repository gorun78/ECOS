package com.chinacreator.gzcm.sysman.audit.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.audit.entity.AuditLog;

/**
 * 审计日志DAO接口
 */
public interface AuditLogDao {
    void insert(AuditLog auditLog) throws Exception;
    AuditLog findById(String logId) throws Exception;
    List<AuditLog> query(AuditLog condition, int offset, int limit) throws Exception;
    
    /**
     * 带时间范围的查询
     */
    List<AuditLog> query(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime, int offset, int limit) throws Exception;
    
    long count(AuditLog condition) throws Exception;
    
    /**
     * 带时间范围的计数
     */
    Long count(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception;
    
    /**
     * 按事件类型统计
     */
    Map<String, Long> countByEventType(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception;
    
    /**
     * 按用户统计
     */
    Map<String, Long> countByUser(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception;
    
    /**
     * 按资源统计
     */
    Map<String, Long> countByResource(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception;
    
    /**
     * 按日期统计
     */
    Map<String, Long> countByDate(AuditLog condition, LocalDateTime startTime, LocalDateTime endTime) throws Exception;
}
