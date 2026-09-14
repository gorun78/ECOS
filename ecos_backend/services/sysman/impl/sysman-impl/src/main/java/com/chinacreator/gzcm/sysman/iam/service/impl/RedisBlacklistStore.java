package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Token 黑名单 Redis 持久化实现（enterprise/ultimate 版）
 * <p>
 * Caffeine 本地一级 + Redis 分布式二级，多实例共享。
 * Redis 不可达时降级为仅 Caffeine（不影响 Gateway 启动）。
 * </p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class RedisBlacklistStore implements BlacklistStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBlacklistStore.class);

    private static final String KEY_PREFIX = "token:blacklist:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public void save(String token, long expireAt) {
        if (redisTemplate == null) {
            return;
        }
        try {
            long ttlSeconds = Math.max(1, (expireAt - System.currentTimeMillis()) / 1000);
            redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(expireAt), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("RedisBlacklistStore save 失败，降级为纯 Caffeine: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Long> load() {
        Map<String, Long> result = new HashMap<>();
        if (redisTemplate == null) {
            return result;
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        String token = key.substring(KEY_PREFIX.length());
                        long expireAt = Long.parseLong(value);
                        if (expireAt > System.currentTimeMillis()) {
                            result.put(token, expireAt);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("RedisBlacklistStore load 失败，启动时黑名单为空: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void remove(String token) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + token);
        } catch (Exception e) {
            log.warn("RedisBlacklistStore remove 失败: {}", e.getMessage());
        }
    }
}
