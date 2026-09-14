package com.chinacreator.gzcm.sysman.log.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess.DatabaseAccessException;
import com.chinacreator.gzcm.runtime.core.logging.ILoggingService;
import com.chinacreator.gzcm.runtime.core.logging.LogQueryCondition;
import com.chinacreator.gzcm.sysman.log.IUserOperationLogService;

/**
 * 用户操作日志服务实现
 * 与Runtime日志框架集成，使用模块标识sys-man.user-operation
 * 保持现有td_sm_log表结构，通过适配器接入统一框架
 * 
 * @author CDRC Sys-Man Team
 */
@Service
public class UserOperationLogServiceImpl implements IUserOperationLogService {
    
    private static final String TABLE_NAME = "td_sm_log";
    
    private final ILoggingService loggingService;
    private final ISystemDatabaseAccess databaseAccess;
    
    @Autowired
    public UserOperationLogServiceImpl(ILoggingService loggingService, 
                                      ISystemDatabaseAccess databaseAccess) {
        this.loggingService = loggingService;
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void logOperation(UserOperationLogEntry entry) {
        if (entry == null) {
            return;
        }
        
        // 生成日志ID
        if (entry.getLogId() == null) {
            entry.setLogId(UUID.randomUUID().toString());
        }
        
        // 设置操作时间
        if (entry.getOperationTime() == null) {
            entry.setOperationTime(new Date());
        }
        
        // 转换为Runtime日志格式
        Map<String, Object> context = new HashMap<>();
        context.put("logId", entry.getLogId());
        context.put("userId", entry.getUserId());
        context.put("userName", entry.getUserName());
        context.put("orgId", entry.getOrgId());
        context.put("module", entry.getModule());
        context.put("operationType", entry.getOperationType());
        context.put("content", entry.getContent());
        context.put("ipAddress", entry.getIpAddress());
        context.put("remark1", entry.getRemark1());
        
        // 通过Runtime日志框架记录
        loggingService.log("sys-man.user-operation", 
                          ILoggingService.LogLevel.INFO, 
                          entry.getContent(), 
                          context);
        
        // 同时保存到td_sm_log表（保持兼容性）
        try {
            SmLogEntity smLogEntity = convertToSmLogEntity(entry);
            databaseAccess.insert(TABLE_NAME, smLogEntity);
        } catch (DatabaseAccessException e) {
            // 保存到td_sm_log失败不影响Runtime日志记录
            System.err.println("保存到td_sm_log表失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为td_sm_log表实体
     */
    private SmLogEntity convertToSmLogEntity(UserOperationLogEntry entry) {
        SmLogEntity entity = new SmLogEntity();
        
        // 转换logId为numeric类型（如果logId是UUID，需要转换为数字）
        try {
            // 尝试将UUID转换为数字（简化处理：使用hashCode的绝对值）
            long logIdNumeric = Math.abs(entry.getLogId().hashCode());
            entity.setLogId(logIdNumeric);
        } catch (Exception e) {
            // 如果转换失败，使用默认值
            entity.setLogId(System.currentTimeMillis());
        }
        
        entity.setLogOperuser(entry.getUserId());
        entity.setOpOrgid(entry.getOrgId());
        entity.setOperModule(entry.getModule());
        entity.setLogVisitorial(entry.getIpAddress());
        entity.setLogOpertime(entry.getOperationTime() != null ? 
            new Timestamp(entry.getOperationTime().getTime()) : new Timestamp(System.currentTimeMillis()));
        entity.setLogContent(entry.getContent());
        entity.setRemark1(entry.getRemark1());
        
        // 转换操作类型为numeric
        try {
            int operType = Integer.parseInt(entry.getOperationType());
            entity.setOperType(operType);
        } catch (NumberFormatException e) {
            // 如果转换失败，使用默认值4（其他）
            entity.setOperType(4);
        }
        
        return entity;
    }
    
    /**
     * td_sm_log表实体类
     */
    public static class SmLogEntity {
        private Long logId;
        private String logOperuser;
        private String opOrgid;
        private String operModule;
        private String logVisitorial;
        private Timestamp logOpertime;
        private String logContent;
        private String remark1;
        private Integer operType;
        
        // Getters and Setters
        public Long getLogId() {
            return logId;
        }
        
        public void setLogId(Long logId) {
            this.logId = logId;
        }
        
        public String getLogOperuser() {
            return logOperuser;
        }
        
        public void setLogOperuser(String logOperuser) {
            this.logOperuser = logOperuser;
        }
        
        public String getOpOrgid() {
            return opOrgid;
        }
        
        public void setOpOrgid(String opOrgid) {
            this.opOrgid = opOrgid;
        }
        
        public String getOperModule() {
            return operModule;
        }
        
        public void setOperModule(String operModule) {
            this.operModule = operModule;
        }
        
        public String getLogVisitorial() {
            return logVisitorial;
        }
        
        public void setLogVisitorial(String logVisitorial) {
            this.logVisitorial = logVisitorial;
        }
        
        public Timestamp getLogOpertime() {
            return logOpertime;
        }
        
        public void setLogOpertime(Timestamp logOpertime) {
            this.logOpertime = logOpertime;
        }
        
        public String getLogContent() {
            return logContent;
        }
        
        public void setLogContent(String logContent) {
            this.logContent = logContent;
        }
        
        public String getRemark1() {
            return remark1;
        }
        
        public void setRemark1(String remark1) {
            this.remark1 = remark1;
        }
        
        public Integer getOperType() {
            return operType;
        }
        
        public void setOperType(Integer operType) {
            this.operType = operType;
        }
    }
    
    @Override
    public List<UserOperationLogEntry> queryOperations(UserOperationQueryCondition condition) {
        if (condition == null) {
            return new ArrayList<>();
        }
        
        try {
            // 优先从Runtime日志框架查询（统一格式）
            LogQueryCondition logCondition = new LogQueryCondition();
            logCondition.setModule("sys-man.user-operation");
            if (condition.getUserId() != null) {
                logCondition.setUserId(condition.getUserId());
            }
            if (condition.getStartTime() != null) {
                logCondition.setStartTime(condition.getStartTime());
            }
            if (condition.getEndTime() != null) {
                logCondition.setEndTime(condition.getEndTime());
            }
            if (condition.getKeyword() != null) {
                logCondition.setKeyword(condition.getKeyword());
            }
            if (condition.getPage() != null) {
                logCondition.setPage(condition.getPage());
            }
            if (condition.getPageSize() != null) {
                logCondition.setPageSize(condition.getPageSize());
            }
            
            // 从Runtime日志框架查询
            List<com.chinacreator.gzcm.runtime.core.logging.LogEntry> logEntries = 
                loggingService.query(logCondition);
            
            // 转换为UserOperationLogEntry
            List<UserOperationLogEntry> entries = new ArrayList<>();
            for (com.chinacreator.gzcm.runtime.core.logging.LogEntry logEntry : logEntries) {
                UserOperationLogEntry entry = convertToUserOperationLogEntry(logEntry);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            
            return entries;
        } catch (Exception e) {
            System.err.println("查询用户操作日志失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 将LogEntry转换为UserOperationLogEntry
     */
    private UserOperationLogEntry convertToUserOperationLogEntry(
            com.chinacreator.gzcm.runtime.core.logging.LogEntry logEntry) {
        if (logEntry == null || logEntry.getContext() == null) {
            return null;
        }
        
        Map<String, Object> context = logEntry.getContext();
        UserOperationLogEntry entry = new UserOperationLogEntry();
        entry.setLogId((String) context.get("logId"));
        entry.setUserId((String) context.get("userId"));
        entry.setUserName((String) context.get("userName"));
        entry.setOrgId((String) context.get("orgId"));
        entry.setModule((String) context.get("module"));
        entry.setOperationType((String) context.get("operationType"));
        entry.setContent(logEntry.getMessage());
        entry.setIpAddress((String) context.get("ipAddress"));
        entry.setRemark1((String) context.get("remark1"));
        entry.setOperationTime(logEntry.getTimestamp());
        
        return entry;
    }
    
    @Override
    public OperationStatistics statistics(UserOperationQueryCondition condition) {
        OperationStatistics statistics = new OperationStatistics();
        
        try {
            // 查询所有符合条件的日志
            List<UserOperationLogEntry> entries = queryOperations(condition);
            
            statistics.setTotalCount((long) entries.size());
            
            // 按操作类型统计
            Map<String, Long> operationTypeCount = new HashMap<>();
            Map<String, Long> moduleCount = new HashMap<>();
            Map<String, Long> userCount = new HashMap<>();
            Map<String, Long> dailyCount = new HashMap<>();
            
            for (UserOperationLogEntry entry : entries) {
                // 操作类型统计
                String opType = entry.getOperationType();
                operationTypeCount.put(opType, operationTypeCount.getOrDefault(opType, 0L) + 1);
                
                // 模块统计
                String module = entry.getModule();
                if (module != null) {
                    moduleCount.put(module, moduleCount.getOrDefault(module, 0L) + 1);
                }
                
                // 用户统计
                String userId = entry.getUserId();
                if (userId != null) {
                    userCount.put(userId, userCount.getOrDefault(userId, 0L) + 1);
                }
                
                // 按日期统计
                if (entry.getOperationTime() != null) {
                    String date = new java.text.SimpleDateFormat("yyyy-MM-dd")
                        .format(entry.getOperationTime());
                    dailyCount.put(date, dailyCount.getOrDefault(date, 0L) + 1);
                }
            }
            
            statistics.setOperationTypeCount(operationTypeCount);
            statistics.setModuleCount(moduleCount);
            statistics.setUserCount(userCount);
            statistics.setDailyCount(dailyCount);
        } catch (Exception e) {
            System.err.println("统计用户操作日志失败: " + e.getMessage());
            statistics.setTotalCount(0L);
            statistics.setOperationTypeCount(new HashMap<>());
            statistics.setModuleCount(new HashMap<>());
            statistics.setUserCount(new HashMap<>());
            statistics.setDailyCount(new HashMap<>());
        }
        
        return statistics;
    }
    
    @Override
    public void archive(Date beforeDate) {
        // 通过Runtime日志框架归档
        loggingService.archive("user_operation", beforeDate);
    }
}

