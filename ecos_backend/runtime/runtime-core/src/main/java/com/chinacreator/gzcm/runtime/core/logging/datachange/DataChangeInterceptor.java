package com.chinacreator.gzcm.runtime.core.logging.datachange;

import java.util.HashMap;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.logging.datachange.AuditLogContext;

/**
 * 数据变更拦截器
 * 用于自动记录数据变更日志
 * 可以通过AOP或数据库触发器实现
 * 
 * @author CDRC Runtime Team
 */
public class DataChangeInterceptor {
    
    private final IDataChangeLogService dataChangeLogService;
    private String currentUserId;
    private String currentTenantId;
    private String currentTraceId;
    private String currentAuditLogId;
    
    public DataChangeInterceptor(IDataChangeLogService dataChangeLogService) {
        this.dataChangeLogService = dataChangeLogService;
    }
    
    /**
     * 设置当前上下文
     */
    public void setContext(String userId, String tenantId, String traceId, String auditLogId) {
        this.currentUserId = userId;
        this.currentTenantId = tenantId;
        this.currentTraceId = traceId;
        this.currentAuditLogId = auditLogId;
    }
    
    /**
     * 拦截数据插入操作
     */
    public void interceptInsert(String tableName, String recordId, Map<String, Object> data) {
        DataChangeLogEntry entry = new DataChangeLogEntry();
        entry.setTableName(tableName);
        entry.setRecordId(recordId);
        entry.setOperationType("CREATE");
        entry.setAfterData(data);
        
        // 从上下文或当前设置获取信息
        applyContextToEntry(entry);
        
        dataChangeLogService.logChange(entry);
    }
    
    /**
     * 拦截数据更新操作
     */
    public void interceptUpdate(String tableName, String recordId, 
                               Map<String, Object> beforeData, Map<String, Object> afterData) {
        DataChangeLogEntry entry = new DataChangeLogEntry();
        entry.setTableName(tableName);
        entry.setRecordId(recordId);
        entry.setOperationType("UPDATE");
        entry.setBeforeData(beforeData);
        entry.setAfterData(afterData);
        
        // 从上下文或当前设置获取信息
        applyContextToEntry(entry);
        
        dataChangeLogService.logChange(entry);
    }
    
    /**
     * 拦截数据删除操作
     */
    public void interceptDelete(String tableName, String recordId, Map<String, Object> data) {
        DataChangeLogEntry entry = new DataChangeLogEntry();
        entry.setTableName(tableName);
        entry.setRecordId(recordId);
        entry.setOperationType("DELETE");
        entry.setBeforeData(data);
        
        // 从上下文或当前设置获取信息
        applyContextToEntry(entry);
        
        dataChangeLogService.logChange(entry);
    }
    
    /**
     * 应用上下文信息到日志条目
     * 优先使用ThreadLocal中的审计日志上下文，其次使用当前设置的上下文
     */
    private void applyContextToEntry(DataChangeLogEntry entry) {
        // 优先从ThreadLocal获取审计日志上下文
        AuditLogContext auditContext = AuditLogContext.getCurrent();
        if (auditContext != null) {
            entry.setAuditLogId(auditContext.getAuditLogId());
            entry.setUserId(auditContext.getUserId() != null ? auditContext.getUserId() : currentUserId);
            entry.setTenantId(auditContext.getTenantId() != null ? auditContext.getTenantId() : currentTenantId);
            entry.setTraceId(auditContext.getTraceId() != null ? auditContext.getTraceId() : currentTraceId);
        } else {
            // 使用当前设置的上下文
            entry.setUserId(currentUserId);
            entry.setTenantId(currentTenantId);
            entry.setTraceId(currentTraceId);
            entry.setAuditLogId(currentAuditLogId);
        }
    }
    
    /**
     * 从实体对象提取数据
     */
    public Map<String, Object> extractData(Object entity) {
        Map<String, Object> data = new HashMap<>();
        if (entity == null) {
            return data;
        }
        
        // 使用反射提取字段值
        java.lang.reflect.Field[] fields = entity.getClass().getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(entity);
                if (value != null) {
                    data.put(field.getName(), value);
                }
            } catch (IllegalAccessException e) {
                // 忽略无法访问的字段
            }
        }
        
        return data;
    }
}

