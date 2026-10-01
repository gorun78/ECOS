package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 准入等级拦截器数据访问 DAO — 承接 ClearanceInterceptor 中的 JdbcTemplate 操作。
 * <p>铁律 §2.5：拦截器不直用 JdbcTemplate，统一经 DAO。
 * <p>注意：{@code queryForObject} 在无结果行时会抛 {@code EmptyResultDataAccessException}，
 * 由拦截器捕获后按缓存回退。此为既有 fail-soft 语义的一部分，保持 DAO 不吞异常。
 */
@Repository
public class ClearanceLookupDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 按 userId 回查其所属机构（详情设计-01 W40：机构只从服务端解析）。 */
    public List<Map<String, Object>> findOrgById(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT \"ORG_ID\" FROM TD_USER WHERE \"ID\" = ?", userId);
    }

    /** 按行表 + id 列回查配置（含租户过滤缓存）。 */
    public List<Map<String, Object>> queryClearanceRows(String tableName, String idColumn, String idValue, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = String.format(
                    "SELECT clearance_level FROM %s WHERE %s = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                    tableName, idColumn);
            params = new Object[]{idValue, tenantId};
        } else {
            sql = String.format(
                    "SELECT clearance_level FROM %s WHERE %s = ? LIMIT 1",
                    tableName, idColumn);
            params = new Object[]{idValue};
        }
        return jdbcTemplate.queryForList(sql, params);
    }

    /** 查询该用户最大一级 clearance_level（可能为负，表示无配置）。 */
    public Integer highestRoleClearanceLevel(String userId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = """
                    SELECT COALESCE(MAX(rsp.clearance_level), -1) AS max_level
                    FROM td_user_role ur
                    JOIN td_role_security_profile rsp ON ur.ROLE_ID = rsp.role_id
                    WHERE ur.USER_ID = ? AND (rsp.tenant_id = ? OR rsp.tenant_id IS NULL)
                    """;
            params = new Object[]{userId, tenantId};
        } else {
            sql = """
                    SELECT COALESCE(MAX(rsp.clearance_level), -1) AS max_level
                    FROM td_user_role ur
                    JOIN td_role_security_profile rsp ON ur.ROLE_ID = rsp.role_id
                    WHERE ur.USER_ID = ?
                    """;
            params = new Object[]{userId};
        }
        return jdbcTemplate.queryForObject(sql, Integer.class, params);
    }

    public Integer globalDefaultClearanceLevel(String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
            params = new Object[]{tenantId};
        } else {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
            params = new Object[]{};
        }
        return jdbcTemplate.queryForObject(sql, Integer.class, params);
    }

    public Integer orgClearanceLevel(String orgId, String tenantId) {
        String sql;
        Object[] params;
        if (tenantId != null) {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' AND (tenant_id = ? OR tenant_id IS NULL) ORDER BY clearance_level DESC LIMIT 1";
            params = new Object[]{orgId, tenantId};
        } else {
            sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' ORDER BY clearance_level DESC LIMIT 1";
            params = new Object[]{orgId};
        }
        return jdbcTemplate.queryForObject(sql, Integer.class, params);
    }

    public Integer tenantClearanceLevel(String tenantId) {
        String sql = "SELECT clearance_level FROM td_user_security_profile WHERE tenant_id = ? AND scope_type = 'TENANT' ORDER BY clearance_level DESC LIMIT 1";
        return jdbcTemplate.queryForObject(sql, Integer.class, tenantId);
    }
}
