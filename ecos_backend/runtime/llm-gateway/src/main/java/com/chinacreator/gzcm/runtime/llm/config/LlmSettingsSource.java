package com.chinacreator.gzcm.runtime.llm.config;

/**
 * LLM 运行期配置源 SPI（G-2 整改，参照 {@code CacheSettingsSource} 先例）。
 *
 * <p>runtime 底座只声明接缝，<b>不自建配置读取</b>：sysman-impl 提供读
 * {@code sys_config config_group=llm} 的适配 Bean（{@code SysConfigLlmSettingsSource}）；
 * 宿主未装配时消费方落到 {@link LlmSettings#DEFAULTS}（= yml 现行缺省，行为不变）。</p>
 */
@FunctionalInterface
public interface LlmSettingsSource {

    /** 当前生效配置；不可读时返回 {@link LlmSettings#DEFAULTS}，禁返回 null。 */
    LlmSettings settings();
}
