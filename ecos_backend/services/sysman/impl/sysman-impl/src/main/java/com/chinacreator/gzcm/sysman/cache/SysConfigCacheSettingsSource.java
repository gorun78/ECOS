package com.chinacreator.gzcm.sysman.cache;

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
 * <p><b>包位置是承流条件，不是风格问题</b>（#147AA 取证）：gateway 的
 * {@code @ComponentScan} 只圈 {@code com.chinacreator.gzcm.sysman}（不含 {@code ...services.sysman}）。
 * 本类原落 {@code com.chinacreator.gzcm.services.sysman.cache} 时，Bean 从不生成，
 * {@code RuntimeCacheManager} 的 {@code CacheSettingsSource} 恒缺席 ⇒ 配置面永远读不到、
 * 缓存恒 DISABLED。移到 sysman 包下才真正承流；防回退门见 gateway
 * {@code CacheSettingsSourceScanGateTest}。</p>
 *
 * <p>键：{@code ecos.cache.enabled} / {@code ecos.cache.default_ttl_seconds} /
 * {@code ecos.cache.max_stale_seconds} / {@code ecos.cache.redis_enabled} / {@code ecos.cache.env}；
 * 读取失败一律返回 {@link CacheSettings#DISABLED}（不缓存，never fail-open）。</p>
 *
 * <p><b>env 段的特殊口径</b>（§2.9.8③；#147AA）：环境标识是<b>部署事实</b>而非业务配置，
 * 一次部署内不得漂移，故取 {@code -D ecos.cache.env} → 环境变量 {@code ECOS_CACHE_ENV} →
 * {@code sys_config} 的 {@code ecos.cache.env} → {@link CacheSettings#DEFAULT_ENV} 四级链，
 * 部署级来源优先于表内值；无论命中哪一级，消费方都只经 {@link CacheSettings#env()} 单源读取，
 * 不得自行拼环境段。</p>
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
    static final String KEY_ENV = "ecos.cache.env";
    static final String ENV_PROPERTY = "ecos.cache.env";
    static final String ENV_VARIABLE = "ECOS_CACHE_ENV";

    private final SysConfigService sysConfigService;

    public SysConfigCacheSettingsSource(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 每次都经 {@link SysConfigService} 的进程内配置缓存取值（几次 map 查找），<b>不做二次快照</b>。
     *
     * <p>#147J 曾在此另存一份 {@code volatile} 快照，但全仓无任何链路调用它的 {@code refresh()}，
     * 等于「改配置必须重启」——而「关掉缓存」是安全动作，迟缓生效本身就是缺陷（§2.9.8④ 主动失效）。
     * 改为直读后，配置写入 + {@code SysConfigService.refreshCache()} 即生效，无需重启、无需新接线。</p>
     */
    @Override
    public CacheSettings settings() {
        try {
            return new CacheSettings(
                    bool(KEY_ENABLED, false),
                    number(KEY_DEFAULT_TTL, 0L),
                    number(KEY_MAX_STALE, 0L),
                    bool(KEY_REDIS, false),
                    resolveEnv()).normalized();
        } catch (RuntimeException e) {
            log.warn("读取缓存配置失败，按 DISABLED 处理（不缓存，never fail-open）: {}", e.getMessage());
            return CacheSettings.DISABLED;
        }
    }

    /** 部署级来源优先于表内值；全链空白则交给 {@link CacheSettings} 取缺省。 */
    private String resolveEnv() {
        String fromProperty = System.getProperty(ENV_PROPERTY);
        if (isPresent(fromProperty)) {
            return fromProperty.trim();
        }
        String fromVariable = environmentVariable(ENV_VARIABLE);
        if (isPresent(fromVariable)) {
            return fromVariable.trim();
        }
        String fromTable = sysConfigService.getString(KEY_ENV);
        return isPresent(fromTable) ? fromTable.trim() : CacheSettings.DEFAULT_ENV;
    }

    /** 环境变量读取面——单测须与宿主的 {@code ECOS_CACHE_ENV} 解耦，故留此覆写点。 */
    String environmentVariable(String name) {
        return System.getenv(name);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
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
