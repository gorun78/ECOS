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
                provider(() -> new CacheSettings(true, 60, 60, false)), now::get);

        cache.put(key("tenant-a", "1"), "v1");
        assertEquals(Optional.of("v1"), cache.get(key("tenant-a", "1"), String.class));
        assertTrue(cache.get(key("tenant-a", "2"), String.class).isEmpty(), "版本化缓存：新版本不命中旧值");

        now.addAndGet(61_000L);
        assertTrue(cache.get(key("tenant-a", "1"), String.class).isEmpty(), "过期必须回源");
    }

    @Test
    void domainInvalidationOnlyTouchesThatDomain() {
        RuntimeCacheManager cache = new RuntimeCacheManager(
                provider(() -> new CacheSettings(true, 600, 600, false)));
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
                provider(() -> new CacheSettings(true, 600, 600, false)));
        cache.put(key("tenant-a", "1"), "string");
        assertTrue(cache.get(key("tenant-a", "1"), Integer.class).isEmpty());
    }
}
