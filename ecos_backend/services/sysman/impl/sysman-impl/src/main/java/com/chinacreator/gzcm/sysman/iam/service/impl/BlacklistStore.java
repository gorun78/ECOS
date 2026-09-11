package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.Map;

/**
 * Token 黑名单持久化存储接口（B1: Caffeine + 分版本持久化）
 * <p>
 * standard 版用 DbBlacklistStore（PG 持久化），enterprise/ultimate 版用 RedisBlacklistStore（Redis TTL）。
 * </p>
 */
public interface BlacklistStore {

    /**
     * 保存一个黑名单 token
     *
     * @param token       JWT token
     * @param expireAt    过期时间戳（毫秒）
     */
    void save(String token, long expireAt);

    /**
     * 加载所有未过期的黑名单 token
     *
     * @return token → expireAt 映射
     */
    Map<String, Long> load();

    /**
     * 移除一个黑名单 token
     *
     * @param token JWT token
     */
    void remove(String token);
}
