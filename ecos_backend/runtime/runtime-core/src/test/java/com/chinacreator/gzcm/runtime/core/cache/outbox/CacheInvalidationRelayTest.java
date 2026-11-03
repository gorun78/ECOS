package com.chinacreator.gzcm.runtime.core.cache.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RedisStringCache;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * 缓存失效 Outbox 中继门（§2.9.8④；#147AF/D）。
 *
 * <p>六条：① 发布成功 ⇒ 置 PUBLISHED；② 发布不可用 ⇒ 累加失败、<b>不得</b>置 PUBLISHED（禁假成功）；
 * ③ 缓存关闭 ⇒ 不空转；④ 负载为扁平 JSON 且订阅端能按字段解析；⑤ 保留期清退的截止时刻在 now 之前
 * 且单轮有上限；⑥ 清退异常不冒泡（定时线程不得停摆）。清退 SQL 的<b>语义</b>（只删过期 PUBLISHED、
 * 不碰 PENDING/FAILED）在网关侧真库活体腿 {@code CacheInvalidationOutboxLiveTest} 验，本类只验调用形状。</p>
 *
 * @author ECOS Runtime #147AF
 */
class CacheInvalidationRelayTest {

    private static ObjectProvider<RedisStringCache> redisProvider(RedisStringCache redis) {
        return new ObjectProvider<>() {
            @Override
            public RedisStringCache getObject(Object... args) {
                return redis;
            }

            @Override
            public RedisStringCache getObject() {
                return redis;
            }

            @Override
            public RedisStringCache getIfAvailable() {
                return redis;
            }

            @Override
            public RedisStringCache getIfUnique() {
                return redis;
            }
        };
    }

    private static ObjectProvider<RuntimeCacheManager> cacheProvider(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        RuntimeCacheManager manager = new RuntimeCacheManager(new ObjectProvider<>() {
            @Override
            public CacheSettingsSource getObject(Object... args) {
                return src;
            }

            @Override
            public CacheSettingsSource getObject() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfAvailable() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfUnique() {
                return src;
            }
        });
        return new ObjectProvider<>() {
            @Override
            public RuntimeCacheManager getObject(Object... args) {
                return manager;
            }

            @Override
            public RuntimeCacheManager getObject() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfAvailable() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfUnique() {
                return manager;
            }
        };
    }

    private static Map<String, Object> row() {
        return Map.of("id", "row-1", "event_type", "CACHE_INVALIDATE",
                "payload_json", "{\"env\":\"default\",\"domain\":\"sysman\",\"reason\":\"test\"}", "attempts", 0);
    }

    @Test
    @DisplayName("发布成功：置 PUBLISHED 且计数 +1")
    void marksPublishedOnSuccess() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        when(dao.pollPending(CacheInvalidationRelay.BATCH)).thenReturn(List.of(row()));
        RedisStringCache redis = mock(RedisStringCache.class);
        when(redis.publish(eq(CacheInvalidationRelay.CHANNEL), anyString())).thenReturn(2L);

        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(redis),
                cacheProvider(new CacheSettings(true, 600L, 60L, true, "dev")), 1440);

        assertEquals(1, relay.relayOnce());
        verify(dao).markPublished("row-1");
        verify(dao, never()).markFailed(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("发布不可用：累加失败且绝不置 PUBLISHED（禁假成功）")
    void marksFailedWhenRedisUnavailable() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        when(dao.pollPending(CacheInvalidationRelay.BATCH)).thenReturn(List.of(row()));
        RedisStringCache redis = mock(RedisStringCache.class);
        when(redis.publish(anyString(), anyString())).thenReturn(-1L);

        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(redis),
                cacheProvider(new CacheSettings(true, 600L, 60L, true, "dev")), 1440);

        assertEquals(0, relay.relayOnce());
        verify(dao, never()).markPublished(anyString());
        verify(dao).markFailed(eq("row-1"), anyString(), eq(CacheInvalidationRelay.MAX_ATTEMPTS));
    }

    @Test
    @DisplayName("缓存关闭：不空转（不取批次）")
    void doesNothingWhenCacheDisabled() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(null),
                cacheProvider(CacheSettings.DISABLED), 1440);

        assertEquals(0, relay.relayOnce());
        verify(dao, never()).pollPending(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("负载为扁平 JSON，订阅端按字段解析")
    void payloadIsFlatJsonAndParsable() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        when(dao.enqueue(anyString(), anyString())).thenReturn("id-1");
        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(null),
                cacheProvider(CacheSettings.DISABLED), 1440);

        relay.enqueueDomainInvalidation("dev", "sysman", "role-changed");

        org.mockito.ArgumentCaptor<String> payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(dao).enqueue(eq("CACHE_INVALIDATE"), payload.capture());
        assertTrue(payload.getValue().contains("\"env\":\"dev\""));
        assertTrue(payload.getValue().contains("\"domain\":\"sysman\""));
        assertEquals("dev", CacheInvalidationSubscriberConfig.extract(payload.getValue(), "env"));
        assertEquals("sysman", CacheInvalidationSubscriberConfig.extract(payload.getValue(), "domain"));
        assertEquals("role-changed", CacheInvalidationSubscriberConfig.extract(payload.getValue(), "reason"));
    }

    @Test
    @DisplayName("保留期清退：截止时刻=now−retention，单轮上限=PURGE_BATCH")
    void purgeUsesRetentionWindowAndBatchCap() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(null),
                cacheProvider(new CacheSettings(true, 600L, 60L, true, "dev")), 120);
        when(dao.purgePublished(any(), anyInt())).thenReturn(7);

        Timestamp before = Timestamp.from(Instant.now());
        assertEquals(7, relay.purgeOnce());
        ArgumentCaptor<Timestamp> cutoff = ArgumentCaptor.forClass(Timestamp.class);
        verify(dao).purgePublished(cutoff.capture(), eq(CacheInvalidationRelay.PURGE_BATCH));

        long ageMillis = Duration.between(cutoff.getValue().toInstant(),
                before.toInstant()).toMillis();
        assertTrue(ageMillis >= Duration.ofMinutes(119).toMillis()
                        && ageMillis <= Duration.ofMinutes(121).toMillis(),
                "cutoff 必须落在 now 之前约 120 分钟；落在 now 之后就是把未投递的行当过期删除: " + ageMillis);
    }

    @Test
    @DisplayName("清退异常不得冒泡：定时线程不能因一轮 SQL 失败而停摆")
    void purgeSwallowsDaoFailure() {
        CacheInvalidationOutboxDao dao = mock(CacheInvalidationOutboxDao.class);
        when(dao.purgePublished(any(), anyInt())).thenThrow(new IllegalStateException("表不存在"));
        CacheInvalidationRelay relay = new CacheInvalidationRelay(dao, redisProvider(null),
                cacheProvider(new CacheSettings(true, 600L, 60L, true, "dev")), 1440);

        relay.purge();
        verify(dao).purgePublished(any(), eq(CacheInvalidationRelay.PURGE_BATCH));
    }
}
