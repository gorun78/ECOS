package com.chinacreator.gzcm.sysman.audit.service.impl;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.chinacreator.gzcm.sysman.audit.dao.AuditLogDao;
import com.chinacreator.gzcm.sysman.audit.entity.AuditLog;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务实现
 */
@Service
public class AuditLogServiceImpl implements IAuditLogService {

    private final AuditLogDao auditLogDao;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public AuditLogServiceImpl(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    @Override
    public void log(AuditEvent event) {
        // 异步记录日志
        CompletableFuture.runAsync(() -> {
            try {
                AuditLog auditLog = convertToEntity(event);
                auditLogDao.insert(auditLog);
            } catch (Exception e) {
                // 记录失败，但不影响主流程
                org.slf4j.LoggerFactory.getLogger(AuditLogServiceImpl.class)
                    .error("审计日志记录失败: eventId={}, eventType={}", 
                        event.getEventId(), event.getEventType(), e);
            }
        }, executorService);
    }

    @Override
    public List<AuditEvent> query(AuditQueryCondition condition) {
        try {
            AuditLog queryCondition = convertToQueryCondition(condition);
            int offset = (condition.getPage() != null && condition.getPage() > 0) 
                    ? (condition.getPage() - 1) * (condition.getPageSize() != null ? condition.getPageSize() : 20)
                    : 0;
            int limit = condition.getPageSize() != null ? condition.getPageSize() : 20;
            
            List<AuditLog> logs = auditLogDao.query(queryCondition, offset, limit);
            return logs.stream().map(this::convertToEvent).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("查询审计日志失败: " + e.getMessage(), e);
        }
    }

    @Override
    public AuditEvent getById(String logId) {
        try {
            AuditLog log = auditLogDao.findById(logId);
            return log != null ? convertToEvent(log) : null;
        } catch (Exception e) {
            throw new RuntimeException("获取审计日志失败: " + e.getMessage(), e);
        }
    }

    private AuditLog convertToEntity(AuditEvent event) throws Exception {
        AuditLog log = new AuditLog();
        log.setLogId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString());
        log.setEventType(event.getEventType());
        log.setTimestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now());
        log.setUserId(event.getUserId());
        log.setTenantId(event.getTenantId());
        log.setResource(event.getResource());
        log.setAction(event.getAction());
        log.setResult(event.getResult());
        log.setIpAddress(event.getIpAddress());
        log.setUserAgent(event.getUserAgent());
        log.setRequestId(event.getRequestId());
        log.setDuration(event.getDuration());
        if (event.getDetails() != null) {
            log.setDetails(objectMapper.writeValueAsString(event.getDetails()));
        }
        return log;
    }

    private AuditEvent convertToEvent(AuditLog log) {
        AuditEvent event = new AuditEvent();
        event.setEventId(log.getLogId());
        event.setEventType(log.getEventType());
        event.setTimestamp(log.getTimestamp());
        event.setUserId(log.getUserId());
        event.setTenantId(log.getTenantId());
        event.setResource(log.getResource());
        event.setAction(log.getAction());
        event.setResult(log.getResult());
        event.setIpAddress(log.getIpAddress());
        event.setUserAgent(log.getUserAgent());
        event.setRequestId(log.getRequestId());
        event.setDuration(log.getDuration());
        if (log.getDetails() != null) {
            try {
                event.setDetails(objectMapper.readValue(log.getDetails(), 
                        objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class)));
            } catch (Exception e) {
                // 忽略JSON解析失败
            }
        }
        return event;
    }

    private AuditLog convertToQueryCondition(AuditQueryCondition condition) {
        AuditLog log = new AuditLog();
        log.setUserId(condition.getUserId());
        log.setTenantId(condition.getTenantId());
        log.setResource(condition.getResource());
        log.setEventType(condition.getEventType());
        log.setResult(condition.getResult());
        // startTime和endTime需要在SQL中处理
        return log;
    }
    
    @Override
    public AuditStatistics statistics(AuditQueryCondition condition) {
        try {
            AuditStatistics stats = new AuditStatistics();
            
            // 总记录数
            AuditLog queryCondition = convertToQueryCondition(condition);
            Long totalCount = auditLogDao.count(queryCondition, condition.getStartTime(), condition.getEndTime());
            stats.setTotalCount(totalCount);
            
            // 成功/失败统计
            AuditLog successCondition = convertToQueryCondition(condition);
            successCondition.setResult("SUCCESS");
            Long successCount = auditLogDao.count(successCondition, condition.getStartTime(), condition.getEndTime());
            stats.setSuccessCount(successCount);
            
            AuditLog failureCondition = convertToQueryCondition(condition);
            failureCondition.setResult("FAILURE");
            Long failureCount = auditLogDao.count(failureCondition, condition.getStartTime(), condition.getEndTime());
            stats.setFailureCount(failureCount);
            
            // 按事件类型统计
            Map<String, Long> eventTypeCount = auditLogDao.countByEventType(queryCondition, condition.getStartTime(), condition.getEndTime());
            stats.setEventTypeCount(eventTypeCount);
            
            // 按用户统计
            Map<String, Long> userCount = auditLogDao.countByUser(queryCondition, condition.getStartTime(), condition.getEndTime());
            stats.setUserCount(userCount);
            
            // 按资源统计
            Map<String, Long> resourceCount = auditLogDao.countByResource(queryCondition, condition.getStartTime(), condition.getEndTime());
            stats.setResourceCount(resourceCount);
            
            // 按日期统计
            Map<String, Long> dailyCount = auditLogDao.countByDate(queryCondition, condition.getStartTime(), condition.getEndTime());
            stats.setDailyCount(dailyCount);
            
            return stats;
        } catch (Exception e) {
            throw new RuntimeException("统计审计日志失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] export(AuditQueryCondition condition, String format) {
        try {
            // 查询所有符合条件的日志（不分页）
            AuditLog queryCondition = convertToQueryCondition(condition);
            List<AuditLog> logs = auditLogDao.query(queryCondition, condition.getStartTime(), condition.getEndTime(), 0, Integer.MAX_VALUE);
            List<AuditEvent> events = logs.stream().map(this::convertToEvent).collect(Collectors.toList());
            
            // 根据格式导出
            if ("CSV".equalsIgnoreCase(format)) {
                return exportToCsv(events);
            } else if ("JSON".equalsIgnoreCase(format)) {
                return exportToJson(events);
            } else {
                throw new IllegalArgumentException("不支持的导出格式: " + format);
            }
        } catch (Exception e) {
            throw new RuntimeException("导出审计日志失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 导出为CSV格式
     */
    private byte[] exportToCsv(List<AuditEvent> events) {
        StringBuilder csv = new StringBuilder();
        // CSV头部
        csv.append("事件ID,事件类型,时间,用户ID,租户ID,资源,操作,结果,IP地址,用户代理,请求ID,耗时(ms)\n");
        
        // CSV数据行
        for (AuditEvent event : events) {
            csv.append(escapeCsvField(event.getEventId())).append(",");
            csv.append(escapeCsvField(event.getEventType())).append(",");
            csv.append(event.getTimestamp() != null ? event.getTimestamp().toString() : "").append(",");
            csv.append(escapeCsvField(event.getUserId())).append(",");
            csv.append(escapeCsvField(event.getTenantId())).append(",");
            csv.append(escapeCsvField(event.getResource())).append(",");
            csv.append(escapeCsvField(event.getAction())).append(",");
            csv.append(escapeCsvField(event.getResult())).append(",");
            csv.append(escapeCsvField(event.getIpAddress())).append(",");
            csv.append(escapeCsvField(event.getUserAgent())).append(",");
            csv.append(escapeCsvField(event.getRequestId())).append(",");
            csv.append(event.getDuration() != null ? event.getDuration() : "").append("\n");
        }
        
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * 导出为JSON格式
     */
    private byte[] exportToJson(List<AuditEvent> events) {
        try {
            return objectMapper.writeValueAsBytes(events);
        } catch (Exception e) {
            throw new RuntimeException("JSON导出失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * CSV字段转义
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // 如果包含逗号、引号或换行符，需要用引号包裹并转义引号
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}

