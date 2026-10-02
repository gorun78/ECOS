package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器数据访问 DAO — 承接 JwtAuthenticationFilter 中的 JdbcTemplate 操作。
 * <p>铁律 §2.5：过滤器不直用 JdbcTemplate，统一经 DAO。
 */
@Repository
public class AuthLookupDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 从 TD_USER_ROLE + TD_ROLE_PERMISSION + TD_PERMISSION 查询用户权限码 */
    private static final String PERMISSION_QUERY =
        "SELECT DISTINCT p.permission_code " +
        "FROM td_user_role ur " +
        "JOIN td_role_permission rp ON ur.role_id = rp.role_id " +
        "JOIN td_permission p ON rp.permission_id = p.permission_id " +
        "WHERE ur.user_id = ?";

    /** 检查 token 是否在黑名单中（已被强制踢出）。键为完整 token 串（sys_token_blacklist.token）。 */
    public boolean isTokenBlacklisted(String token) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_token_blacklist WHERE token = ? AND expire_at > ?",
            Integer.class, token, System.currentTimeMillis());
        return count != null && count > 0;
    }

    /** 加载用户权限码列表。 */
    public List<String> loadUserPermissions(String userId) {
        return jdbcTemplate.queryForList(PERMISSION_QUERY, String.class, userId);
    }

    /** 按 userId 回查用户名。 */
    public List<String> loadUsernames(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT username FROM td_user WHERE user_id = ?", String.class, userId);
    }

    /** 按 userId 回查租户ID。 */
    public List<Map<String, Object>> findTenantById(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT tenant_id FROM td_user WHERE user_id = ?", userId);
    }
}
