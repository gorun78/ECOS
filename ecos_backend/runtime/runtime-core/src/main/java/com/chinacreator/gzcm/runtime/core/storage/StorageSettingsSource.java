package com.chinacreator.gzcm.runtime.core.storage;

/**
 * 对象存储配置源 SPI（G-2 整改；对齐 {@code CacheSettingsSource} 形态）。
 *
 * <p>runtime 底座只声明接缝，<b>不自建配置读取</b>：sysman-impl 提供读
 * {@code sys_config config_group=storage} 的适配 Bean；宿主未装配时消费点回落既有解析链
 * （yml {@code @Value} &gt; 环境变量 &gt; 硬编码兜底，行为等价改造前）。</p>
 */
@FunctionalInterface
public interface StorageSettingsSource {

    /** 当前生效配置；不可读时返回 {@link StorageSettings#DEFAULTS}，禁返回 null。 */
    StorageSettings settings();
}
