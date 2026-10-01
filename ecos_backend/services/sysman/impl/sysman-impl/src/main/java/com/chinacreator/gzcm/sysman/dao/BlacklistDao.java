package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 令牌黑名单持久化 DAO — 承接 DbBlacklistStore 中的 JdbcTemplate 操作。
 * <p>铁律 §2.5：Store 不直用 JdbcTemplate，统一经 DAO。
 */
@Repository
public class BlacklistDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    public static final String INSERT_SQL =
        "INSERT INTO sys_token_blacklist (token, expire_at) VALUES (?, ?) " +
        "ON CONFLICT (token) DO UPDATE SET expire_at = EXCLUDED.expire_at";

    public static final String SELECT_SQL =
        "SELECT token, expire_at FROM sys_token_blacklist WHERE expire_at > ?";

    public static final String DELETE_SQL =
        "DELETE FROM sys_token_blacklist WHERE token = ?";

    public static final String CLEAN_EXPIRED_SQL =
        "DELETE FROM sys_token_blacklist WHERE expire_at <= ?";

    /** 清理过期项（初始化用）。 */
    public int cleanExpired(long threshold) {
        return jdbcTemplate.update(CLEAN_EXPIRED_SQL, threshold);
    }

    /** 保存一个 token 黑名单条目。 */
    public void save(String token, long expireAt) {
        jdbcTemplate.update(INSERT_SQL, token, expireAt);
    }

    /** 加载未过期的 token 集合。 */
    public List<Map<String, Object>> loadUnexpired(long now) {
        return jdbcTemplate.queryForList(SELECT_SQL, now);
    }

    /** 移除单个 token。 */
    public void remove(String token) {
        jdbcTemplate.update(DELETE_SQL, token);
    }
}
