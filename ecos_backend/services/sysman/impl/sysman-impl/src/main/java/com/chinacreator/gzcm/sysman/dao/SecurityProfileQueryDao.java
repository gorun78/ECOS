package com.chinacreator.gzcm.sysman.dao;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
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
}
