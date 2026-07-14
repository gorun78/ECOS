package com.chinacreator.gzcm.runtime.core.logging.datachange;

import java.util.Date;
import java.util.List;

/**
 * 数据变更日志服务接口
 */
public interface IDataChangeLogService {
    
    /**
     * 记录变更日志
     * @param entry 日志条目
     */
    void logChange(DataChangeLogEntry entry);
    
    /**
     * 批量记录变更日志
     * @param entries 日志条目列表
     */
    void batchLogChange(List<DataChangeLogEntry> entries);

    /**
     * 查询变更历史
     * @param condition 查询条件
     * @return 变更日志列表
     */
    List<DataChangeLogEntry> queryChangeHistory(DataChangeQueryCondition condition);

    /**
     * 关联审计日志
     * @param changeLogId 变更日志ID
     * @param auditLogId 审计日志ID
     */
    void linkToAuditLog(String changeLogId, String auditLogId);

    /**
     * 根据审计日志ID查询变更日志
     * @param auditLogId 审计日志ID
     * @return 变更日志列表
     */
    List<DataChangeLogEntry> queryByAuditLogId(String auditLogId);

    /**
     * 查询带有审计信息的变更日志
     * @param condition 查询条件
     * @return 带有审计信息的变更日志列表
     */
    List<DataChangeLogEntryWithAudit> queryWithAuditInfo(DataChangeQueryCondition condition);

    /**
     * 带有审计信息的变更日志条目
     */
    public static class DataChangeLogEntryWithAudit extends DataChangeLogEntry {
        private String auditOperationType;
        private String auditResourceType;
        private String auditResourceId;
        private Date auditTime;

        public String getAuditOperationType() { return auditOperationType; }
        public void setAuditOperationType(String auditOperationType) { this.auditOperationType = auditOperationType; }
        public String getAuditResourceType() { return auditResourceType; }
        public void setAuditResourceType(String auditResourceType) { this.auditResourceType = auditResourceType; }
        public String getAuditResourceId() { return auditResourceId; }
        public void setAuditResourceId(String auditResourceId) { this.auditResourceId = auditResourceId; }
        public Date getAuditTime() { return auditTime; }
        public void setAuditTime(Date auditTime) { this.auditTime = auditTime; }
    }
}
