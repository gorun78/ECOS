package com.chinacreator.gzcm.services.sysman.cache;

import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 缓存配置的唯一读取面：{@code sys_config} 的 {@code config_group=cache}（§2.9.8⑤ 裁决；#147J）。
 *
 * <p><b>为什么放在 sysman-impl</b>：铁律「配置与字典只经 sysman api 门面」；runtime 底座不得
 * 直读配置表。本类作为 {@link CacheSettingsSource} 适配 Bean 供 runtime 门面消费，
 * 各引擎/服务<b>不得</b>自建缓存配置（DC-3）。</p>
 *
 * <p>键：{@code ecos.cache.enabled} / {@code ecos.cache.default_ttl_seconds} /
 * {@code ecos.cache.max_stale_seconds} / {@code ecos.cache.redis_enabled}；
 * 读取失败一律返回 {@link CacheSettings#DISABLED}（不缓存，never fail-open）。</p>
 *
 * @author ECOS Sysman #147J
 */
@Component
public class SysConfigCacheSettingsSource implements CacheSettingsSource {

    private static final Logger log = LoggerFactory.getLogger(SysConfigCacheSettingsSource.class);

    static final String GROUP_CACHE = "cache";
    static final String KEY_ENABLED = "ecos.cache.enabled";
    static final String KEY_DEFAULT_TTL = "ecos.cache.default_ttl_seconds";
    static final String KEY_MAX_STALE = "ecos.cache.max_stale_seconds";
    static final String KEY_REDIS = "ecos.cache.redis_enabled";

    private final SysConfigService sysConfigService;

    /** 进程内缓存配置值，避免每次 get 打库（配置变更由既有配置刷新机制驱动重建本 Bean 或重启）。 */
    private volatile CacheSettings cached;

    public SysConfigCacheSettingsSource(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public CacheSettings settings() {
        CacheSettings local = cached;
        if (local != null) {
            return local;
        }
        try {
            CacheSettings loaded = new CacheSettings(
                    bool(KEY_ENABLED, false),
                    number(KEY_DEFAULT_TTL, 0L),
                    number(KEY_MAX_STALE, 0L),
                    bool(KEY_REDIS, false)).normalized();
            cached = loaded;
            return loaded;
        } catch (RuntimeException e) {
            log.warn("读取缓存配置失败，按 DISABLED 处理（不缓存，never fail-open）: {}", e.getMessage());
            return CacheSettings.DISABLED;
        }
    }

    /** 配置变更后由配置刷新链路调用。 */
    public void refresh() {
        cached = null;
    }

    private boolean bool(String key, boolean fallback) {
        String v = sysConfigService.getString(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    private long number(String key, long fallback) {
        String v = sysConfigService.getString(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
