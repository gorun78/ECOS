package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * 安全配置（clearance/level 画像）DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p>表：{@code td_user_security_profile} / {@code td_role_security_profile}
 * （+ {@code td_user_role} JOIN）。tenant-aware 双分支 SQL 原样搬迁，
 * {@code tenantId} 由调用方解析后传入。两个 {@link RowMapper}
 * （{@link #userProfileMapper} / {@link #roleProfileMapper}）随 SQL 一起搬入本 DAO。</p>
 */
@Repository
public class SecurityConfigDao extends JdbcTemplateDaoSupport {

    private static final String USER_TABLE = "td_user_security_profile";
    private static final String ROLE_TABLE = "td_role_security_profile";

    private static final String[] LEVEL_NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    static RowMapper<SecurityProfile> userProfileMapper = (rs, rowNum) -> {
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

    static RowMapper<SecurityProfile> roleProfileMapper = (rs, rowNum) -> {
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

    private static String levelNameFor(int level) {
        return level >= 0 && level < LEVEL_NAMES.length ? LEVEL_NAMES[level] : ("L" + level);
    }

    // ── 查询（tenant-aware 双分支，tenantId=null 走无租户分支）──

    public List<SecurityProfile> queryUserProfile(String userId, String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + USER_TABLE + " WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                    userProfileMapper, userId, tenantId);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + USER_TABLE + " WHERE user_id = ? LIMIT 1",
                userProfileMapper, userId);
    }

    public List<SecurityProfile> queryRoleProfile(String roleId, String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + ROLE_TABLE + " WHERE role_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                    roleProfileMapper, roleId, tenantId);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + ROLE_TABLE + " WHERE role_id = ? LIMIT 1",
                roleProfileMapper, roleId);
    }

    public List<SecurityProfile> queryHighestRoleProfileForUser(String userId, String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query("""
                        SELECT rsp.* FROM td_role_security_profile rsp
                        JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ? AND (rsp.tenant_id = ? OR rsp.tenant_id IS NULL)
                        ORDER BY rsp.clearance_level DESC
                        LIMIT 1
                        """,
                    roleProfileMapper, userId, tenantId);
        }
        return jdbcTemplate.query("""
                        SELECT rsp.* FROM td_role_security_profile rsp
                        JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ?
                        ORDER BY rsp.clearance_level DESC
                        LIMIT 1
                        """,
                roleProfileMapper, userId);
    }

    public List<SecurityProfile> queryGlobalDefaultProfile(String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + USER_TABLE + " WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                    userProfileMapper, tenantId);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + USER_TABLE + " WHERE is_default = TRUE LIMIT 1",
                userProfileMapper);
    }

    public List<SecurityProfile> queryAllUserProfiles(String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + USER_TABLE + " WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY updated_at DESC",
                    userProfileMapper, tenantId);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + USER_TABLE + " ORDER BY updated_at DESC",
                userProfileMapper);
    }

    public List<SecurityProfile> queryAllRoleProfiles(String tenantId) {
        if (tenantId != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM " + ROLE_TABLE + " WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY clearance_level DESC",
                    roleProfileMapper, tenantId);
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + ROLE_TABLE + " ORDER BY clearance_level DESC",
                roleProfileMapper);
    }

    public List<SecurityProfile> queryProfileByScope(String scopeType, String scopeId) {
        String sql;
        switch (scopeType) {
            case "TENANT":
                sql = "SELECT * FROM " + USER_TABLE + " WHERE tenant_id = ? AND scope_type = 'TENANT' LIMIT 1";
                break;
            case "ORG":
                sql = "SELECT * FROM " + USER_TABLE + " WHERE org_id = ? AND scope_type = 'ORG' LIMIT 1";
                break;
            default:
                return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query(sql, userProfileMapper, scopeId);
    }

    public List<SecurityProfile> queryProfilesByScope(String scopeType, String scopeId) {
        if (scopeId != null && !scopeId.isBlank()) {
            switch (scopeType.toUpperCase()) {
                case "TENANT": {
                    List<SecurityProfile> userProfiles = jdbcTemplate.query(
                            "SELECT * FROM " + USER_TABLE + " WHERE tenant_id = ? ORDER BY updated_at DESC",
                            userProfileMapper, scopeId);
                    List<SecurityProfile> roleProfiles = jdbcTemplate.query(
                            "SELECT * FROM " + ROLE_TABLE + " WHERE tenant_id = ? ORDER BY updated_at DESC",
                            roleProfileMapper, scopeId);
                    List<SecurityProfile> all = new java.util.ArrayList<>(userProfiles);
                    all.addAll(roleProfiles);
                    return all;
                }
                case "ORG":
                    return jdbcTemplate.query(
                            "SELECT * FROM " + USER_TABLE + " WHERE org_id = ? ORDER BY updated_at DESC",
                            userProfileMapper, scopeId);
                default:
                    return jdbcTemplate.query(
                            "SELECT * FROM " + USER_TABLE + " WHERE scope_type = ? ORDER BY updated_at DESC",
                            userProfileMapper, scopeType.toUpperCase());
            }
        }
        return jdbcTemplate.query(
                "SELECT * FROM " + USER_TABLE + " WHERE scope_type = ? ORDER BY updated_at DESC",
                userProfileMapper, scopeType.toUpperCase());
    }

    // ── 写 ──

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
        jdbcTemplate.update(upsertSql,
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
        jdbcTemplate.update(upsertSql,
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

        jdbcTemplate.update(upsertSql,
                idValue,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory ? true : null,
                scopeType, tenantId, orgId);
    }

    public void clearAllDefaults() {
        jdbcTemplate.update("UPDATE " + USER_TABLE + " SET is_default = FALSE WHERE is_default = TRUE");
    }

    public void activateProfile(String id) {
        jdbcTemplate.update("UPDATE " + USER_TABLE + " SET is_default = TRUE, updated_at = NOW() WHERE user_id = ?", id);
    }

    public int deleteUserProfile(String id) {
        return jdbcTemplate.update("DELETE FROM " + USER_TABLE + " WHERE user_id = ?", id);
    }

    public int deleteRoleProfile(String id) {
        return jdbcTemplate.update("DELETE FROM " + ROLE_TABLE + " WHERE role_id = ?", id);
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
        jdbcTemplate.update(sql, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, id);
    }

    public void cloneProfile(String newId, Integer clearanceLevel, String linkedWorkstation,
                             String auditMode, Boolean sandboxMandatory,
                             String scopeType, String tenantId, String orgId) {
        String sql = """
                INSERT INTO td_user_security_profile
                    (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())
                """;
        jdbcTemplate.update(sql, newId,
                clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory,
                scopeType, tenantId, orgId);
    }

    /** 连接自检（SELECT 1）。 */
    public boolean ping() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
