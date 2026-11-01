package com.chinacreator.gzcm.runtime.core.cache;

/**
 * Runtime 缓存配置（§2.9.8⑤ 裁决：Redis 缓存能力归 runtime 公共底座，由 Runtime 统一管理配置）。
 *
 * <p><b>默认值 = 关闭</b>：配置源不可用时缓存不启用（fail-closed 于"不使用缓存"），
 * 避免各服务自建缓存配置偷偷打开。权威配置经 sysman facede 的 {@code config_group=cache}
 * 读取（实现见 sysman-impl 的 {@code SysConfigCacheSettingsSource}），本类只是值载体。</p>
 *
 * @param enabled            是否启用缓存（未接入 Redis 的部署保持 false）
 * @param defaultTtlSeconds  缺省 TTL（≤0 视为不缓存）
 * @param maxStaleSeconds    允许的最大陈旧时间（权限类对象须 ≤ 该值）
 * @param redisEnabled       Redis 通道是否启用（当前基线为 false；启用须先补底座与容器裁决）
 * @author ECOS Runtime底座 #147J
 */
public record CacheSettings(boolean enabled, long defaultTtlSeconds, long maxStaleSeconds,
                            boolean redisEnabled) {

    /** 保守缺省：不启用、不缓存、不连 Redis。 */
    public static final CacheSettings DISABLED = new CacheSettings(false, 0L, 0L, false);

    /** 归一化：负 TTL 一律按 0（=不缓存）。 */
    public CacheSettings normalized() {
        return new CacheSettings(enabled, Math.max(0L, defaultTtlSeconds),
                Math.max(0L, maxStaleSeconds), redisEnabled);
    }
}
