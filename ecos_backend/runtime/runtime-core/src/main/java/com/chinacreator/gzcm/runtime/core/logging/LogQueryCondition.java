package com.chinacreator.gzcm.runtime.core.logging;

import java.util.Date;

/**
 * 日志查询条件
 * 
 * @author CDRC Runtime Team
 */
public class LogQueryCondition {
    private String module;
    private ILoggingService.LogLevel level;
    private String logger;
    private String requestId;
    private String traceId;
    private String userId;
    private String tenantId;
    private Date startTime;
    private Date endTime;
    private String keyword;
    private Integer page;
    private Integer pageSize;
    
    public LogQueryCondition() {
    }
    
    // Getters and Setters
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
    
    public String getLogger() {
        return logger;
    }
    
    public void setLogger(String logger) {
        this.logger = logger;
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
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    public String getKeyword() {
        return keyword;
    }
    
    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
    
    public Integer getPage() {
        return page;
    }
    
    public void setPage(Integer page) {
        this.page = page;
    }
    
    public Integer getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}

