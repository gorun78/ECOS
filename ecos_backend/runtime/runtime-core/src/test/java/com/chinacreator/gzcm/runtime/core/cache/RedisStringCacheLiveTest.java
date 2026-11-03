package com.chinacreator.gzcm.runtime.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 远程通道<b>活体</b>集成腿（§2.9.8；#147AF/D）。
 *
 * <p><b>为什么带标志</b>：本腿真连 {@code 127.0.0.1:6379}（容器 {@code ecos-redis}）。
 * 无 Redis 的机器上默认运行必须跳过，否则构建被环境绑死；但「跳过」不等于「已验」——
 * 台账与文档一律按 #147W 口径记**未验**，只有带 {@code -Decos.verify.redis=true} 实跑过才算证据。
 *
 * <p>覆盖：PING 可用性、SET/GET、前缀 SCAN 删除、前缀计数（失效面）。
 * 键前缀带随机后缀，避免污染开发缓存。
 *
 * @author ECOS Runtime #147AF
 */
@EnabledIfSystemProperty(named = "ecos.verify.redis", matches = "true")
class RedisStringCacheLiveTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private static RedisStringCache cache;
    private static String prefix;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        cache = new RedisStringCache(new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public StringRedisTemplate getObject(Object... args) {
                return template;
            }

            @Override
            public StringRedisTemplate getObject() {
                return template;
            }

            @Override
            public StringRedisTemplate getIfAvailable() {
                return template;
            }

            @Override
            public StringRedisTemplate getIfUnique() {
                return template;
            }
        });
        prefix = "ecos:live-test:" + Long.toHexString(System.nanoTime()) + ":";
    }

    @AfterAll
    static void cleanup() {
        if (cache != null) {
            cache.evictPrefix(prefix);
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("PING 可用 + SET/GET + TTL 生效 + 前缀失效与计数")
    void roundTripAgainstLiveRedis() {
        assertTrue(cache.available(), "ecos-redis 容器应可达（redis-cli ping = PONG）");

        String k1 = prefix + "a";
        String k2 = prefix + "b";
        cache.put(k1, "v1", 30);
        cache.put(k2, "v2", 30);
        assertEquals(Optional.of("v1"), cache.get(k1));
        assertEquals(2L, cache.countPrefix(prefix));

        assertEquals(2, cache.evictPrefix(prefix));
        assertEquals(Optional.empty(), cache.get(k1));
        assertEquals(0L, cache.countPrefix(prefix));
    }

    @Test
    @DisplayName("TTL 到点即失效（短 TTL 实测，不依赖 sleep 假绿以外的技巧）")
    void expiryDropsValue() throws InterruptedException {
        String k = prefix + "ttl";
        cache.put(k, "v", 1);
        assertEquals(Optional.of("v"), cache.get(k));
        Thread.sleep(Duration.ofMillis(1200).toMillis());
        assertEquals(Optional.empty(), cache.get(k), "1 秒 TTL 到期后必须读空");
    }
}
