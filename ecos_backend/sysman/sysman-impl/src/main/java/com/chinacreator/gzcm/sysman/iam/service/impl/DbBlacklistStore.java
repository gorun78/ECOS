package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Token 黑名单 PG 持久化实现（standard 版，PG-only）
 * <p>
 * 黑名单落 sys_token_blacklist 表，启动时加载未过期项回填 Caffeine。
 * </p>
 */
@Component
@ConditionalOnMissingClass("org.springframework.data.redis.core.RedisTemplate")
public class DbBlacklistStore implements BlacklistStore {

    private static final Logger log = LoggerFactory.getLogger(DbBlacklistStore.class);

    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS sys_token_blacklist (" +
        "  token TEXT PRIMARY KEY," +
        "  expire_at BIGINT NOT NULL" +
        ")";

    private static final String INSERT_SQL =
        "INSERT INTO sys_token_blacklist (token, expire_at) VALUES (?, ?) " +
        "ON CONFLICT (token) DO UPDATE SET expire_at = EXCLUDED.expire_at";

    private static final String SELECT_SQL =
        "SELECT token, expire_at FROM sys_token_blacklist WHERE expire_at > ?";

    private static final String DELETE_SQL =
        "DELETE FROM sys_token_blacklist WHERE token = ?";

    private static final String CLEAN_EXPIRED_SQL =
        "DELETE FROM sys_token_blacklist WHERE expire_at <= ?";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            // 清理已过期项
            jdbcTemplate.update(CLEAN_EXPIRED_SQL, System.currentTimeMillis());
            log.info("DbBlacklistStore 初始化完成，sys_token_blacklist 表已就绪");
        } catch (Exception e) {
            log.warn("DbBlacklistStore 初始化失败（表创建/清理），降级为纯内存模式: {}", e.getMessage());
        }
    }

    @Override
    public void save(String token, long expireAt) {
        try {
            jdbcTemplate.update(INSERT_SQL, token, expireAt);
        } catch (Exception e) {
            log.warn("DbBlacklistStore save 失败，降级为纯内存: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Long> load() {
        Map<String, Long> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(SELECT_SQL, System.currentTimeMillis());
            for (Map<String, Object> row : rows) {
                String token = (String) row.get("token");
                Long expireAt = ((Number) row.get("expire_at")).longValue();
                result.put(token, expireAt);
            }
        } catch (Exception e) {
            log.warn("DbBlacklistStore load 失败，启动时黑名单为空: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void remove(String token) {
        try {
            jdbcTemplate.update(DELETE_SQL, token);
        } catch (Exception e) {
            log.warn("DbBlacklistStore remove 失败: {}", e.getMessage());
        }
    }
}
