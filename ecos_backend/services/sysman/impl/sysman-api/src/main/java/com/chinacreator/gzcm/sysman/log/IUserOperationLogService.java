package com.chinacreator.gzcm.sysman.log;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户操作日志服务接口
 * 记录用户的操作行为，由Sys-Man管理
 * 
 * @author CDRC Sys-Man Team
 */
public interface IUserOperationLogService {
    
    /**
     * 用户操作日志条目
     */
    class UserOperationLogEntry {
        private String logId;
        private String userId;
        private String userName;
        private String orgId;
        private String module;
        private String operationType; // 1:新增 2:删除 3:修改 4:其他
        private String content;
        private String ipAddress;
        private Date operationTime;
        private String remark1;
        
        // Getters and Setters
        public String getLogId() {
            return logId;
        }
        
        public void setLogId(String logId) {
            this.logId = logId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getUserName() {
            return userName;
        }
        
        public void setUserName(String userName) {
            this.userName = userName;
        }
        
        public String getOrgId() {
            return orgId;
        }
        
        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }
        
        public String getModule() {
            return module;
        }
        
        public void setModule(String module) {
            this.module = module;
        }
        
        public String getOperationType() {
            return operationType;
        }
        
        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public String getIpAddress() {
            return ipAddress;
        }
        
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
        
        public Date getOperationTime() {
            return operationTime;
        }
        
        public void setOperationTime(Date operationTime) {
            this.operationTime = operationTime;
        }
        
        public String getRemark1() {
            return remark1;
        }
        
        public void setRemark1(String remark1) {
            this.remark1 = remark1;
        }
    }
    
    /**
     * 用户操作日志查询条件
     */
    class UserOperationQueryCondition {
        private String userId;
        private String orgId;
        private String module;
        private String operationType;
        private Date startTime;
        private Date endTime;
        private String keyword;
        private Integer page;
        private Integer pageSize;
        
        // Getters and Setters
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getOrgId() {
            return orgId;
        }
        
        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }
        
        public String getModule() {
            return module;
        }
        
        public void setModule(String module) {
            this.module = module;
        }
        
        public String getOperationType() {
            return operationType;
        }
        
        public void setOperationType(String operationType) {
            this.operationType = operationType;
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
    
    /**
     * 操作统计结果
     */
    class OperationStatistics {
        private Long totalCount;
        private Map<String, Long> operationTypeCount;
        private Map<String, Long> moduleCount;
        private Map<String, Long> userCount;
        private Map<String, Long> dailyCount;
        
        // Getters and Setters
        public Long getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(Long totalCount) {
            this.totalCount = totalCount;
        }
        
        public Map<String, Long> getOperationTypeCount() {
            return operationTypeCount;
        }
        
        public void setOperationTypeCount(Map<String, Long> operationTypeCount) {
            this.operationTypeCount = operationTypeCount;
        }
        
        public Map<String, Long> getModuleCount() {
            return moduleCount;
        }
        
        public void setModuleCount(Map<String, Long> moduleCount) {
            this.moduleCount = moduleCount;
        }
        
        public Map<String, Long> getUserCount() {
            return userCount;
        }
        
        public void setUserCount(Map<String, Long> userCount) {
            this.userCount = userCount;
        }
        
        public Map<String, Long> getDailyCount() {
            return dailyCount;
        }
        
        public void setDailyCount(Map<String, Long> dailyCount) {
            this.dailyCount = dailyCount;
        }
    }
    
    /**
     * 记录用户操作
     * 
     * @param entry 用户操作日志条目
     */
    void logOperation(UserOperationLogEntry entry);
    
    /**
     * 查询操作日志
     * 
     * @param condition 查询条件
     * @return 操作日志列表
     */
    List<UserOperationLogEntry> queryOperations(UserOperationQueryCondition condition);
    
    /**
     * 统计操作日志
     * 
     * @param condition 查询条件
     * @return 统计结果
     */
    OperationStatistics statistics(UserOperationQueryCondition condition);
    
    /**
     * 归档操作日志
     * 
     * @param beforeDate 归档此日期之前的数据
     */
    void archive(Date beforeDate);
}

