package com.chinacreator.gzcm.engine.security.service;

import com.chinacreator.gzcm.sysman.controller.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

@Service
public class SecurityConfigService {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigService.class);

    private static final String[] LEVEL_NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    private final JdbcTemplate jdbc;

    private final RowMapper<SecurityProfile> userProfileMapper = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setUserId(rs.getString("user_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(levelNameFor(rs.getInt("clearance_level")));
        p.setLinkedWorkstation(rs.getString("linked_workstation"));
        p.setAuditMode(rs.getString("audit_mode"));
        p.setSandboxMandatory(rs.getBoolean("sandbox_mandatory"));
        p.setIsDefault(rs.getBoolean("is_default"));
        p.setScopeType(rs.getString("scope_type"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setOrgId(rs.getString("org_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        p.setCreatedAt(ca != null ? ca.getTime() : null);
        Timestamp ua = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ua != null ? ua.getTime() : null);
        return p;
    };

    private final RowMapper<SecurityProfile> roleProfileMapper = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setRoleId(rs.getString("role_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(levelNameFor(rs.getInt("clearance_level")));
        p.setLinkedWorkstation(rs.getString("linked_workstation"));
        p.setAuditMode(rs.getString("audit_mode"));
        p.setSandboxMandatory(rs.getBoolean("sandbox_mandatory"));
        p.setScopeType(rs.getString("scope_type"));
        p.setTenantId(rs.getString("tenant_id"));
        p.setOrgId(rs.getString("org_id"));
        Timestamp ca = rs.getTimestamp("created_at");
        p.setCreatedAt(ca != null ? ca.getTime() : null);
        Timestamp ua = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ua != null ? ua.getTime() : null);
        return p;
    };

    public SecurityConfigService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String getCurrentTenantId() {
        try {
            String tid = UserContext.getCurrentTenantId();
            if (tid != null && !tid.isBlank()) {
                return tid;
            }
        } catch (Exception e) {
            log.debug("从 UserContext 获取 tenantId 失败: {}", e.getMessage());
        }
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            log.debug("从 TenantContext 获取 tenantId 失败: {}", e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryUserProfile(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{userId, tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? LIMIT 1";
                params = new Object[]{userId};
            }
            List<SecurityProfile> list = jdbc.query(sql, userProfileMapper, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询用户安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryRoleProfile(String roleId) {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{roleId, tenantId};
            } else {
                sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? LIMIT 1";
                params = new Object[]{roleId};
            }
            List<SecurityProfile> list = jdbc.query(sql, roleProfileMapper, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询角色安全配置异常: roleId={}, {}", roleId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryHighestRoleProfileForUser(String userId) {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = """
                        SELECT rsp.* FROM td_role_security_profile rsp
                        JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ? AND (rsp.tenant_id = ? OR rsp.tenant_id IS NULL)
                        ORDER BY rsp.clearance_level DESC
                        LIMIT 1
                        """;
                params = new Object[]{userId, tenantId};
            } else {
                sql = """
                        SELECT rsp.* FROM td_role_security_profile rsp
                        JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ?
                        ORDER BY rsp.clearance_level DESC
                        LIMIT 1
                        """;
                params = new Object[]{userId};
            }
            List<SecurityProfile> list = jdbc.query(sql, roleProfileMapper, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询用户最高角色安全配置异常: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    public SecurityProfile queryGlobalDefaultProfile() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
                params = new Object[]{};
            }
            List<SecurityProfile> list = jdbc.query(sql, userProfileMapper, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("查询全局默认安全配置异常: {}", e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryAllUserProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY updated_at DESC";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_user_security_profile ORDER BY updated_at DESC";
                params = new Object[]{};
            }
            return jdbc.query(sql, userProfileMapper, params);
        } catch (Exception e) {
            log.warn("查询所有用户安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<SecurityProfile> queryAllRoleProfiles() {
        try {
            String tenantId = getCurrentTenantId();
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT * FROM td_role_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY clearance_level DESC";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT * FROM td_role_security_profile ORDER BY clearance_level DESC";
                params = new Object[]{};
            }
            return jdbc.query(sql, roleProfileMapper, params);
        } catch (Exception e) {
            log.warn("查询所有角色安全配置异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public SecurityProfile queryProfileByScope(String scopeType, String scopeId) {
        try {
            String sql;
            Object[] params;
            switch (scopeType) {
                case "TENANT":
                    sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? AND scope_type = 'TENANT' LIMIT 1";
                    params = new Object[]{scopeId};
                    break;
                case "ORG":
                    sql = "SELECT * FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' LIMIT 1";
                    params = new Object[]{scopeId};
                    break;
                default:
                    log.debug("未知作用域类型: {}", scopeType);
                    return null;
            }
            List<SecurityProfile> list = jdbc.query(sql, userProfileMapper, params);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            log.warn("按作用域查询安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return null;
        }
    }

    public List<SecurityProfile> queryProfilesByScope(String scopeType, String scopeId) {
        try {
            String sql;
            Object[] params;
            if (scopeId != null && !scopeId.isBlank()) {
                switch (scopeType.toUpperCase()) {
                    case "TENANT":
                        sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC";
                        List<SecurityProfile> userProfiles = jdbc.query(sql, userProfileMapper, scopeId);
                        String roleSql = "SELECT * FROM td_role_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC";
                        List<SecurityProfile> roleProfiles = jdbc.query(roleSql, roleProfileMapper, scopeId);
                        List<SecurityProfile> all = new ArrayList<>();
                        all.addAll(userProfiles);
                        all.addAll(roleProfiles);
                        return all;
                    case "ORG":
                        sql = "SELECT * FROM td_user_security_profile WHERE org_id = ? ORDER BY updated_at DESC";
                        params = new Object[]{scopeId};
                        break;
                    default:
                        sql = "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC";
                        params = new Object[]{scopeType.toUpperCase()};
                }
            } else {
                sql = "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC";
                params = new Object[]{scopeType.toUpperCase()};
            }
            return jdbc.query(sql, userProfileMapper, params);
        } catch (Exception e) {
            log.warn("按作用域列出安全配置异常: scopeType={}, scopeId={}, {}", scopeType, scopeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> populateProfileResult(SecurityProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (profile != null) {
            result.put("clearanceLevel", profile.getClearanceLevel() != null ? profile.getClearanceLevel() : 1);
            result.put("level", profile.getLevel() != null ? profile.getLevel() : 1);
            result.put("levelName", profile.getLevelName() != null ? profile.getLevelName() : "L1内部");
            result.put("linkedWorkstation", profile.getLinkedWorkstation() != null ? profile.getLinkedWorkstation() : "");
            result.put("auditMode", profile.getAuditMode() != null ? profile.getAuditMode() : "basic");
            result.put("sandboxMandatory", Boolean.TRUE.equals(profile.getSandboxMandatory()));
            if (profile.getUserId() != null) result.put("userId", profile.getUserId());
            if (profile.getRoleId() != null) result.put("roleId", profile.getRoleId());
            if (profile.getScopeType() != null) result.put("scopeType", profile.getScopeType());
            if (profile.getTenantId() != null) result.put("tenantId", profile.getTenantId());
            if (profile.getOrgId() != null) result.put("orgId", profile.getOrgId());
        } else {
            result.put("clearanceLevel", 1);
            result.put("level", 1);
            result.put("levelName", "L1内部");
            result.put("linkedWorkstation", "");
            result.put("auditMode", "basic");
            result.put("sandboxMandatory", false);
        }
        return result;
    }

    public void upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                  String scopeType, String tenantId, String orgId) {
        String upsertSql = """
                INSERT INTO td_user_security_profile
                    (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    clearance_level = COALESCE(?, td_user_security_profile.clearance_level),
                    linked_workstation = COALESCE(?, td_user_security_profile.linked_workstation),
                    audit_mode = COALESCE(?, td_user_security_profile.audit_mode),
                    sandbox_mandatory = COALESCE(?, td_user_security_profile.sandbox_mandatory),
                    is_default = COALESCE(?, td_user_security_profile.is_default),
                    scope_type = COALESCE(?, td_user_security_profile.scope_type),
                    tenant_id = COALESCE(?, td_user_security_profile.tenant_id),
                    org_id = COALESCE(?, td_user_security_profile.org_id),
                    updated_at = NOW()
                """;
        jdbc.update(upsertSql,
                userId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
    }

    public void upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                                  String auditMode, Boolean sandboxMandatory,
                                  String scopeType, String tenantId, String orgId) {
        String upsertSql = """
                INSERT INTO td_role_security_profile
                    (role_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (role_id) DO UPDATE SET
                    clearance_level = COALESCE(?, td_role_security_profile.clearance_level),
                    linked_workstation = COALESCE(?, td_role_security_profile.linked_workstation),
                    audit_mode = COALESCE(?, td_role_security_profile.audit_mode),
                    sandbox_mandatory = COALESCE(?, td_role_security_profile.sandbox_mandatory),
                    scope_type = COALESCE(?, td_role_security_profile.scope_type),
                    tenant_id = COALESCE(?, td_role_security_profile.tenant_id),
                    org_id = COALESCE(?, td_role_security_profile.org_id),
                    updated_at = NOW()
                """;
        jdbc.update(upsertSql,
                roleId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, scopeType, tenantId, orgId);
    }

    public void upsertProfile(String targetTable, String idColumn, String idValue,
                              Integer clearanceLevel, String linkedWorkstation,
                              String auditMode, Boolean sandboxMandatory, boolean isDefault,
                              String scopeType, String tenantId, String orgId) {
        String upsertSql = String.format("""
                INSERT INTO %s (%s, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (%s) DO UPDATE SET
                    clearance_level = COALESCE(?, %s.clearance_level),
                    linked_workstation = COALESCE(?, %s.linked_workstation),
                    audit_mode = COALESCE(?, %s.audit_mode),
                    sandbox_mandatory = COALESCE(?, %s.sandbox_mandatory),
                    scope_type = COALESCE(?, %s.scope_type),
                    tenant_id = COALESCE(?, %s.tenant_id),
                    org_id = COALESCE(?, %s.org_id),
                    updated_at = NOW()
                """,
                targetTable, idColumn, idColumn, targetTable, targetTable, targetTable, targetTable, targetTable, targetTable, targetTable);

        jdbc.update(upsertSql,
                idValue,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory ? true : null,
                scopeType, tenantId, orgId);
    }

    public void clearAllDefaults() {
        jdbc.update("UPDATE td_user_security_profile SET is_default = FALSE WHERE is_default = TRUE");
    }

    public void activateProfile(String id) {
        jdbc.update("UPDATE td_user_security_profile SET is_default = TRUE, updated_at = NOW() WHERE user_id = ?", id);
    }

    public int deleteUserProfile(String id) {
        return jdbc.update("DELETE FROM td_user_security_profile WHERE user_id = ?", id);
    }

    public int deleteRoleProfile(String id) {
        return jdbc.update("DELETE FROM td_role_security_profile WHERE role_id = ?", id);
    }

    public void updateProfileFields(String id, Integer clearanceLevel, String linkedWorkstation,
                                     String auditMode, Boolean sandboxMandatory, boolean isDefault) {
        String sql = """
                UPDATE td_user_security_profile SET
                    clearance_level = COALESCE(?, clearance_level),
                    linked_workstation = COALESCE(?, linked_workstation),
                    audit_mode = COALESCE(?, audit_mode),
                    sandbox_mandatory = COALESCE(?, sandbox_mandatory),
                    is_default = COALESCE(?, is_default),
                    updated_at = NOW()
                WHERE user_id = ?
                """;
        jdbc.update(sql, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, id);
    }

    public void cloneProfile(String sourceId, String newId) {
        SecurityProfile source = queryUserProfile(sourceId);
        if (source == null) {
            source = queryRoleProfile(sourceId);
        }
        if (source == null) {
            throw new IllegalArgumentException("源安全配置不存在: " + sourceId);
        }
        String sql = """
                INSERT INTO td_user_security_profile
                    (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())
                """;
        jdbc.update(sql, newId,
                source.getClearanceLevel(), source.getLinkedWorkstation(),
                source.getAuditMode(), source.getSandboxMandatory(),
                source.getScopeType(), source.getTenantId(), source.getOrgId());
    }

    public boolean ping() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String levelNameFor(int level) {
        return level >= 0 && level < LEVEL_NAMES.length ? LEVEL_NAMES[level] : ("L" + level);
    }
}
