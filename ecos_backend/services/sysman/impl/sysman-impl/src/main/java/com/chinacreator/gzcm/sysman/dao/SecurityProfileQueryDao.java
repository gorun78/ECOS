package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;
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

    private static final String[] LEVEL_NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    private final RowMapper<SecurityProfile> USER_PROFILE_MAPPER = (rs, rowNum) -> {
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

    private final RowMapper<SecurityProfile> ROLE_PROFILE_MAPPER = (rs, rowNum) -> {
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

    public int update(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
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
