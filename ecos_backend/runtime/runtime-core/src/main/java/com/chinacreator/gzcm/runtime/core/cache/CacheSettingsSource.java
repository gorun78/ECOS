package com.chinacreator.gzcm.runtime.core.cache;

/**
 * 缓存配置源 SPI（§2.9.8⑤；#147J）。
 *
 * <p>runtime 底座只声明接缝，<b>不自建配置读取</b>：sysman-impl 提供读
 * {@code sys_config config_group=cache} 的适配 Bean；宿主未装配时由
 * {@link RuntimeCacheManager} 落到 {@link CacheSettings#DISABLED}（不缓存）。</p>
 */
@FunctionalInterface
public interface CacheSettingsSource {

    /** 当前生效配置；不可读时返回 {@link CacheSettings#DISABLED}，禁返回 null。 */
    CacheSettings settings();
}
