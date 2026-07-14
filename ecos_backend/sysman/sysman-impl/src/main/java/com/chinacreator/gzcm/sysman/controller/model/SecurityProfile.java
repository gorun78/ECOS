package com.chinacreator.gzcm.sysman.controller.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;

/**
 * ECOS Phase 1 P1-1: Security Config Panel — 安全配置模型。
 * <p>
 * MVP 阶段使用内存 ConcurrentHashMap 存储，无 JDBC 依赖。
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ── 标识 ──────────────────────────────────────
    private String id;
    private String name;

    // ── 密码策略 ──────────────────────────────────
    private Integer passwordMinLength;
    private Boolean passwordRequireUppercase;
    private Boolean passwordRequireLowercase;
    private Boolean passwordRequireDigit;
    private Boolean passwordRequireSpecial;
    private Integer passwordExpireDays;
    private Integer passwordHistoryCount;

    // ── 登录安全 ──────────────────────────────────
    private Integer maxLoginAttempts;
    private Integer lockoutDurationMinutes;

    // ── 会话管理 ──────────────────────────────────
    private Integer sessionTimeoutMinutes;
    private Integer maxConcurrentSessions;

    // ── 多因素认证 ────────────────────────────────
    private Boolean mfaEnabled;
    private String mfaType;

    // ── IP 访问控制 ───────────────────────────────
    private String allowedIpRanges;
    private String deniedIpRanges;

    // ── P1-1 安全面板字段 ──────────────────────────
    private Integer clearanceLevel;       // 准入等级 0-4
    private String linkedWorkstation;
    private String auditMode;
    private Boolean sandboxMandatory;

    // ── 准入等级显示 ────────────────────────────────
    private Integer level;               // 准入等级 0-4 (同 clearanceLevel，用于级联查询返回)
    private String levelName;            // 等级名称: L0公开/L1内部/L2保密/L3机密/L4绝密

    // ── User/Role 级联 ────────────────────────────
    private String userId;               // 关联用户ID (nullable)
    private String roleId;               // 关联角色ID (nullable)
    private String scopeType;            // 作用域类型: GLOBAL/TENANT/ORG/ROLE/USER
    private String tenantId;             // 租户ID (scope_type=TENANT)
    private String orgId;                // 机构ID (scope_type=ORG)

    // ── 审计 ──────────────────────────────────────
    private Boolean auditEnabled;
    private String auditLevel;

    // ── 元数据 ────────────────────────────────────
    private String description;
    private Boolean isDefault;
    private Long createdAt;
    private Long updatedAt;

    // ── getter/setter ─────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getPasswordMinLength() { return passwordMinLength; }
    public void setPasswordMinLength(Integer passwordMinLength) { this.passwordMinLength = passwordMinLength; }

    public Boolean getPasswordRequireUppercase() { return passwordRequireUppercase; }
    public void setPasswordRequireUppercase(Boolean passwordRequireUppercase) { this.passwordRequireUppercase = passwordRequireUppercase; }

    public Boolean getPasswordRequireLowercase() { return passwordRequireLowercase; }
    public void setPasswordRequireLowercase(Boolean passwordRequireLowercase) { this.passwordRequireLowercase = passwordRequireLowercase; }

    public Boolean getPasswordRequireDigit() { return passwordRequireDigit; }
    public void setPasswordRequireDigit(Boolean passwordRequireDigit) { this.passwordRequireDigit = passwordRequireDigit; }

    public Boolean getPasswordRequireSpecial() { return passwordRequireSpecial; }
    public void setPasswordRequireSpecial(Boolean passwordRequireSpecial) { this.passwordRequireSpecial = passwordRequireSpecial; }

    public Integer getPasswordExpireDays() { return passwordExpireDays; }
    public void setPasswordExpireDays(Integer passwordExpireDays) { this.passwordExpireDays = passwordExpireDays; }

    public Integer getPasswordHistoryCount() { return passwordHistoryCount; }
    public void setPasswordHistoryCount(Integer passwordHistoryCount) { this.passwordHistoryCount = passwordHistoryCount; }

    public Integer getMaxLoginAttempts() { return maxLoginAttempts; }
    public void setMaxLoginAttempts(Integer maxLoginAttempts) { this.maxLoginAttempts = maxLoginAttempts; }

    public Integer getLockoutDurationMinutes() { return lockoutDurationMinutes; }
    public void setLockoutDurationMinutes(Integer lockoutDurationMinutes) { this.lockoutDurationMinutes = lockoutDurationMinutes; }

    public Integer getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(Integer sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }

    public Integer getMaxConcurrentSessions() { return maxConcurrentSessions; }
    public void setMaxConcurrentSessions(Integer maxConcurrentSessions) { this.maxConcurrentSessions = maxConcurrentSessions; }

    public Boolean getMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getMfaType() { return mfaType; }
    public void setMfaType(String mfaType) { this.mfaType = mfaType; }

    public String getAllowedIpRanges() { return allowedIpRanges; }
    public void setAllowedIpRanges(String allowedIpRanges) { this.allowedIpRanges = allowedIpRanges; }

    public String getDeniedIpRanges() { return deniedIpRanges; }
    public void setDeniedIpRanges(String deniedIpRanges) { this.deniedIpRanges = deniedIpRanges; }

    // ── 安全面板字段 getter/setter ──────────────────

    public Integer getClearanceLevel() { return clearanceLevel; }
    public void setClearanceLevel(Integer clearanceLevel) { this.clearanceLevel = clearanceLevel; }

    public String getLinkedWorkstation() { return linkedWorkstation; }
    public void setLinkedWorkstation(String linkedWorkstation) { this.linkedWorkstation = linkedWorkstation; }

    public String getAuditMode() { return auditMode; }
    public void setAuditMode(String auditMode) { this.auditMode = auditMode; }

    public Boolean getSandboxMandatory() { return sandboxMandatory; }
    public void setSandboxMandatory(Boolean sandboxMandatory) { this.sandboxMandatory = sandboxMandatory; }

    // ── 准入等级显示 getter/setter ──────────────────

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }

    // ── User/Role 级联 getter/setter ───────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    // ── 审计 ──────────────────────────────────────
    public Boolean getAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(Boolean auditEnabled) { this.auditEnabled = auditEnabled; }

    public String getAuditLevel() { return auditLevel; }
    public void setAuditLevel(String auditLevel) { this.auditLevel = auditLevel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
