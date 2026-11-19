package com.chinacreator.gzcm.runtime.core.event;

/**
 * 事件总线配置源 SPI（G-2 整改；对齐 {@code CacheSettingsSource} 形态）。
 *
 * <p>runtime 底座只声明接缝，<b>不自建配置读取</b>：sysman-impl 提供读
 * {@code sys_config config_group=event} 的适配 Bean；宿主未装配时消费点回落 yml
 * {@code @Value} 缺省（行为等价改造前）。</p>
 */
@FunctionalInterface
public interface EventSettingsSource {

    /** 当前生效配置；不可读时返回 {@link EventSettings#DEFAULTS}，禁返回 null。 */
    EventSettings settings();
}
