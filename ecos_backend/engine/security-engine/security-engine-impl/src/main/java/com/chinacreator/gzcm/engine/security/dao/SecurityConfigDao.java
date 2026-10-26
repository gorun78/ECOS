package com.chinacreator.gzcm.engine.security.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 安全档案<b>写面</b> DAO（R2-B3b 数据访问收口；只搬不改语义）。
 *
 * <p><b>读面已全部迁出本类</b>：{@code td_user_security_profile} / {@code td_role_security_profile}
 * 是 sysman 属主表（铁律 #5），本类原先另存了七组同形查询语句（用户档、角色档、全局默认档、
 * 全量用户档、全量角色档、按作用域取档／列档），与 sysman {@code SecurityProfileQueryDao} 两存。
 * 现已逐批改为只经属主 api 门面 {@code ISecurityProfileQueryService}：
 * 「取最高角色档」见 #142/B4-3e-83（D-B4-99 d)），其余六组见 #142/B4-3e-84（D-B4-100）。</p>
 *
 * <p><b>写面仍在册为 D-B4-101</b>（本批不扩面）：{@code upsert*} / {@code delete*} /
 * {@code updateProfileFields} / {@code cloneProfile} / {@code activateProfile} /
 * {@code clearAllDefaults} 仍是引擎侧直写属主表，且 {@link #upsertProfile} 以
 * {@code String.format} 拼表名与列名。这些语句与 sysman 属主写面同样两存，改法须逐条对
 * 白名单与迁移期行为（含 {@code ON CONFLICT} 上的 {@code is_default}／{@code sandbox_mandatory}
 * 语义差），另批处置。</p>
 */
@Repository
public class SecurityConfigDao extends JdbcTemplateDaoSupport {

    private static final String USER_TABLE = "td_user_security_profile";
    private static final String ROLE_TABLE = "td_role_security_profile";

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
