package com.chinacreator.gzcm.runtime.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Runtime 统一缓存门面（§2.9.8④⑤；#147J）。
 *
 * <p><b>定位</b>：受控加速层——权威数据源唯一、可重建、权限先于缓存、fail-closed。
 * 当前运行时实现为进程内 TTL 缓存（Redis 未接入运行链路为<b>既成事实</b>，不得写成已接入）；
 * 后续接 Redis 只替换本类内部存储，键规范/{@code CacheSettings} 单一事实源不变。</p>
 *
 * <p><b>三条硬口径</b>：</p>
 * <ol>
 *   <li><b>关闭即不缓存</b>：{@link CacheSettings#enabled()} 为 false 时 get 恒空、put 恒 no-op
 *       （调用方必须回源，绝不把"没有缓存"当"没有数据"）；</li>
 *   <li><b>版本化</b>：键含 {@code v{version}}，写新版本即天然隔离旧值；</li>
 *   <li><b>先提交权威数据、再失效缓存</b>：失效只能由写路径在提交后调用
 *       {@link #invalidateDomain} / {@link #evict}，缓存层不反向改权威数据。</li>
 * </ol>
 *
 * @author ECOS Runtime底座 #147J
 */
@Component
public class RuntimeCacheManager {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCacheManager.class);

    private final ObjectProvider<CacheSettingsSource> settingsSource;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Autowired
    public RuntimeCacheManager(ObjectProvider<CacheSettingsSource> settingsSource) {
        this(settingsSource, System::currentTimeMillis);
    }

    /** 测试用时钟注入（避免 sleep 判 TTL）。 */
    RuntimeCacheManager(ObjectProvider<CacheSettingsSource> settingsSource, LongSupplier clock) {
        this.settingsSource = settingsSource;
        this.clock = clock;
    }

    private CacheSettings settings() {
        CacheSettingsSource src = settingsSource != null ? settingsSource.getIfAvailable() : null;
        if (src == null) {
            return CacheSettings.DISABLED;
        }
        CacheSettings s = src.settings();
        return s == null ? CacheSettings.DISABLED : s.normalized();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    /** 读缓存；过期/关闭/未命中一律空（调用方回源）。 */
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        if (key == null || type == null || !enabled()) {
            return Optional.empty();
        }
        Entry e = store.get(key.toString());
        if (e == null) {
            return Optional.empty();
        }
        if (e.expiresAt <= clock.getAsLong()) {
            store.remove(key.toString(), e);
            return Optional.empty();
        }
        Object value = e.value;
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }

    /**
     * 写缓存。
     *
     * @param ttlSeconds &gt;0 用显式 TTL；≤0 用配置缺省 TTL；最终 TTL 为 0 时不缓存
     */
    public void put(CacheKey key, Object value) {
        put(key, value, 0L);
    }

    public void put(CacheKey key, Object value, long ttlSeconds) {
        CacheSettings s = settings();
        if (key == null || value == null || !s.enabled()) {
            return;
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : s.defaultTtlSeconds();
        if (ttl <= 0) {
            return;
        }
        store.put(key.toString(), new Entry(value, clock.getAsLong() + ttl * 1000L));
    }

    /** 精确失效（写路径提交后调用）。 */
    public void evict(CacheKey key) {
        if (key != null) {
            store.remove(key.toString());
        }
    }

    /** 域级失效（如权限变更后整域失效）；只删该前缀，不做通配 SQL/脚本。 */
    public int invalidateDomain(String env, String domain) {
        CacheKey probe;
        try {
            probe = new CacheKey(env, domain, "any", "any", "any", "0");
        } catch (IllegalArgumentException e) {
            log.warn("缓存域失效入参非法: env={} domain={}", env, domain);
            return 0;
        }
        String prefix = "ecos:" + probe.env() + ':' + probe.domain() + ':';
        int before = store.size();
        store.keySet().removeIf(k -> k.startsWith(prefix));
        return before - store.size();
    }

    /** 清空（仅运维/测试；生产由 TTL 自然收敛）。 */
    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    private record Entry(Object value, long expiresAt) {
    }
}
