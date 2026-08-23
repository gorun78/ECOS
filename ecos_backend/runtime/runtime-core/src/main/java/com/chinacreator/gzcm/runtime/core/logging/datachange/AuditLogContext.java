package com.chinacreator.gzcm.runtime.core.logging.datachange;

/**
 * 审计日志上下文
 */
public class AuditLogContext {
    
    private static final ThreadLocal<AuditLogContext> CONTEXT = new ThreadLocal<>();
    
    private String auditLogId;
    private String userId;
    private String tenantId;
    private String traceId;
    
    public static AuditLogContext getCurrent() {
        return CONTEXT.get();
    }
    
    public static void setCurrent(AuditLogContext context) {
        CONTEXT.set(context);
    }
    
    public static void clear() {
        CONTEXT.remove();
    }
    
    public String getAuditLogId() {
        return auditLogId;
    }
    
    public void setAuditLogId(String auditLogId) {
        this.auditLogId = auditLogId;
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
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
