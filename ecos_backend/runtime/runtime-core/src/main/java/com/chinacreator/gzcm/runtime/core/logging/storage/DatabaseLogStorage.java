package com.chinacreator.gzcm.runtime.core.logging.storage;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.DatabaseAccessException;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.ILogStorage;
import com.chinacreator.gzcm.runtime.core.logging.LogEntry;
import com.chinacreator.gzcm.runtime.core.logging.LogQueryCondition;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据库日志存储实现
 * 使用ISystemDatabaseAccess进行数据库操作
 * 支持异步批量写入，提高性能
 * 
 * @author CDRC Runtime Team
 */
public class DatabaseLogStorage implements ILogStorage {
    
    private static final String TABLE_NAME = "td_runtime_log";
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 5000; // 5秒刷新一次
    
    private final ISystemDatabaseAccess databaseAccess;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<LogEntry> logQueue;
    private final ExecutorService executorService;
    private volatile boolean running = true;
    
    public DatabaseLogStorage(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
        this.objectMapper = new ObjectMapper();
        this.logQueue = new LinkedBlockingQueue<>();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LogStorage-Worker");
            t.setDaemon(true);
            return t;
        });
        
        // 启动异步写入线程
        startAsyncWriter();
    }
    
    @Override
    public void save(LogEntry entry) {
        if (entry == null) {
            return;
        }
        
        // 生成日志ID
        if (entry.getLogId() == null) {
            entry.setLogId(UUID.randomUUID().toString());
        }
        
        // 设置线程名
        if (entry.getThread() == null) {
            entry.setThread(Thread.currentThread().getName());
        }
        
        // 添加到队列
        try {
            logQueue.put(entry);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void batchSave(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (LogEntry entry : entries) {
            save(entry);
        }
    }
    
    @Override
    public List<LogEntry> query(LogQueryCondition condition) {
        if (condition == null) {
            return new ArrayList<>();
        }
        
        try {
            // 构建查询条件
            Map<String, Object> queryCondition = new java.util.HashMap<>();
            
            if (condition.getModule() != null) {
                queryCondition.put("module", condition.getModule());
            }
            if (condition.getLevel() != null) {
                queryCondition.put("level", condition.getLevel().name());
            }
            if (condition.getLogger() != null) {
                queryCondition.put("logger", condition.getLogger());
            }
            if (condition.getRequestId() != null) {
                queryCondition.put("request_id", condition.getRequestId());
            }
            if (condition.getTraceId() != null) {
                queryCondition.put("trace_id", condition.getTraceId());
            }
            if (condition.getUserId() != null) {
                queryCondition.put("user_id", condition.getUserId());
            }
            if (condition.getTenantId() != null) {
                queryCondition.put("tenant_id", condition.getTenantId());
            }
            
            // 时间范围条件需要通过SQL实现
            List<RuntimeLogEntity> entities;
            if (condition.getStartTime() != null || condition.getEndTime() != null) {
                // 使用SQL查询支持时间范围
                StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE 1=1");
                List<Object> params = new java.util.ArrayList<>();
                
                if (condition.getModule() != null) {
                    sql.append(" AND module = ?");
                    params.add(condition.getModule());
                }
                if (condition.getLevel() != null) {
                    sql.append(" AND level = ?");
                    params.add(condition.getLevel().name());
                }
                if (condition.getStartTime() != null) {
                    sql.append(" AND timestamp >= ?");
                    params.add(new Timestamp(condition.getStartTime().getTime()));
                }
                if (condition.getEndTime() != null) {
                    sql.append(" AND timestamp <= ?");
                    params.add(new Timestamp(condition.getEndTime().getTime()));
                }
                if (condition.getKeyword() != null) {
                    sql.append(" AND message LIKE ?");
                    params.add("%" + condition.getKeyword() + "%");
                }
                
                sql.append(" ORDER BY timestamp DESC");
                
                // 分页
                int offset = 0;
                int limit = 1000;
                if (condition.getPage() != null && condition.getPageSize() != null) {
                    offset = (condition.getPage() - 1) * condition.getPageSize();
                    limit = condition.getPageSize();
                    sql.append(" LIMIT " + offset + ", " + limit);
                }
                
                List<Map<String, Object>> results = databaseAccess.executeQuery(sql.toString(), params.toArray());
                entities = convertToEntities(results);
            } else {
                // 使用条件查询
                int offset = 0;
                int limit = 1000;
                if (condition.getPage() != null && condition.getPageSize() != null) {
                    offset = (condition.getPage() - 1) * condition.getPageSize();
                    limit = condition.getPageSize();
                }
                entities = databaseAccess.query(TABLE_NAME, RuntimeLogEntity.class, queryCondition, offset, limit);
            }
            
            // 转换为LogEntry
            List<LogEntry> entries = new ArrayList<>();
            for (RuntimeLogEntity entity : entities) {
                entries.add(convertToLogEntry(entity));
            }
            
            return entries;
        } catch (DatabaseAccessException e) {
            System.err.println("查询日志失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 将查询结果转换为实体列表
     */
    private List<RuntimeLogEntity> convertToEntities(List<Map<String, Object>> results) {
        List<RuntimeLogEntity> entities = new ArrayList<>();
        for (Map<String, Object> row : results) {
            RuntimeLogEntity entity = new RuntimeLogEntity();
            entity.setLogId((String) row.get("log_id"));
            entity.setModule((String) row.get("module"));
            entity.setLevel((String) row.get("level"));
            entity.setMessage((String) row.get("message"));
            entity.setLogger((String) row.get("logger"));
            entity.setThread((String) row.get("thread"));
            if (row.get("timestamp") != null) {
                entity.setTimestamp((Timestamp) row.get("timestamp"));
            }
            entity.setRequestId((String) row.get("request_id"));
            entity.setTraceId((String) row.get("trace_id"));
            entity.setSpanId((String) row.get("span_id"));
            entity.setUserId((String) row.get("user_id"));
            entity.setTenantId((String) row.get("tenant_id"));
            entity.setContext((String) row.get("context"));
            entity.setException((String) row.get("exception"));
            entities.add(entity);
        }
        return entities;
    }
    
    /**
     * 将实体转换为LogEntry
     */
    private LogEntry convertToLogEntry(RuntimeLogEntity entity) {
        LogEntry entry = new LogEntry();
        entry.setLogId(entity.getLogId());
        entry.setModule(entity.getModule());
        if (entity.getLevel() != null) {
            try {
                entry.setLevel(ILoggingService.LogLevel.valueOf(entity.getLevel()));
            } catch (IllegalArgumentException e) {
                entry.setLevel(ILoggingService.LogLevel.INFO);
            }
        }
        entry.setMessage(entity.getMessage());
        entry.setLogger(entity.getLogger());
        entry.setThread(entity.getThread());
        if (entity.getTimestamp() != null) {
            entry.setTimestamp(new Date(entity.getTimestamp().getTime()));
        }
        entry.setRequestId(entity.getRequestId());
        entry.setTraceId(entity.getTraceId());
        entry.setSpanId(entity.getSpanId());
        entry.setUserId(entity.getUserId());
        entry.setTenantId(entity.getTenantId());
        
        // 反序列化context和exception
        try {
            if (entity.getContext() != null) {
                entry.setContext(objectMapper.readValue(entity.getContext(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
            if (entity.getException() != null) {
                entry.setException(objectMapper.readValue(entity.getException(), 
                    LogEntry.ExceptionInfo.class));
            }
        } catch (Exception e) {
            // 忽略反序列化错误
        }
        
        return entry;
    }
    
    @Override
    public void archive(Date beforeDate, String targetPath) {
        // 归档逻辑由LogArchiveServiceImpl实现
        // 这里只提供接口，实际归档在归档服务中完成
    }
    
    @Override
    public void delete(Date beforeDate) {
        if (beforeDate == null) {
            return;
        }
        
        try {
            String sql = "DELETE FROM " + TABLE_NAME + " WHERE timestamp < ?";
            databaseAccess.executeUpdate(sql, new Timestamp(beforeDate.getTime()));
        } catch (DatabaseAccessException e) {
            System.err.println("删除日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动异步写入线程
     */
    private void startAsyncWriter() {
        executorService.submit(() -> {
            List<LogEntry> batch = new ArrayList<>();
            long lastFlushTime = System.currentTimeMillis();
            
            while (running || !logQueue.isEmpty()) {
                try {
                    // 从队列中取出日志，最多等待1秒
                    LogEntry entry = logQueue.poll();
                    if (entry != null) {
                        batch.add(entry);
                    }
                    
                    long currentTime = System.currentTimeMillis();
                    boolean shouldFlush = batch.size() >= BATCH_SIZE || 
                                         (currentTime - lastFlushTime >= FLUSH_INTERVAL_MS && !batch.isEmpty());
                    
                    if (shouldFlush) {
                        flushBatch(batch);
                        batch.clear();
                        lastFlushTime = currentTime;
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
                    System.err.println("日志写入失败: " + e.getMessage());
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
    private void flushBatch(List<LogEntry> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        for (LogEntry entry : batch) {
            try {
                // 转换为数据库实体
                RuntimeLogEntity entity = convertToEntity(entry);
                databaseAccess.insert(TABLE_NAME, entity);
            } catch (DatabaseAccessException e) {
                // 记录失败但不影响主流程
                System.err.println("日志保存失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 转换为数据库实体
     */
    private RuntimeLogEntity convertToEntity(LogEntry entry) {
        RuntimeLogEntity entity = new RuntimeLogEntity();
        entity.setLogId(entry.getLogId());
        entity.setModule(entry.getModule());
        entity.setLevel(entry.getLevel() != null ? entry.getLevel().name() : "INFO");
        entity.setMessage(entry.getMessage());
        entity.setLogger(entry.getLogger());
        entity.setThread(entry.getThread());
        entity.setTimestamp(entry.getTimestamp() != null ? 
            new Timestamp(entry.getTimestamp().getTime()) : new Timestamp(System.currentTimeMillis()));
        entity.setRequestId(entry.getRequestId());
        entity.setTraceId(entry.getTraceId());
        entity.setSpanId(entry.getSpanId());
        entity.setUserId(entry.getUserId());
        entity.setTenantId(entry.getTenantId());
        
        // 序列化context和exception为JSON
        try {
            if (entry.getContext() != null) {
                entity.setContext(objectMapper.writeValueAsString(entry.getContext()));
            }
            if (entry.getException() != null) {
                entity.setException(objectMapper.writeValueAsString(entry.getException()));
            }
        } catch (Exception e) {
            // 忽略序列化错误
        }
        
        return entity;
    }
    
    /**
     * 关闭存储
     */
    public void shutdown() {
        running = false;
        executorService.shutdown();
    }
    
    /**
     * 运行时日志实体（对应数据库表结构）
     */
    public static class RuntimeLogEntity {
        private String logId;
        private String module;
        private String level;
        private String message;
        private String logger;
        private String thread;
        private Timestamp timestamp;
        private String requestId;
        private String traceId;
        private String spanId;
        private String userId;
        private String tenantId;
        private String context; // JSON格式
        private String exception; // JSON格式
        
        // Getters and Setters
        public String getLogId() {
            return logId;
        }
        
        public void setLogId(String logId) {
            this.logId = logId;
        }
        
        public String getModule() {
            return module;
        }
        
        public void setModule(String module) {
            this.module = module;
        }
        
        public String getLevel() {
            return level;
        }
        
        public void setLevel(String level) {
            this.level = level;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String getLogger() {
            return logger;
        }
        
        public void setLogger(String logger) {
            this.logger = logger;
        }
        
        public String getThread() {
            return thread;
        }
        
        public void setThread(String thread) {
            this.thread = thread;
        }
        
        public Timestamp getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(Timestamp timestamp) {
            this.timestamp = timestamp;
        }
        
        public String getRequestId() {
            return requestId;
        }
        
        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
        
        public String getTraceId() {
            return traceId;
        }
        
        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
        
        public String getSpanId() {
            return spanId;
        }
        
        public void setSpanId(String spanId) {
            this.spanId = spanId;
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
        
        public String getContext() {
            return context;
        }
        
        public void setContext(String context) {
            this.context = context;
        }
        
        public String getException() {
            return exception;
        }
        
        public void setException(String exception) {
            this.exception = exception;
        }
    }
}

