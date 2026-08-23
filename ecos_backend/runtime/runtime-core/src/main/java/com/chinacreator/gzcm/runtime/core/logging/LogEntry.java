package com.chinacreator.gzcm.runtime.core.logging;

import java.util.Date;
import java.util.Map;

/**
 * 日志条目
 * 
 * @author CDRC Runtime Team
 */
public class LogEntry {
    private String logId;
    private String module;
    private ILoggingService.LogLevel level;
    private String message;
    private String logger;
    private String thread;
    private Date timestamp;
    private String requestId;
    private String traceId;
    private String spanId;
    private String userId;
    private String tenantId;
    private Map<String, Object> context;
    private ExceptionInfo exception;
    
    public LogEntry() {
        this.timestamp = new Date();
    }
    
    public LogEntry(String module, ILoggingService.LogLevel level, String message) {
        this();
        this.module = module;
        this.level = level;
        this.message = message;
    }
    
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
    
    public ILoggingService.LogLevel getLevel() {
        return level;
    }
    
    public void setLevel(ILoggingService.LogLevel level) {
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
    
    public Date getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Date timestamp) {
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
    
    public Map<String, Object> getContext() {
        return context;
    }
    
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    public ExceptionInfo getException() {
        return exception;
    }
    
    public void setException(ExceptionInfo exception) {
        this.exception = exception;
    }
    
    /**
     * 异常信息
     */
    public static class ExceptionInfo {
        private String type;
        private String message;
        private String[] stackTrace;
        
        public ExceptionInfo() {
        }
        
        public ExceptionInfo(Throwable throwable) {
            this.type = throwable.getClass().getName();
            this.message = throwable.getMessage();
            if (throwable.getStackTrace() != null) {
                this.stackTrace = new String[throwable.getStackTrace().length];
                for (int i = 0; i < throwable.getStackTrace().length; i++) {
                    this.stackTrace[i] = throwable.getStackTrace()[i].toString();
                }
            }
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String[] getStackTrace() {
            return stackTrace;
        }
        
        public void setStackTrace(String[] stackTrace) {
            this.stackTrace = stackTrace;
        }
    }
}

