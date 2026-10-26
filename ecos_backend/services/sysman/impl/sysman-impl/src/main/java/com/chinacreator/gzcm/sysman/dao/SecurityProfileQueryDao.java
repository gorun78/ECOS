package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.clearance.model.ClearanceLevel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * SecurityProfile 查询 DAO — 承接 SecurityProfileQueryService 中的 JdbcTemplate 操作（铁律 §2.5）。
 * <p>SQL 与行映射自 SecurityProfileController 原样下沉，字节级等价。
 */
@Repository
public class SecurityProfileQueryDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {


    private final RowMapper<SecurityProfile> USER_PROFILE_MAPPER = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setUserId(rs.getString("user_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(ClearanceLevel.nameOf(rs.getInt("clearance_level")));
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

    private final RowMapper<SecurityProfile> ROLE_PROFILE_MAPPER = (rs, rowNum) -> {
        SecurityProfile p = new SecurityProfile();
        p.setRoleId(rs.getString("role_id"));
        p.setClearanceLevel(rs.getInt("clearance_level"));
        p.setLevel(rs.getInt("clearance_level"));
        p.setLevelName(ClearanceLevel.nameOf(rs.getInt("clearance_level")));
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


    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    // ──────────────────────────────────────────────────────────────────
    // 写面（#142/B4-3e-87，收口 D-B4-101）—— 属主单源
    // ──────────────────────────────────────────────────────────────────
    //
    // 九腿写语句在此单源；消费方两条：
    //   ① 属主 {@code SecurityProfileController}（三枚路由 UPSERT，现改经本 DAO）；
    //   ② 安全引擎 {@code SecurityConfigService}（经属主 api 门面，不写本表语句）。
    //
    // 语义裁决（DDL 实证 = V24 + V41）：
    //   - td_role_security_profile 无 is_default 列 ⇒ 角色腿不含该列；
    //   - 通用 UPSERT（coalescing）ON CONFLICT 不碰 is_default（属主既有语义）；
    //   - 用户专属 UPSERT ON CONFLICT 不收 is_default（属主 PUT /user/{userId} 既有语义）；
    //   - 默认档切换只走 clearAllDefaults / activateDefault 两腿；
    //   - 克隆腿固定 is_default=FALSE。

    public int upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                 String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                 String scopeType, String tenantId, String orgId) {
        String sql = """
                INSERT INTO td_user_security_profile
                    (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    clearance_level = COALESCE(?, td_user_security_profile.clearance_level),
                    linked_workstation = COALESCE(?, td_user_security_profile.linked_workstation),
                    audit_mode = COALESCE(?, td_user_security_profile.audit_mode),
                    sandbox_mandatory = COALESCE(?, td_user_security_profile.sandbox_mandatory),
                    scope_type = COALESCE(?, td_user_security_profile.scope_type),
                    tenant_id = COALESCE(?, td_user_security_profile.tenant_id),
                    org_id = COALESCE(?, td_user_security_profile.org_id),
                    updated_at = NOW()
                """;
        return jdbcTemplate.update(sql,
                userId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                scopeType, tenantId, orgId);
    }

    public int upsertRoleProfile(String roleId, Integer clearanceLevel, String linkedWorkstation,
                                 String auditMode, Boolean sandboxMandatory,
                                 String scopeType, String tenantId, String orgId) {
        String sql = """
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
        return jdbcTemplate.update(sql,
                roleId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, scopeType, tenantId, orgId,
                clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory,
                scopeType, tenantId, orgId);
    }

    /**
     * 按具名目标档 UPSERT（属主单源，替代原引擎面 {@code String.format} 拼标识符腿）。
     *
     * <p>目标档闭合枚举：{@code USER} → {@code td_user_security_profile}/user_id
     * （含 {@code is_default} INSERT 参数）；{@code ROLE} → {@code td_role_security_profile}/
     * role_id（该表无 {@code is_default} 列）。非白名单值（含 {@code null}）抛
     * {@link IllegalArgumentException}——拼不出语句，也不接受调用方递来的任意标识符。</p>
     */
    public int upsertCoalescing(String profileTarget, String idValue, Integer clearanceLevel,
                                String linkedWorkstation, String auditMode, Boolean sandboxMandatory,
                                boolean isDefault, String scopeType, String tenantId, String orgId) {
        if ("USER".equalsIgnoreCase(profileTarget)) {
            return upsertUserProfile(idValue, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
        }
        if ("ROLE".equalsIgnoreCase(profileTarget)) {
            return upsertRoleProfile(idValue, clearanceLevel, linkedWorkstation,
                    auditMode, sandboxMandatory, scopeType, tenantId, orgId);
        }
        throw new IllegalArgumentException("未知档案目标档: " + profileTarget
                + "（只允许 USER / ROLE，拒拼任意标识符）");
    }

    public int clearAllDefaults() {
        return jdbcTemplate.update(
                "UPDATE td_user_security_profile SET is_default = FALSE WHERE is_default = TRUE");
    }

    public int activateDefault(String userId) {
        return jdbcTemplate.update(
                "UPDATE td_user_security_profile SET is_default = TRUE, updated_at = NOW() WHERE user_id = ?",
                userId);
    }

    public int deleteUserProfile(String userId) {
        return jdbcTemplate.update(
                "DELETE FROM td_user_security_profile WHERE user_id = ?", userId);
    }

    public int deleteRoleProfile(String roleId) {
        return jdbcTemplate.update(
                "DELETE FROM td_role_security_profile WHERE role_id = ?", roleId);
    }

    public int updateProfileFields(String userId, Integer clearanceLevel, String linkedWorkstation,
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
        return jdbcTemplate.update(sql, clearanceLevel, linkedWorkstation, auditMode,
                sandboxMandatory, isDefault, userId);
    }

    public int cloneUserProfile(String newUserId, Integer clearanceLevel, String linkedWorkstation,
                                String auditMode, Boolean sandboxMandatory,
                                String scopeType, String tenantId, String orgId) {
        String sql = """
                INSERT INTO td_user_security_profile
                    (user_id, clearance_level, linked_workstation, audit_mode, sandbox_mandatory, is_default, scope_type, tenant_id, org_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())
                """;
        return jdbcTemplate.update(sql, newUserId,
                clearanceLevel, linkedWorkstation,
                auditMode, sandboxMandatory,
                scopeType, tenantId, orgId);
    }

    public SecurityProfile findUser(String userId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
            params = new Object[]{userId, tenantId};
        } else {
            sql = "SELECT * FROM td_user_security_profile WHERE user_id = ? LIMIT 1";
            params = new Object[]{userId};
        }
        List<SecurityProfile> list = jdbcTemplate.query(sql, USER_PROFILE_MAPPER, params);
        return list.isEmpty() ? null : list.get(0);
    }

    public SecurityProfile findRole(String roleId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
            params = new Object[]{roleId, tenantId};
        } else {
            sql = "SELECT * FROM td_role_security_profile WHERE role_id = ? LIMIT 1";
            params = new Object[]{roleId};
        }
        List<SecurityProfile> list = jdbcTemplate.query(sql, ROLE_PROFILE_MAPPER, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 「取用户最高角色档」的<b>属主单源语句</b>（#142/B4-3e-83，收口 D-B4-99 d)）。
     *
     * <p>全仓只有这一处写 {@code td_user_role} 的最高档行读：sysman 密级五档链（经
     * {@code ClearanceLookupDao} 委派）与安全引擎（经 {@code ISecurityProfileQueryService} 门面）
     * 都走这里。收口前引擎侧有一份逐字副本、密级链另有一条
     * {@code COALESCE(MAX(rsp.clearance_level), -1)} 聚合腿，同一主体可拿到不同档位。</p>
     *
     * <p><b>为什么必须显式排除空值档</b>：{@code clearance_level} 在 DDL 上可空
     * （{@code 02_ecos_security.sql}：{@code clearance_level INT DEFAULT 0}，显式写 NULL 即落空），
     * 而 PG 的 {@code ORDER BY … DESC} 默认 <b>NULLS FIRST</b>；不排除的话「最高档」会取到那条
     * 空密级行（{@code rs.getInt} 读成 {@code 0}＝L0 公开），与聚合腿 {@code MAX()} 忽略 NULL 的
     * 口径相反。加了本谓词后，「最高档」只有一个含义：<b>非空密级里的最大值</b>；
     * 没有任何非空档 ⇒ {@code null}（未解析，禁兜底成任何档位）。</p>
     */
    public SecurityProfile findHighestRoleForUser(String userId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = """
                    SELECT rsp.* FROM td_role_security_profile rsp
                    JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                    WHERE ur.USER_ID = ? AND (rsp.tenant_id = ? OR rsp.tenant_id IS NULL)
                      AND rsp.clearance_level IS NOT NULL
                    ORDER BY rsp.clearance_level DESC
                    LIMIT 1
                    """;
            params = new Object[]{userId, tenantId};
        } else {
            sql = """
                    SELECT rsp.* FROM td_role_security_profile rsp
                    JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id
                    WHERE ur.USER_ID = ? AND rsp.clearance_level IS NOT NULL
                    ORDER BY rsp.clearance_level DESC
                    LIMIT 1
                    """;
            params = new Object[]{userId};
        }
        List<SecurityProfile> list = jdbcTemplate.query(sql, ROLE_PROFILE_MAPPER, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 「用户是否挂指定角色码」的<b>属主单源语句</b>（#142/B4-3e-86，收口 D-B4-103）。
     *
     * <p>收口前安全引擎 {@code SecuritySandboxDao.countSuperAdminRole} 在两枚
     * {@code *_security_profile} 表上 JOIN {@code profile_id}＋按 {@code role_name} 过滤——
     * 两表主键分别是 {@code user_id}/{@code role_id}、<b>均无 profile_id 与 role_name 列</b>，
     * 故该语句恒 PG error 被调用方 catch 折 0，超管裁决<b>永假</b>。本语句走真实隶属链
     * {@code td_user_role → td_role}（真列 {@code role_code}/{@code role_name}）；
     * 两列带内命中是因为平台超管角色在两列上同文落库（本机语义单源）。</p>
     */
    public boolean hasRoleCode(String userId, String roleCode) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException(
                    "IAM 绑定库连接未装配（td_user_role/td_role 所在库 JdbcTemplate 缺失）：hasRoleCode 不可用");
        }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM td_user_role ur "
                        + "JOIN td_role r ON ur.role_id = r.role_id "
                        + "WHERE ur.user_id = ? AND (r.role_code = ? OR r.role_name = ?)",
                Integer.class, userId, roleCode, roleCode);
        return n != null && n > 0;
    }

    public SecurityProfile findGlobalDefault(String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
            params = new Object[]{tenantId};
        } else {
            sql = "SELECT * FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
            params = new Object[]{};
        }
        List<SecurityProfile> list = jdbcTemplate.query(sql, USER_PROFILE_MAPPER, params);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SecurityProfile> listAllUser(String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT * FROM td_user_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY updated_at DESC";
            params = new Object[]{tenantId};
        } else {
            sql = "SELECT * FROM td_user_security_profile ORDER BY updated_at DESC";
            params = new Object[]{};
        }
        return jdbcTemplate.query(sql, USER_PROFILE_MAPPER, params);
    }

    public List<SecurityProfile> listAllRole(String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT * FROM td_role_security_profile WHERE tenant_id = ? OR tenant_id IS NULL ORDER BY clearance_level DESC";
            params = new Object[]{tenantId};
        } else {
            sql = "SELECT * FROM td_role_security_profile ORDER BY clearance_level DESC";
            params = new Object[]{};
        }
        return jdbcTemplate.query(sql, ROLE_PROFILE_MAPPER, params);
    }

    public SecurityProfile findUserByScope(String scopeType, String scopeId) {
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
                return null;
        }
        List<SecurityProfile> list = jdbcTemplate.query(sql, USER_PROFILE_MAPPER, params);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按作用域列举档案的<b>属主单源语句</b>（#142/B4-3e-84，收口 D-B4-100）。
     *
     * <p>语义自安全引擎 {@code SecurityConfigDao#queryProfilesByScope} 原样迁入：
     * {@code TENANT}＝该租户用户档与角色档合并；{@code ORG}＝该机构用户档；
     * 其余（含 {@code scopeId} 空白）＝按 {@code scope_type} 匹配用户档，均按
     * {@code updated_at DESC}。这是跨租户管理面列举，不叠加租户过滤。</p>
     */
    public List<SecurityProfile> listProfilesByScope(String scopeType, String scopeId) {
        if (scopeId != null && !scopeId.isBlank()) {
            switch (scopeType.toUpperCase()) {
                case "TENANT": {
                    List<SecurityProfile> all = new ArrayList<>(jdbcTemplate.query(
                            "SELECT * FROM td_user_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC",
                            USER_PROFILE_MAPPER, scopeId));
                    all.addAll(jdbcTemplate.query(
                            "SELECT * FROM td_role_security_profile WHERE tenant_id = ? ORDER BY updated_at DESC",
                            ROLE_PROFILE_MAPPER, scopeId));
                    return all;
                }
                case "ORG":
                    return jdbcTemplate.query(
                            "SELECT * FROM td_user_security_profile WHERE org_id = ? ORDER BY updated_at DESC",
                            USER_PROFILE_MAPPER, scopeId);
                default:
                    return jdbcTemplate.query(
                            "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC",
                            USER_PROFILE_MAPPER, scopeType.toUpperCase());
            }
        }
        return jdbcTemplate.query(
                "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC",
                USER_PROFILE_MAPPER, scopeType.toUpperCase());
    }
}
