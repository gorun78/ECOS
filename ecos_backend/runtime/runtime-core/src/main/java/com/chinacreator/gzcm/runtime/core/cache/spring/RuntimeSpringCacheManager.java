package com.chinacreator.gzcm.runtime.core.cache.spring;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;

/**
 * Spring Cache 门面 → Runtime 单源（§2.9.8⑤；#147AF/D）。
 *
 * <p>装配本 Bean 后，任何 {@code @Cacheable/@CacheEvict} 都落到 {@link RuntimeCacheManager}：
 * 键规范、TTL、陈旧上限、Redis 通道与 Outbox 失效全部复用同一底座，
 * <b>不产生第二套缓存实现</b>（这正是此前“拒绝直接引入 Spring Cache 抽象”的原因）。</p>
 *
 * <p>未启用缓存时（{@code ecos.cache.enabled=false}）各 {@link Cache} 仍可注入，但 get 恒空、
 * put 恒 no-op——与 Runtime 语义一致：没有缓存 ≠ 没有数据。</p>
 *
 * @author ECOS Runtime #147AF
 */
@Component
public class RuntimeSpringCacheManager implements CacheManager {

    private final ObjectProvider<RuntimeCacheManager> runtimeCache;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public RuntimeSpringCacheManager(ObjectProvider<RuntimeCacheManager> runtimeCache) {
        this.runtimeCache = runtimeCache;
    }

    @Override
    public Cache getCache(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        RuntimeCacheManager manager = runtimeCache == null ? null : runtimeCache.getIfAvailable();
        if (manager == null) {
            return null;
        }
        return caches.computeIfAbsent(name, n -> new RuntimeSpringCache(n, manager));
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }
}
