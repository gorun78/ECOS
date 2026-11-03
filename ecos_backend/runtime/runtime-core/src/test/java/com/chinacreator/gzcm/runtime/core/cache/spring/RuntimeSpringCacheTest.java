package com.chinacreator.gzcm.runtime.core.cache.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * Spring Cache 薄适配门（§2.9.8⑤；#147AF/D）。
 *
 * <p>五条：① 对象可经 JSON 类型头往返（限 {@code com.chinacreator.gzcm} 白名单）；
 * ② 无租户上下文不缓存（权限先于缓存）；③ 键含冒号不缓存（歧义即串租户）；
 * ④ {@code evict/clear} 生效；⑤ 缓存关闭时不缓存也不错觉命中。</p>
 *
 * @author ECOS Runtime #147AF
 */
class RuntimeSpringCacheTest {

    /** 值类型必须在白名单包内才能反序列化。 */
    public static class SampleValue {
        private String id;
        private List<String> tags;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    private static ObjectProvider<RuntimeCacheManager> runtime(CacheSettings settings) {
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

    private static Cache cache(CacheSettings settings) {
        RuntimeSpringCacheManager manager = new RuntimeSpringCacheManager(runtime(settings));
        return manager.getCache("demo");
    }

    private static SampleValue value() {
        SampleValue v = new SampleValue();
        v.setId("id-1");
        v.setTags(List.of("a", "b"));
        return v;
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("对象经 JSON 类型头往返（白名单包内类型）")
    void roundTripsObjectValue() {
        Cache cache = cache(new CacheSettings(true, 600L, 60L, false, "dev"));
        TenantContextHolder.setTenantId("tenant-a");

        cache.put("k1", value());
        Cache.ValueWrapper hit = cache.get("k1");

        assertNotNull(hit);
        SampleValue got = (SampleValue) hit.get();
        assertEquals("id-1", got.getId());
        assertEquals(List.of("a", "b"), got.getTags());
    }

    @Test
    @DisplayName("无租户上下文：不缓存（put 后 get 恒空）")
    void noTenantMeansNoCaching() {
        Cache cache = cache(new CacheSettings(true, 600L, 60L, false, "dev"));

        cache.put("k1", value());

        assertNull(cache.get("k1"));
    }

    @Test
    @DisplayName("键含冒号：不缓存（键段歧义禁止）")
    void ambiguousKeyIsNotCached() {
        Cache cache = cache(new CacheSettings(true, 600L, 60L, false, "dev"));
        TenantContextHolder.setTenantId("tenant-a");

        cache.put("a:b", value());

        assertNull(cache.get("a:b"));
    }

    @Test
    @DisplayName("evict 精确失效、clear 按域失效")
    void evictAndClearWork() {
        Cache cache = cache(new CacheSettings(true, 600L, 60L, false, "dev"));
        TenantContextHolder.setTenantId("tenant-a");
        cache.put("k1", value());
        cache.put("k2", value());

        cache.evict("k1");
        assertNull(cache.get("k1"));
        assertNotNull(cache.get("k2"));

        cache.clear();
        assertNull(cache.get("k2"));
    }

    @Test
    @DisplayName("缓存关闭：put 恒 no-op、get 恒空")
    void disabledCacheNeverStores() {
        Cache cache = cache(CacheSettings.DISABLED);
        TenantContextHolder.setTenantId("tenant-a");

        cache.put("k1", value());

        assertNull(cache.get("k1"));
    }

    @Test
    @DisplayName("get(key, loader) 未命中时回源并回填")
    void loadsOnMiss() {
        Cache cache = cache(new CacheSettings(true, 600L, 60L, false, "dev"));
        TenantContextHolder.setTenantId("tenant-a");
        AtomicInteger calls = new AtomicInteger();

        Object first = cache.get("k1", () -> {
            calls.incrementAndGet();
            return value();
        });
        Object second = cache.get("k1", () -> {
            calls.incrementAndGet();
            return value();
        });

        assertEquals("id-1", ((SampleValue) first).getId());
        assertEquals("id-1", ((SampleValue) second).getId());
        assertEquals(1, calls.get(), "第二次必须命中缓存，不重复回源");
    }
}
