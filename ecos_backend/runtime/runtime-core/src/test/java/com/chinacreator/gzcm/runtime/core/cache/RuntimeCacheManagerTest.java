package com.chinacreator.gzcm.runtime.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** §2.9.8③④⑤ 缓存键规范 / TTL / 关闭即不缓存（#147J）。 */
class RuntimeCacheManagerTest {

    private static CacheKey key(String scope, String version) {
        return new CacheKey("dev", "ontology", "rule", scope, "r1", version);
    }

    /** 极简 ObjectProvider 假面。 */
    private static ObjectProvider<CacheSettingsSource> provider(CacheSettingsSource src) {
        return new ObjectProvider<>() {
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
        };
    }

    @Test
    void keyFormatIsSingleSourcedAndAmbiguousSegmentsRejected() {
        assertEquals("ecos:dev:ontology:rule:tenant-a:r1:v7", key("tenant-a", "7").toString());
        assertEquals("ecos:dev:ontology:rule:tenant-a:r1:v7", key("tenant-a", "v7").toString());
        assertThrows(IllegalArgumentException.class,
                () -> new CacheKey("dev", "ontology", "rule", " ", "r1", "1"),
                "无 scope 的键必须拒绝（权限先于缓存）");
        assertThrows(IllegalArgumentException.class,
                () -> new CacheKey("dev", "on:tology", "rule", "t", "r1", "1"),
                "含冒号段会串租户");
        assertNull(CacheKey.parse("redis:dev:ontology:rule:t:r1:v1"));
    }

    @Test
    void disabledCacheNeverReturnsOrStores() {
        RuntimeCacheManager cache = new RuntimeCacheManager(provider(() -> CacheSettings.DISABLED));
        cache.put(key("tenant-a", "1"), "value");
        assertFalse(cache.enabled());
        assertTrue(cache.get(key("tenant-a", "1"), String.class).isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void ttlExpiryAndVersionIsolation() {
        AtomicLong now = new AtomicLong(1_000_000L);
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 60, 60, false, "dev")), now::get);

        cache.put(key("tenant-a", "1"), "v1");
        assertEquals(Optional.of("v1"), cache.get(key("tenant-a", "1"), String.class));
        assertTrue(cache.get(key("tenant-a", "2"), String.class).isEmpty(), "版本化缓存：新版本不命中旧值");

        now.addAndGet(61_000L);
        assertTrue(cache.get(key("tenant-a", "1"), String.class).isEmpty(), "过期必须回源");
    }

    @Test
    void domainInvalidationOnlyTouchesThatDomain() {
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 600, false, "dev")));
        cache.put(key("tenant-a", "1"), "a");
        cache.put(key("tenant-b", "1"), "b");
        cache.put(new CacheKey("dev", "cognitive", "model", "tenant-a", "m1", "1"), "c");

        assertEquals(2, cache.invalidateDomain("dev", "ontology"));
        assertTrue(cache.get(key("tenant-a", "1"), String.class).isEmpty());
        assertEquals(Optional.of("c"),
                cache.get(new CacheKey("dev", "cognitive", "model", "tenant-a", "m1", "1"), String.class));
    }

    @Test
    void typeMismatchIsMissNotClassCast() {
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 600, false, "dev")));
        cache.put(key("tenant-a", "1"), "string");
        assertTrue(cache.get(key("tenant-a", "1"), Integer.class).isEmpty());
    }

    /** 环境段单源（#147AA）：键的 env 来自 CacheSettings，调用方无从自备。 */
    @Test
    void keyBuilderTakesEnvFromSettingsSingleSource() {
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 120, false, "prod")));
        assertEquals("prod", cache.env());
        CacheKey k = cache.key("sysman", "permission", "tenant-a", "u1", "3");
        assertEquals("ecos:prod:sysman:permission:tenant-a:u1:v3", k.toString());
        assertEquals(120L, cache.stalenessCeilingSeconds(), "陈旧上限须由配置面给出");
    }

    /** 陈旧上限钳制：显式 TTL 也压不过 maxStaleSeconds——权限类对象的天花板由底座保证。 */
    @Test
    void explicitTtlIsClampedByMaxStaleCeiling() {
        AtomicLong now = new AtomicLong(500_000L);
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 30, false, "dev")), now::get);

        cache.put(key("tenant-a", "1"), "v1", 600L);
        now.addAndGet(29_000L);
        assertEquals(Optional.of("v1"), cache.get(key("tenant-a", "1"), String.class));

        now.addAndGet(2_000L);
        assertTrue(cache.get(key("tenant-a", "1"), String.class).isEmpty(),
                "显式 600s TTL 必须被 30s 陈旧上限钳制，否则权限陈旧时间失控");
    }

    /** 缺省 TTL 同样受钳制；上限未给出（≤0）时消费方须自行拒绝缓存权限类对象。 */
    @Test
    void defaultTtlIsClampedAndMissingCeilingIsReported() {
        AtomicLong now = new AtomicLong(1_000L);
        RuntimeCacheManager clamped = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 10, false, "dev")), now::get);
        clamped.put(key("tenant-a", "1"), "v1");
        now.addAndGet(11_000L);
        assertTrue(clamped.get(key("tenant-a", "1"), String.class).isEmpty());

        RuntimeCacheManager unbounded = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 0, false, "dev")));
        assertEquals(0L, unbounded.stalenessCeilingSeconds(),
                "上限缺席须以 ≤0 暴露给消费方（权限类对象据此不缓存，never fail-open）");
    }

    /** 按对象失效跨所有 scope（同一用户多租户视图），且不误伤他人。 */
    @Test
    void evictObjectRemovesEveryScopeOfThatObjectOnly() {
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 600, false, "dev")));
        cache.put(cache.key("sysman", "permission", "tenant-a", "u1", "1"), "a");
        cache.put(cache.key("sysman", "permission", "tenant-b", "u1", "1"), "b");
        cache.put(cache.key("sysman", "permission", "tenant-a", "u2", "1"), "c");
        cache.put(cache.key("sysman", "decision", "tenant-a", "u1", "1"), "d");

        assertEquals(2, cache.evictObject("sysman", "permission", "u1"));
        assertTrue(cache.get(cache.key("sysman", "permission", "tenant-a", "u1", "1"), String.class).isEmpty());
        assertTrue(cache.get(cache.key("sysman", "permission", "tenant-b", "u1", "1"), String.class).isEmpty());
        assertEquals(Optional.of("c"),
                cache.get(cache.key("sysman", "permission", "tenant-a", "u2", "1"), String.class), "不得误伤他人权限");
        assertEquals(Optional.of("d"),
                cache.get(cache.key("sysman", "decision", "tenant-a", "u1", "1"), String.class), "不得跨类型误伤");
        assertEquals(0, cache.evictObject("sysman", "permission", "u9"), "无命中须返回 0 而非异常");
    }

    /** 缓存配置 env 段本身的可证伪面（夹具）。 */
    @Test
    void settingsEnvSegmentIsNormalizedOrRejected() {
        assertEquals(CacheSettings.DEFAULT_ENV, new CacheSettings(true, 1, 1, false, "  ").env());
        assertEquals(CacheSettings.DEFAULT_ENV, new CacheSettings(true, 1, 1, false, null).env());
        assertEquals("dev", new CacheSettings(true, 1, 1, false, " dev ").env());
        assertThrows(IllegalArgumentException.class,
                () -> new CacheSettings(true, 1, 1, false, "dev:eu"), "env 含冒号会串环境");
        assertEquals(CacheSettings.DISABLED, CacheSettings.DISABLED.normalized());
    }
}
