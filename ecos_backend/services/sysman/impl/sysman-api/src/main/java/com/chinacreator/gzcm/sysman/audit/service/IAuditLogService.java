package com.chinacreator.gzcm.sysman.audit.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;

/**
 * 审计日志服务接口
 */
public interface IAuditLogService {

    /**
     * 记录审计日志
     *
     * @param event 审计事件
     */
    void log(AuditEvent event);

    /**
     * 查询审计日志
     *
     * @param condition 查询条件
     * @return 审计日志列表
     */
    List<AuditEvent> query(AuditQueryCondition condition);

    /**
     * 根据ID获取审计日志
     *
     * @param logId 日志ID
     * @return 审计事件
     */
    AuditEvent getById(String logId);
    
    /**
     * 统计审计日志
     * 
     * @param condition 查询条件
     * @return 统计结果
     */
    AuditStatistics statistics(AuditQueryCondition condition);
    
    /**
     * 导出审计日志
     * 
     * @param condition 查询条件
     * @param format 导出格式（CSV/JSON/EXCEL）
     * @return 导出数据
     */
    byte[] export(AuditQueryCondition condition, String format);
    
    /**
     * 审计查询条件
     */
    class AuditQueryCondition {
        private String userId;
        private String tenantId;
        private String resource;
        private String eventType;
        private String result;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer page;
        private Integer pageSize;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    }
    
    /**
     * 审计统计结果
     */
    class AuditStatistics {
        private Long totalCount;  // 总记录数
        private Long successCount;  // 成功数
        private Long failureCount;  // 失败数
        private Map<String, Long> eventTypeCount;  // 按事件类型统计
        private Map<String, Long> userCount;  // 按用户统计
        private Map<String, Long> resourceCount;  // 按资源统计
        private Map<String, Long> dailyCount;  // 按日期统计
        
        public Long getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(Long totalCount) {
            this.totalCount = totalCount;
        }
        
        public Long getSuccessCount() {
            return successCount;
        }
        
        public void setSuccessCount(Long successCount) {
            this.successCount = successCount;
        }
        
        public Long getFailureCount() {
            return failureCount;
        }
        
        public void setFailureCount(Long failureCount) {
            this.failureCount = failureCount;
        }
        
        public Map<String, Long> getEventTypeCount() {
            return eventTypeCount;
        }
        
        public void setEventTypeCount(Map<String, Long> eventTypeCount) {
            this.eventTypeCount = eventTypeCount;
        }
        
        public Map<String, Long> getUserCount() {
            return userCount;
        }
        
        public void setUserCount(Map<String, Long> userCount) {
            this.userCount = userCount;
        }
        
        public Map<String, Long> getResourceCount() {
            return resourceCount;
        }
        
        public void setResourceCount(Map<String, Long> resourceCount) {
            this.resourceCount = resourceCount;
        }
        
        public Map<String, Long> getDailyCount() {
            return dailyCount;
        }
        
        public void setDailyCount(Map<String, Long> dailyCount) {
            this.dailyCount = dailyCount;
        }
    }
}

