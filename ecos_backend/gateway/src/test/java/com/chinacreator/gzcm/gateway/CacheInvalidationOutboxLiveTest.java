package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.runtime.core.cache.CacheInvalidationNotifier;
import com.chinacreator.gzcm.runtime.core.cache.CacheKey;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RedisStringCache;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.runtime.core.cache.outbox.CacheInvalidationOutboxDao;
import com.chinacreator.gzcm.runtime.core.cache.outbox.CacheInvalidationRelay;

/**
 * 缓存失效链路<b>活体</b>集成腿（§2.9.8④；#147AN）。
 *
 * <p><b>它证明什么</b>：真连开发库 PG 与 {@code ecos-redis}，把手装的
 * {@code RuntimeCacheManager → CacheInvalidationNotifier → Outbox 表 → 中继 → Redis 频道 →
 * 订阅容器} 整条链跑通——即「写路径失效 ⇒ 同库落一行 ⇒ 中继置 PUBLISHED ⇒ 另一节点真的收到」。
 * 落库、发布、订阅这三件事在单测里只能拿假面验证调用形状，真做过只能靠本腿。</p>
 *
 * <p><b>为什么带标志</b>：与 {@code RedisStringCacheLiveTest} 同口径——没有 PG/Redis 的机器默认跳过。
 * 「跳过」不等于「已验」，台账按 #147W 口径记**未验**；只有带 {@code -Decos.verify.live=true} 实跑过才算证据。</p>
 *
 * <p><b>它不证明什么（如实）</b>：这里的失效入口是直调 {@code evictObject}，不是经 HTTP 写面
 * （如 {@code PUT /users/{id}/roles}）触发；HTTP 那一腿要 gateway 上下文起来才能跑。</p>
 *
 * <p>两个 manager 实例都装了投递口（与生产一致：订阅端所在节点的缓存管理器同样有这个 Bean），
 * 所以「广播处理不再入队」这条断言是在真实装配形状上做的，不是靠缺席蒙过的。
 * 测试行先按登记 id 逐条删，再按本轮唯一 nonce 兜底清扫（断言中途失败时 id 可能没登记上）；
 * nonce 每轮随机，因此清扫不会碰到并发会话的真实行。</p>
 *
 * @author ECOS Runtime #147AN
 */
@EnabledIfSystemProperty(named = "ecos.verify.live", matches = "true")
class CacheInvalidationOutboxLiveTest {

    private static final String TABLE = "ecos_control.cache_invalidation_outbox";
    private static final String ENV = "live";
    private static final String DOMAIN = "kb-search";
    private static final String OBJECT_TYPE = "knowledge-search";

    private static String nonce;
    private static JdbcTemplate jdbc;
    private static LettuceConnectionFactory factory;
    private static RedisMessageListenerContainer container;
    private static RuntimeCacheManager nodeA;
    private static RuntimeCacheManager nodeB;
    private static CacheInvalidationRelay relay;
    private static final AtomicInteger broadcastsAtNodeB = new AtomicInteger();
    private static final Set<String> createdIds = new LinkedHashSet<>();

    @BeforeAll
    static void wireRealInfra() {
        nonce = Long.toHexString(System.nanoTime());

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("127.0.0.1");
        ds.setPortNumber(5432);
        ds.setDatabaseName("sys_man");
        ds.setUser(System.getProperty("ecos.live.db.user", "postgres"));
        // 口令一律由运行侧供给（-Decos.live.db.password 或 ECOS_DB_PASSWORD），仓库里不留默认值
        String dbPass = System.getProperty("ecos.live.db.password", System.getenv("ECOS_DB_PASSWORD"));
        assertNotNull(dbPass, "活体腿需显式给数据库口令：-Decos.live.db.password=… 或环境变量 ECOS_DB_PASSWORD");
        ds.setPassword(dbPass);
        jdbc = new JdbcTemplate(ds);

        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        RedisStringCache redis = new RedisStringCache(provider(template));
        assertTrue(redis.available(), "ecos-redis 容器应可达（PONG）");

        // 「另一节点」的订阅端：与生产 CacheInvalidationSubscriberConfig 同形状，只计数不驱动失效
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> broadcastsAtNodeB.incrementAndGet(),
                new ChannelTopic(CacheInvalidationRelay.CHANNEL));
        container.afterPropertiesSet();
        container.start();

        nodeA = cache(redis);
        nodeB = cache(redis);
        relay = new CacheInvalidationRelay(new CacheInvalidationOutboxDao(jdbc),
                provider(redis), provider(nodeA));
        // 两个节点都装投递口：生产里订阅端节点的管理器同样有这个 Bean
        CacheInvalidationNotifier notifier = relay;
        inject(nodeA, "outboxProvider", provider(notifier));
        inject(nodeB, "outboxProvider", provider(notifier));
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (container != null) {
            container.destroy();
        }
        if (factory != null) {
            factory.destroy();
        }
        for (String id : createdIds) {
            if (jdbc != null) {
                jdbc.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
            }
        }
        // 断言中途失败时 id 可能还没登记：nonce 本轮唯一，按它兜底清扫（不会碰到并发会话的真实行）
        if (jdbc != null) {
            jdbc.update("DELETE FROM " + TABLE + " WHERE payload_json LIKE ?", "%" + nonce + "%");
        }
    }

    @Test
    @DisplayName("失效 → 同库恰好落一行 PENDING（生产半真在投递，不是只有消费端）")
    void evictionWritesOneOutboxRow() {
        CacheKey key = new CacheKey(ENV, DOMAIN, OBJECT_TYPE, "tenant-a", "obj-" + nonce, "1");
        nodeA.putString(key, "{\"hits\":1}", 30);
        assertEquals("{\"hits\":1}", nodeA.getString(key).orElse(null), "先确认真写进了 Redis 字符串通道");

        nodeA.evictObject(DOMAIN, OBJECT_TYPE, "obj-" + nonce);

        List<String> rows = rowsLike("obj-" + nonce);
        assertEquals(1, rows.size(), "一次对象失效必须恰好落一行，多行＝回环");
        String id = rows.get(0);
        createdIds.add(id);
        assertTrue(payloadOf(id).contains("\"domain\":\"" + DOMAIN + "\""), "负载必须带缓存域");
        assertEquals("PENDING", statusOf(id));
    }

    @Test
    @DisplayName("中继发布 → PUBLISHED / attempts=1 / published_at，且另一节点真的收到")
    void relayPublishesToRealRedisChannel() {
        nodeA.evictObject(DOMAIN, OBJECT_TYPE, "relay-" + nonce);
        String id = singleRowIdLike("relay-" + nonce);
        int before = broadcastsAtNodeB.get();

        relay.relay();

        assertEquals("PUBLISHED", statusOf(id));
        assertEquals(1, (int) attemptsOf(id), "首次发布 attempts 必须为 1");
        assertNotNull(jdbc.queryForObject("SELECT published_at FROM " + TABLE + " WHERE id = ?",
                Timestamp.class, id), "published_at 必须落值");
        assertTrue(broadcastsAtNodeB.get() > before, "订阅端必须真的收到频道消息（跨节点面）");
    }

    @Test
    @DisplayName("订阅端走 Local 变体：执行失效但不再宣告，链路不成环")
    void broadcastHandlerDoesNotReEnqueue() {
        nodeA.evictObject(DOMAIN, OBJECT_TYPE, "loop-" + nonce);
        relay.relay();
        singleRowIdLike("loop-" + nonce);

        // 模拟收到广播后的处理动作（生产订阅端就是这么调的）
        nodeB.invalidateDomainLocal(ENV, DOMAIN);

        assertEquals(1, rowsLike("loop-" + nonce).size(),
                "广播处理若再宣告失效，这里就会变成 2 行 ⇒ 成环");
    }

    @Test
    @DisplayName("重复中继幂等：已 PUBLISHED 的行不会被再次捞出发布")
    void publishedRowIsNotRepublished() {
        nodeA.evictObject(DOMAIN, OBJECT_TYPE, "twice-" + nonce);
        String id = singleRowIdLike("twice-" + nonce);
        relay.relay();
        assertEquals("PUBLISHED", statusOf(id));
        int attemptsAfterFirst = attemptsOf(id);
        int received = broadcastsAtNodeB.get();

        relay.relay();

        assertEquals(attemptsAfterFirst, (int) attemptsOf(id), "已发布行不得再被中继");
        assertEquals(received, broadcastsAtNodeB.get(), "已发布行不得再产生广播");
    }

    // ── 支撑 ────────────────────────────────────────────────────────────

    private static RuntimeCacheManager cache(RedisStringCache redis) {
        CacheSettings settings = new CacheSettings(true, 30L, 60L, true, ENV);
        RuntimeCacheManager manager = new RuntimeCacheManager(provider((CacheSettingsSource) () -> settings));
        inject(manager, "redisProvider", provider(redis));
        return manager;
    }

    private static List<String> rowsLike(String objectId) {
        return jdbc.queryForList("SELECT id FROM " + TABLE + " WHERE payload_json LIKE ?",
                String.class, "%" + objectId + "%");
    }

    private static String singleRowIdLike(String objectId) {
        List<String> ids = rowsLike(objectId);
        assertEquals(1, ids.size(), "一次对象失效必须恰好一行（零行＝生产半没接上，多行＝广播回环）");
        createdIds.add(ids.get(0));
        return ids.get(0);
    }

    private static String payloadOf(String id) {
        return jdbc.queryForObject("SELECT payload_json FROM " + TABLE + " WHERE id = ?", String.class, id);
    }

    private static String statusOf(String id) {
        createdIds.add(id);
        return jdbc.queryForObject("SELECT status FROM " + TABLE + " WHERE id = ?", String.class, id);
    }

    private static Integer attemptsOf(String id) {
        Integer n = jdbc.queryForObject("SELECT attempts FROM " + TABLE + " WHERE id = ?", Integer.class, id);
        return n == null ? -1 : n;
    }

    private static void inject(Object target, String field, Object value) {
        try {
            Field f = RuntimeCacheManager.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 " + field + " 失败", e);
        }
    }

    /** 极简 ObjectProvider 假面（与 runtime-core 侧门禁测试同形）。 */
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }
}
