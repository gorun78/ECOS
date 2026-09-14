package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 认知引擎配置保存项 DTO — PUT {@code /api/v1/cognitive/config} 的入参单元。
 *
 * <p>对应 sys_config 表一行的两个核心字段：
 * <ul>
 *   <li>{@code configKey} &nbsp;&nbsp;— 配置键，必填，对应 {@code config_key}</li>
 *   <li>{@code configValue} &nbsp;— 配置值，空串视为清空，对应 {@code config_value}</li>
 * </ul>
 *
 * <p>兼容前端旧字段（{@code config_key} / {@code config_value}）：
 * Jackson {@link JsonAlias} 自动双向映射。</p>
 */
public class CognitiveConfigSaveDTO {

    private String configKey;
    private String configValue;

    public CognitiveConfigSaveDTO() {}

    public CognitiveConfigSaveDTO(String configKey, String configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
    }

    public String getConfigKey() { return configKey; }

    @JsonAlias({"config_key"})
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }

    @JsonAlias({"config_value"})
    public void setConfigValue(String configValue) { this.configValue = configValue; }
}
