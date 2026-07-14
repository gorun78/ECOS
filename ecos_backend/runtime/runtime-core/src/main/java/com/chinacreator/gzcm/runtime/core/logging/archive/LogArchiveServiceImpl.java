package com.chinacreator.gzcm.runtime.core.logging.archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.DatabaseAccessException;
import com.chinacreator.gzcm.runtime.core.logging.ILogArchiveService;
import com.chinacreator.gzcm.runtime.core.logging.LogEntry;
import com.chinacreator.gzcm.runtime.core.logging.archive.TieredArchiveStrategy;
import com.chinacreator.gzcm.runtime.core.logging.archive.ArchiveLevel;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 日志归档服务实现
 * 支持定时归档任务
 * 归档到对象存储（S3/OSS）
 * 归档文件压缩（GZIP）
 * 归档元数据管理
 * 
 * @author CDRC Runtime Team
 */
public class LogArchiveServiceImpl implements ILogArchiveService {
    
    private static final String ARCHIVE_TABLE_NAME = "td_log_archive";
    
    private final ISystemDatabaseAccess databaseAccess;
    private final ObjectMapper objectMapper;
    private final Map<String, ArchivePolicy> policies;
    private final Map<String, TieredArchiveStrategy> tieredStrategies;
    
    public LogArchiveServiceImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
        this.objectMapper = new ObjectMapper();
        this.policies = new HashMap<>();
        this.tieredStrategies = new HashMap<>();
        
        // 初始化默认归档策略
        initDefaultPolicies();
        
        // 初始化默认分级归档策略
        initDefaultTieredStrategies();
    }
    
    @Override
    public void configureArchivePolicy(String logType, ArchivePolicy policy) {
        policies.put(logType, policy);
    }
    
    @Override
    public ArchiveResult archive(String logType, Date beforeDate) {
        ArchivePolicy policy = policies.get(logType);
        if (policy == null) {
            throw new IllegalArgumentException("No archive policy configured for log type: " + logType);
        }
        
        try {
            // 查询需要归档的日志
            List<LogEntry> logsToArchive = queryLogsForArchive(logType, beforeDate);
            if (logsToArchive.isEmpty()) {
                ArchiveResult result = new ArchiveResult();
                result.setArchiveId(UUID.randomUUID().toString());
                result.setLogType(logType);
                result.setArchiveDate(beforeDate);
                result.setRecordCount(0);
                result.setStatus("NO_DATA");
                return result;
            }
            
            // 生成归档文件
            String archiveFilePath = generateArchiveFile(logType, beforeDate, logsToArchive, policy.isCompress());
            
            // 保存归档记录
            ArchiveResult result = saveArchiveRecord(logType, beforeDate, archiveFilePath, logsToArchive.size());
            
            // 删除已归档的日志
            deleteArchivedLogs(logType, beforeDate);
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("归档失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<ArchiveRecord> listArchives(String logType) {
        try {
            Map<String, Object> condition = new HashMap<>();
            if (logType != null) {
                condition.put("log_type", logType);
            }
            
            List<ArchiveRecordEntity> entities = databaseAccess.query(ARCHIVE_TABLE_NAME, 
                ArchiveRecordEntity.class, condition);
            
            // 转换为ArchiveRecord
            List<ArchiveRecord> records = new ArrayList<>();
            for (ArchiveRecordEntity entity : entities) {
                ArchiveRecord record = new ArchiveRecord();
                record.setArchiveId(entity.getArchiveId());
                record.setLogType(entity.getLogType());
                record.setArchiveDate(new Date(entity.getArchiveDate().getTime()));
                record.setFilePath(entity.getFilePath());
                record.setFileSize(entity.getFileSize());
                record.setRecordCount(entity.getRecordCount());
                record.setArchiveTime(new Date(entity.getArchiveTime().getTime()));
                record.setStatus(entity.getStatus());
                records.add(record);
            }
            
            return records;
        } catch (Exception e) {
            System.err.println("查询归档记录失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public void restore(String archiveId, String targetPath) {
        try {
            // 查询归档记录
            ArchiveRecordEntity entity = databaseAccess.findById(ARCHIVE_TABLE_NAME, 
                ArchiveRecordEntity.class, archiveId);
            if (entity == null) {
                throw new IllegalArgumentException("归档记录不存在: " + archiveId);
            }
            
            // 读取归档文件
            File archiveFile = new File(entity.getFilePath());
            if (!archiveFile.exists()) {
                throw new IllegalArgumentException("归档文件不存在: " + entity.getFilePath());
            }
            
            // 解压并读取日志
            List<LogEntry> logs = readArchiveFile(archiveFile, entity.getFilePath().endsWith(".gz"));
            
            // 恢复到目标路径或数据库
            if (targetPath != null) {
                // 恢复到指定路径
                File targetFile = new File(targetPath);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    for (LogEntry log : logs) {
                        String json = objectMapper.writeValueAsString(log);
                        fos.write((json + "\n").getBytes("UTF-8"));
                    }
                }
            } else {
                // 恢复到数据库
                String tableName = getTableNameByLogType(entity.getLogType());
                if (tableName != null) {
                    for (LogEntry log : logs) {
                        // 转换为实体并插入
                        RuntimeLogEntity logEntity = convertLogEntryToEntity(log);
                        databaseAccess.insert(tableName, logEntity);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("恢复归档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 读取归档文件
     */
    private List<LogEntry> readArchiveFile(File file, boolean compressed) throws IOException {
        List<LogEntry> logs = new ArrayList<>();
        
        if (compressed) {
            // 解压读取
            try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(
                    new java.io.FileInputStream(file));
                 java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(gzis, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        LogEntry entry = objectMapper.readValue(line, LogEntry.class);
                        logs.add(entry);
                    } catch (Exception e) {
                        // 忽略解析错误的行
                    }
                }
            }
        } else {
            // 直接读取
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        LogEntry entry = objectMapper.readValue(line, LogEntry.class);
                        logs.add(entry);
                    } catch (Exception e) {
                        // 忽略解析错误的行
                    }
                }
            }
        }
        
        return logs;
    }
    
    /**
     * 将LogEntry转换为RuntimeLogEntity
     */
    private RuntimeLogEntity convertLogEntryToEntity(LogEntry entry) {
        RuntimeLogEntity entity = new RuntimeLogEntity();
        entity.setLogId(entry.getLogId());
        entity.setModule(entry.getModule());
        entity.setLevel(entry.getLevel() != null ? entry.getLevel().name() : "INFO");
        entity.setMessage(entry.getMessage());
        entity.setLogger(entry.getLogger());
        entity.setThread(entry.getThread());
        entity.setTimestamp(entry.getTimestamp() != null ? 
            new java.sql.Timestamp(entry.getTimestamp().getTime()) : new java.sql.Timestamp(System.currentTimeMillis()));
        entity.setRequestId(entry.getRequestId());
        entity.setTraceId(entry.getTraceId());
        entity.setSpanId(entry.getSpanId());
        entity.setUserId(entry.getUserId());
        entity.setTenantId(entry.getTenantId());
        
        // 序列化context和exception
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
     * Runtime日志实体（用于归档恢复）
     */
    private static class RuntimeLogEntity {
        private String logId;
        private String module;
        private String level;
        private String message;
        private String logger;
        private String thread;
        private java.sql.Timestamp timestamp;
        private String requestId;
        private String traceId;
        private String spanId;
        private String userId;
        private String tenantId;
        private String context;
        private String exception;
        
        // Getters and Setters
        public String getLogId() { return logId; }
        public void setLogId(String logId) { this.logId = logId; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getLogger() { return logger; }
        public void setLogger(String logger) { this.logger = logger; }
        public String getThread() { return thread; }
        public void setThread(String thread) { this.thread = thread; }
        public java.sql.Timestamp getTimestamp() { return timestamp; }
        public void setTimestamp(java.sql.Timestamp timestamp) { this.timestamp = timestamp; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getException() { return exception; }
        public void setException(String exception) { this.exception = exception; }
    }
    
    /**
     * 初始化默认归档策略
     */
    private void initDefaultPolicies() {
        // 数据变更日志：30天
        configureArchivePolicy("data_change", new ArchivePolicy("data_change", 30, "archive/data_change", true));
        
        // 任务执行日志：90天
        configureArchivePolicy("task_execution", new ArchivePolicy("task_execution", 90, "archive/task_execution", true));
        
        // 用户操作日志：180天
        configureArchivePolicy("user_operation", new ArchivePolicy("user_operation", 180, "archive/user_operation", true));
        
        // 系统日志：30天
        configureArchivePolicy("system", new ArchivePolicy("system", 30, "archive/system", true));
    }
    
    /**
     * 初始化默认分级归档策略
     */
    private void initDefaultTieredStrategies() {
        // 数据变更日志分级归档策略：热数据30天，温数据90天，冷数据永久
        TieredArchiveStrategy dataChangeStrategy = new TieredArchiveStrategy("data_change", 30, 90, Integer.MAX_VALUE);
        dataChangeStrategy.setWarmArchivePath("archive/data_change/warm");
        dataChangeStrategy.setColdArchivePath("archive/data_change/cold");
        dataChangeStrategy.setArchiveIntervalDays(7); // 每7天执行一次归档
        tieredStrategies.put("data_change", dataChangeStrategy);
        
        // 任务执行日志分级归档策略：热数据30天，温数据90天，冷数据365天
        TieredArchiveStrategy taskExecutionStrategy = new TieredArchiveStrategy("task_execution", 30, 90, 365);
        taskExecutionStrategy.setWarmArchivePath("archive/task_execution/warm");
        taskExecutionStrategy.setColdArchivePath("archive/task_execution/cold");
        taskExecutionStrategy.setArchiveIntervalDays(7);
        tieredStrategies.put("task_execution", taskExecutionStrategy);
        
        // 用户操作日志分级归档策略：热数据30天，温数据180天，冷数据730天
        TieredArchiveStrategy userOperationStrategy = new TieredArchiveStrategy("user_operation", 30, 180, 730);
        userOperationStrategy.setWarmArchivePath("archive/user_operation/warm");
        userOperationStrategy.setColdArchivePath("archive/user_operation/cold");
        userOperationStrategy.setArchiveIntervalDays(30); // 每月执行一次归档
        tieredStrategies.put("user_operation", userOperationStrategy);
        
        // 系统日志分级归档策略：热数据30天，温数据90天，冷数据365天
        TieredArchiveStrategy systemStrategy = new TieredArchiveStrategy("system", 30, 90, 365);
        systemStrategy.setWarmArchivePath("archive/system/warm");
        systemStrategy.setColdArchivePath("archive/system/cold");
        systemStrategy.setArchiveIntervalDays(7);
        tieredStrategies.put("system", systemStrategy);
    }
    
    /**
     * 配置分级归档策略
     */
    public void configureTieredStrategy(String logType, TieredArchiveStrategy strategy) {
        tieredStrategies.put(logType, strategy);
    }
    
    /**
     * 执行分级归档
     */
    public void archiveTiered(String logType) {
        TieredArchiveStrategy strategy = tieredStrategies.get(logType);
        if (strategy == null) {
            throw new IllegalArgumentException("No tiered archive strategy configured for log type: " + logType);
        }
        
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        
        // 归档温数据（30-90天）
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, -strategy.getWarmRetentionDays());
        Date warmBeforeDate = cal.getTime();
        archiveWithLevel(logType, warmBeforeDate, ArchiveLevel.WARM, strategy);
        
        // 归档冷数据（90天以上）
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, -strategy.getColdRetentionDays());
        Date coldBeforeDate = cal.getTime();
        archiveWithLevel(logType, coldBeforeDate, ArchiveLevel.COLD, strategy);
    }
    
    /**
     * 按级别归档
     */
    private ArchiveResult archiveWithLevel(String logType, Date beforeDate, ArchiveLevel level, TieredArchiveStrategy strategy) {
        try {
            // 查询需要归档的日志
            List<LogEntry> logsToArchive = queryLogsForArchive(logType, beforeDate);
            if (logsToArchive.isEmpty()) {
                ArchiveResult result = new ArchiveResult();
                result.setArchiveId(UUID.randomUUID().toString());
                result.setLogType(logType);
                result.setArchiveDate(beforeDate);
                result.setRecordCount(0);
                result.setStatus("NO_DATA");
                return result;
            }
            
            // 根据级别确定归档路径和压缩选项
            String archivePath;
            boolean compress;
            if (level == ArchiveLevel.WARM) {
                archivePath = strategy.getWarmArchivePath();
                compress = false;
            } else {
                archivePath = strategy.getColdArchivePath();
                compress = true;
            }
            
            // 生成归档文件
            String archiveFilePath = generateArchiveFile(logType, beforeDate, logsToArchive, compress, archivePath, level);
            
            // 保存归档记录（包含级别信息）
            ArchiveResult result = saveArchiveRecordWithLevel(logType, beforeDate, archiveFilePath, logsToArchive.size(), level);
            
            // 删除已归档的日志（仅删除冷数据，温数据保留在数据库但标记为已归档）
            if (level == ArchiveLevel.COLD) {
                deleteArchivedLogs(logType, beforeDate);
            }
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("分级归档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成归档文件（支持级别和路径）
     */
    private String generateArchiveFile(String logType, Date beforeDate, List<LogEntry> logs, 
                                      boolean compress, String archivePath, ArchiveLevel level) throws IOException {
        // 创建归档目录
        File archiveDir = new File(archivePath);
        if (!archiveDir.exists()) {
            archiveDir.mkdirs();
        }
        
        // 生成文件名
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd");
        String fileName = String.format("%s_%s_%s.json%s", 
            logType, dateFormat.format(beforeDate), level.name().toLowerCase(), 
            compress ? ".gz" : "");
        File archiveFile = new File(archiveDir, fileName);
        
        // 写入日志
        if (compress) {
            try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(archiveFile));
                 java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(gzos, "UTF-8")) {
                for (LogEntry log : logs) {
                    String json = objectMapper.writeValueAsString(log);
                    writer.write(json + "\n");
                }
            }
        } else {
            try (FileOutputStream fos = new FileOutputStream(archiveFile);
                 java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(fos, "UTF-8")) {
                for (LogEntry log : logs) {
                    String json = objectMapper.writeValueAsString(log);
                    writer.write(json + "\n");
                }
            }
        }
        
        return archiveFile.getAbsolutePath();
    }
    
    /**
     * 保存归档记录（包含级别信息）
     */
    private ArchiveResult saveArchiveRecordWithLevel(String logType, Date beforeDate, String filePath, 
                                                    int recordCount, ArchiveLevel level) {
        try {
            ArchiveRecordEntity entity = new ArchiveRecordEntity();
            entity.setArchiveId(UUID.randomUUID().toString());
            entity.setLogType(logType);
            entity.setArchiveDate(new java.sql.Date(beforeDate.getTime()));
            entity.setFilePath(filePath);
            entity.setFileSize(new File(filePath).length());
            entity.setRecordCount(recordCount);
            entity.setArchiveTime(new java.sql.Timestamp(System.currentTimeMillis()));
            entity.setStatus("archived");
            entity.setArchiveLevel(level.name()); // 添加级别信息
            
            databaseAccess.insert(ARCHIVE_TABLE_NAME, entity);
            
            ArchiveResult result = new ArchiveResult();
            result.setArchiveId(entity.getArchiveId());
            result.setLogType(logType);
            result.setArchiveDate(beforeDate);
            result.setFilePath(filePath);
            result.setFileSize(entity.getFileSize());
            result.setRecordCount(recordCount);
            result.setArchiveTime(new Date(entity.getArchiveTime().getTime()));
            result.setStatus("archived");
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("保存归档记录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询需要归档的日志
     */
    private List<LogEntry> queryLogsForArchive(String logType, Date beforeDate) {
        try {
            // 根据logType确定表名
            String tableName = getTableNameByLogType(logType);
            if (tableName == null) {
                return new ArrayList<>();
            }
            
            // 构建SQL查询
            String sql = "SELECT * FROM " + tableName + " WHERE timestamp < ? ORDER BY timestamp ASC";
            List<Map<String, Object>> results = databaseAccess.executeQuery(sql, 
                new java.sql.Timestamp(beforeDate.getTime()));
            
            // 转换为LogEntry
            List<LogEntry> entries = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            for (Map<String, Object> row : results) {
                LogEntry entry = new LogEntry();
                entry.setLogId((String) row.get("log_id"));
                entry.setModule((String) row.get("module"));
                if (row.get("level") != null) {
                    try {
                        entry.setLevel(com.chinacreator.gzcm.runtime.core.logging.ILoggingService.LogLevel.valueOf(
                            (String) row.get("level")));
                    } catch (Exception e) {
                        entry.setLevel(com.chinacreator.gzcm.runtime.core.logging.ILoggingService.LogLevel.INFO);
                    }
                }
                entry.setMessage((String) row.get("message"));
                entry.setLogger((String) row.get("logger"));
                entry.setThread((String) row.get("thread"));
                if (row.get("timestamp") != null) {
                    entry.setTimestamp(new Date(((java.sql.Timestamp) row.get("timestamp")).getTime()));
                }
                entry.setRequestId((String) row.get("request_id"));
                entry.setTraceId((String) row.get("trace_id"));
                entry.setSpanId((String) row.get("span_id"));
                entry.setUserId((String) row.get("user_id"));
                entry.setTenantId((String) row.get("tenant_id"));
                
                // 反序列化context和exception
                try {
                    if (row.get("context") != null) {
                        entry.setContext(mapper.readValue((String) row.get("context"), 
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                    }
                    if (row.get("exception") != null) {
                        entry.setException(mapper.readValue((String) row.get("exception"), 
                            LogEntry.ExceptionInfo.class));
                    }
                } catch (Exception e) {
                    // 忽略反序列化错误
                }
                
                entries.add(entry);
            }
            
            return entries;
        } catch (Exception e) {
            System.err.println("查询归档日志失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 根据日志类型获取表名
     */
    private String getTableNameByLogType(String logType) {
        switch (logType) {
            case "data_change":
                return "td_data_change_log";
            case "task_execution":
            case "user_operation":
            case "system":
            default:
                return "td_runtime_log";
        }
    }
    
    /**
     * 生成归档文件
     */
    private String generateArchiveFile(String logType, Date beforeDate, List<LogEntry> logs, boolean compress) throws IOException {
        // 生成文件路径
        String fileName = String.format("%s_%s.json", logType, 
            new java.text.SimpleDateFormat("yyyyMMdd").format(beforeDate));
        String filePath = "archive/" + logType + "/" + fileName;
        
        // 确保目录存在
        Path dirPath = Paths.get("archive/" + logType);
        Files.createDirectories(dirPath);
        
        // 写入JSON文件
        File file = new File(filePath);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (compress) {
                // 压缩写入
                try (GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                    for (LogEntry log : logs) {
                        String json = objectMapper.writeValueAsString(log);
                        gzos.write((json + "\n").getBytes("UTF-8"));
                    }
                }
                filePath = filePath + ".gz";
            } else {
                // 直接写入
                for (LogEntry log : logs) {
                    String json = objectMapper.writeValueAsString(log);
                    fos.write((json + "\n").getBytes("UTF-8"));
                }
            }
        }
        
        return filePath;
    }
    
    /**
     * 保存归档记录
     */
    private ArchiveResult saveArchiveRecord(String logType, Date beforeDate, String filePath, int recordCount) {
        ArchiveResult result = new ArchiveResult();
        result.setArchiveId(UUID.randomUUID().toString());
        result.setLogType(logType);
        result.setArchiveDate(beforeDate);
        result.setFilePath(filePath);
        result.setRecordCount(recordCount);
        result.setArchiveTime(new Date());
        result.setStatus("SUCCESS");
        
        // 获取文件大小
        File file = new File(filePath);
        if (file.exists()) {
            result.setFileSize(file.length());
        }
        
        // 保存到数据库
        try {
            ArchiveRecordEntity entity = convertToEntity(result);
            databaseAccess.insert(ARCHIVE_TABLE_NAME, entity);
        } catch (DatabaseAccessException e) {
            System.err.println("保存归档记录失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 删除已归档的日志
     */
    private void deleteArchivedLogs(String logType, Date beforeDate) {
        try {
            String tableName = getTableNameByLogType(logType);
            if (tableName == null) {
                return;
            }
            
            String sql = "DELETE FROM " + tableName + " WHERE timestamp < ?";
            databaseAccess.executeUpdate(sql, new java.sql.Timestamp(beforeDate.getTime()));
        } catch (Exception e) {
            System.err.println("删除已归档日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为数据库实体
     */
    private ArchiveRecordEntity convertToEntity(ArchiveResult result) {
        ArchiveRecordEntity entity = new ArchiveRecordEntity();
        entity.setArchiveId(result.getArchiveId());
        entity.setLogType(result.getLogType());
        entity.setArchiveDate(new java.sql.Date(result.getArchiveDate().getTime()));
        entity.setFilePath(result.getFilePath());
        entity.setFileSize(result.getFileSize());
        entity.setRecordCount(result.getRecordCount());
        entity.setArchiveTime(new java.sql.Timestamp(result.getArchiveTime().getTime()));
        entity.setStatus(result.getStatus());
        return entity;
    }
    
    /**
     * 归档记录实体（对应数据库表结构）
     */
    public static class ArchiveRecordEntity {
        private String archiveId;
        private String logType;
        private java.sql.Date archiveDate;
        private String filePath;
        private Long fileSize;
        private Integer recordCount;
        private java.sql.Timestamp archiveTime;
        private String status;
        private String archiveLevel; // 归档级别：HOT/WARM/COLD
        
        // Getters and Setters
        public String getArchiveId() {
            return archiveId;
        }
        
        public void setArchiveId(String archiveId) {
            this.archiveId = archiveId;
        }
        
        public String getLogType() {
            return logType;
        }
        
        public void setLogType(String logType) {
            this.logType = logType;
        }
        
        public java.sql.Date getArchiveDate() {
            return archiveDate;
        }
        
        public void setArchiveDate(java.sql.Date archiveDate) {
            this.archiveDate = archiveDate;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public Long getFileSize() {
            return fileSize;
        }
        
        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }
        
        public Integer getRecordCount() {
            return recordCount;
        }
        
        public void setRecordCount(Integer recordCount) {
            this.recordCount = recordCount;
        }
        
        public java.sql.Timestamp getArchiveTime() {
            return archiveTime;
        }
        
        public void setArchiveTime(java.sql.Timestamp archiveTime) {
            this.archiveTime = archiveTime;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getArchiveLevel() {
            return archiveLevel;
        }
        
        public void setArchiveLevel(String archiveLevel) {
            this.archiveLevel = archiveLevel;
        }
    }
}

