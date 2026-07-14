package com.chinacreator.gzcm.runtime.core.logging.datachange;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.DatabaseAccessException;
import com.chinacreator.gzcm.runtime.core.logging.datachange.DataChangeLogEntry;
import com.chinacreator.gzcm.runtime.core.logging.datachange.DataChangeQueryCondition;
import com.chinacreator.gzcm.runtime.core.logging.datachange.IDataChangeLogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据变更日志服务实现
 * 
 * @author CDRC Runtime Team
 */
public class DataChangeLogServiceImpl implements IDataChangeLogService {
    
    private static final String TABLE_NAME = "td_data_change_log";
    private static final int BATCH_SIZE = 100;
    
    private final ISystemDatabaseAccess databaseAccess;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<DataChangeLogEntry> logQueue;
    private final ExecutorService executorService;
    private volatile boolean running = true;
    
    public DataChangeLogServiceImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
        this.objectMapper = new ObjectMapper();
        this.logQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DataChangeLog-Worker");
            t.setDaemon(true);
            return t;
        });
        
        // 启动异步写入线程
        startAsyncWriter();
    }
    
    @Override
    public void logChange(DataChangeLogEntry entry) {
        if (entry == null) {
            return;
        }
        
        // 生成日志ID
        if (entry.getLogId() == null) {
            entry.setLogId(UUID.randomUUID().toString());
        }
        
        // 设置变更时间
        if (entry.getChangeTime() == null) {
            entry.setChangeTime(new Date());
        }
        
        // 添加到队列
        try {
            logQueue.put(entry);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void batchLogChange(List<DataChangeLogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (DataChangeLogEntry entry : entries) {
            logChange(entry);
        }
    }
    
    @Override
    public List<DataChangeLogEntry> queryChangeHistory(DataChangeQueryCondition condition) {
        if (condition == null) {
            return new ArrayList<>();
        }
        
        try {
            // 构建SQL查询
            StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE 1=1");
            List<Object> params = new java.util.ArrayList<>();
            
            if (condition.getTableName() != null) {
                sql.append(" AND table_name = ?");
                params.add(condition.getTableName());
            }
            if (condition.getRecordId() != null) {
                sql.append(" AND record_id = ?");
                params.add(condition.getRecordId());
            }
            if (condition.getOperationType() != null) {
                sql.append(" AND operation_type = ?");
                params.add(condition.getOperationType());
            }
            if (condition.getUserId() != null) {
                sql.append(" AND user_id = ?");
                params.add(condition.getUserId());
            }
            if (condition.getTenantId() != null) {
                sql.append(" AND tenant_id = ?");
                params.add(condition.getTenantId());
            }
            if (condition.getAuditLogId() != null) {
                sql.append(" AND audit_log_id = ?");
                params.add(condition.getAuditLogId());
            }
            if (condition.getTraceId() != null) {
                sql.append(" AND trace_id = ?");
                params.add(condition.getTraceId());
            }
            if (condition.getStartTime() != null) {
                sql.append(" AND change_time >= ?");
                params.add(new Timestamp(condition.getStartTime().getTime()));
            }
            if (condition.getEndTime() != null) {
                sql.append(" AND change_time <= ?");
                params.add(new Timestamp(condition.getEndTime().getTime()));
            }
            
            sql.append(" ORDER BY change_time DESC");
            
            // 分页
            int offset = 0;
            int limit = 1000;
            if (condition.getPage() != null && condition.getPageSize() != null) {
                offset = (condition.getPage() - 1) * condition.getPageSize();
                limit = condition.getPageSize();
                sql.append(" LIMIT " + offset + ", " + limit);
            }
            
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql.toString(), params.toArray());
            
            // 转换为DataChangeLogEntry
            List<DataChangeLogEntry> entries = new ArrayList<>();
            for (Map<String, Object> row : results) {
                entries.add(convertToEntry(row));
            }
            
            return entries;
        } catch (DatabaseAccessException e) {
            System.err.println("查询数据变更历史失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 将查询结果转换为DataChangeLogEntry
     */
    private DataChangeLogEntry convertToEntry(Map<String, Object> row) {
        DataChangeLogEntry entry = new DataChangeLogEntry();
        entry.setLogId((String) row.get("log_id"));
        entry.setTableName((String) row.get("table_name"));
        entry.setRecordId((String) row.get("record_id"));
        entry.setOperationType((String) row.get("operation_type"));
        entry.setUserId((String) row.get("user_id"));
        entry.setTenantId((String) row.get("tenant_id"));
        entry.setAuditLogId((String) row.get("audit_log_id"));
        entry.setTraceId((String) row.get("trace_id"));
        
        if (row.get("change_time") != null) {
            entry.setChangeTime(new Date(((Timestamp) row.get("change_time")).getTime()));
        }
        
        // 反序列化beforeData和afterData
        try {
            if (row.get("before_data") != null) {
                entry.setBeforeData(objectMapper.readValue((String) row.get("before_data"), 
                    new TypeReference<Map<String, Object>>() {}));
            }
            if (row.get("after_data") != null) {
                entry.setAfterData(objectMapper.readValue((String) row.get("after_data"), 
                    new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception e) {
            // 忽略反序列化错误
        }
        
        return entry;
    }
    
    @Override
    public void linkToAuditLog(String changeLogId, String auditLogId) {
        if (changeLogId == null || auditLogId == null) {
            return;
        }
        
        try {
            String sql = "UPDATE " + TABLE_NAME + " SET audit_log_id = ? WHERE log_id = ?";
            databaseAccess.executeUpdate(sql, auditLogId, changeLogId);
        } catch (DatabaseAccessException e) {
            System.err.println("关联审计日志失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<DataChangeLogEntry> queryByAuditLogId(String auditLogId) {
        if (auditLogId == null) {
            return new ArrayList<>();
        }
        
        DataChangeQueryCondition condition = new DataChangeQueryCondition();
        condition.setAuditLogId(auditLogId);
        return queryChangeHistory(condition);
    }
    
    @Override
    public List<IDataChangeLogService.DataChangeLogEntryWithAudit> queryWithAuditInfo(DataChangeQueryCondition condition) {
        List<DataChangeLogEntry> entries = queryChangeHistory(condition);
        List<IDataChangeLogService.DataChangeLogEntryWithAudit> result = new ArrayList<>();
        
        for (DataChangeLogEntry entry : entries) {
            IDataChangeLogService.DataChangeLogEntryWithAudit entryWithAudit = 
                new IDataChangeLogService.DataChangeLogEntryWithAudit();
            
            // 复制基本字段
            entryWithAudit.setLogId(entry.getLogId());
            entryWithAudit.setTableName(entry.getTableName());
            entryWithAudit.setRecordId(entry.getRecordId());
            entryWithAudit.setOperationType(entry.getOperationType());
            entryWithAudit.setBeforeData(entry.getBeforeData());
            entryWithAudit.setAfterData(entry.getAfterData());
            entryWithAudit.setUserId(entry.getUserId());
            entryWithAudit.setTenantId(entry.getTenantId());
            entryWithAudit.setAuditLogId(entry.getAuditLogId());
            entryWithAudit.setChangeTime(entry.getChangeTime());
            entryWithAudit.setTraceId(entry.getTraceId());
            
            // 如果存在审计日志ID，尝试查询审计日志信息
            if (entry.getAuditLogId() != null) {
                try {
                    // 这里需要调用Sys-Man的审计日志服务获取审计日志信息
                    // 由于跨模块调用，这里先使用占位实现
                    // 实际实现应该通过RPC或服务接口调用Sys-Man的审计日志服务
                    loadAuditInfo(entryWithAudit, entry.getAuditLogId());
                } catch (Exception e) {
                    // 忽略审计日志查询失败
                    System.err.println("查询审计日志信息失败: " + e.getMessage());
                }
            }
            
            result.add(entryWithAudit);
        }
        
        return result;
    }
    
    /**
     * 加载审计日志信息
     * 实际实现应该调用Sys-Man的审计日志服务
     */
    private void loadAuditInfo(IDataChangeLogService.DataChangeLogEntryWithAudit entry, String auditLogId) {
        // TODO: 调用Sys-Man的审计日志服务获取审计日志信息
        // 这里使用占位实现，实际应该通过服务接口调用
        // IAuditLogService auditLogService = ...;
        // AuditLog auditLog = auditLogService.getById(auditLogId);
        // if (auditLog != null) {
        //     entry.setAuditOperationType(auditLog.getOperationType());
        //     entry.setAuditResourceType(auditLog.getResourceType());
        //     entry.setAuditResourceId(auditLog.getResourceId());
        //     entry.setAuditTime(auditLog.getAuditTime());
        // }
    }
    
    /**
     * 启动异步写入线程
     */
    private void startAsyncWriter() {
        executorService.submit(() -> {
            List<DataChangeLogEntry> batch = new ArrayList<>();
            
            while (running || !logQueue.isEmpty()) {
                try {
                    // 从队列中取出日志
                    DataChangeLogEntry entry = logQueue.poll();
                    if (entry != null) {
                        batch.add(entry);
                    }
                    
                    // 批量刷新
                    if (batch.size() >= BATCH_SIZE) {
                        flushBatch(batch);
                        batch.clear();
                    }
                    
                    // 如果队列为空且没有运行，退出
                    if (!running && logQueue.isEmpty() && batch.isEmpty()) {
                        break;
                    }
                    
                    // 避免CPU空转
                    if (entry == null && batch.isEmpty()) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 记录错误但不影响主流程
                    System.err.println("数据变更日志写入失败: " + e.getMessage());
                }
            }
            
            // 最后刷新剩余日志
            if (!batch.isEmpty()) {
                flushBatch(batch);
            }
        });
    }
    
    /**
     * 批量刷新日志到数据库
     */
    private void flushBatch(List<DataChangeLogEntry> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        for (DataChangeLogEntry entry : batch) {
            try {
                // 转换为数据库实体
                DataChangeLogEntity entity = convertToEntity(entry);
                databaseAccess.insert(TABLE_NAME, entity);
            } catch (DatabaseAccessException e) {
                // 记录失败但不影响主流程
                System.err.println("数据变更日志保存失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 转换为数据库实体
     */
    private DataChangeLogEntity convertToEntity(DataChangeLogEntry entry) {
        DataChangeLogEntity entity = new DataChangeLogEntity();
        entity.setLogId(entry.getLogId());
        entity.setTableName(entry.getTableName());
        entity.setRecordId(entry.getRecordId());
        entity.setOperationType(entry.getOperationType());
        entity.setUserId(entry.getUserId());
        entity.setTenantId(entry.getTenantId());
        entity.setAuditLogId(entry.getAuditLogId());
        entity.setChangeTime(entry.getChangeTime() != null ? 
            new Timestamp(entry.getChangeTime().getTime()) : new Timestamp(System.currentTimeMillis()));
        entity.setTraceId(entry.getTraceId());
        
        // 序列化beforeData和afterData为JSON
        try {
            if (entry.getBeforeData() != null) {
                entity.setBeforeData(objectMapper.writeValueAsString(entry.getBeforeData()));
            }
            if (entry.getAfterData() != null) {
                entity.setAfterData(objectMapper.writeValueAsString(entry.getAfterData()));
            }
        } catch (Exception e) {
            // 忽略序列化错误
        }
        
        return entity;
    }
    
    /**
     * 关闭服务
     */
    public void shutdown() {
        running = false;
        executorService.shutdown();
    }
    
    /**
     * 数据变更日志实体（对应数据库表结构）
     */
    public static class DataChangeLogEntity {
        private String logId;
        private String tableName;
        private String recordId;
        private String operationType;
        private String beforeData; // JSON格式
        private String afterData; // JSON格式
        private String userId;
        private String tenantId;
        private String auditLogId;
        private Timestamp changeTime;
        private String traceId;
        
        // Getters and Setters
        public String getLogId() {
            return logId;
        }
        
        public void setLogId(String logId) {
            this.logId = logId;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        
        public String getRecordId() {
            return recordId;
        }
        
        public void setRecordId(String recordId) {
            this.recordId = recordId;
        }
        
        public String getOperationType() {
            return operationType;
        }
        
        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }
        
        public String getBeforeData() {
            return beforeData;
        }
        
        public void setBeforeData(String beforeData) {
            this.beforeData = beforeData;
        }
        
        public String getAfterData() {
            return afterData;
        }
        
        public void setAfterData(String afterData) {
            this.afterData = afterData;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getTenantId() {
            return tenantId;
        }
        
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
        
        public String getAuditLogId() {
            return auditLogId;
        }
        
        public void setAuditLogId(String auditLogId) {
            this.auditLogId = auditLogId;
        }
        
        public Timestamp getChangeTime() {
            return changeTime;
        }
        
        public void setChangeTime(Timestamp changeTime) {
            this.changeTime = changeTime;
        }
        
        public String getTraceId() {
            return traceId;
        }
        
        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
}

