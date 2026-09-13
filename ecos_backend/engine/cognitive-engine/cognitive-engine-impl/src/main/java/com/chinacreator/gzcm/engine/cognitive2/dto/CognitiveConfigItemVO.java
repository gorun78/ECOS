package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 认知引擎配置项 VO — GET {@code /api/v1/cognitive/config} 的列表返回单元。
 *
 * <p>对应 sys_config 表 cognitive-engine 分组一行（active）。
 * 字段命名沿用前端 {@code CognitiveConfigTab} 已有的 snake_case（config_key / config_value），
 * 同时提供 camelCase getter 别名，避免前端类型改造成本。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CognitiveConfigItemVO {

    private final String config_key;
    private final String config_value;
    private final String description;
    private final String config_type;
    private final String config_label;

    public CognitiveConfigItemVO(String config_key, String config_value, String description,
                                  String config_type, String config_label) {
        this.config_key = config_key;
        this.config_value = config_value;
        this.description = description;
        this.config_type = config_type;
        this.config_label = config_label;
    }

    // ── snake_case 主 getter（前端兼容）──────────────────────────────

    public String getConfig_key() { return config_key; }
    public String getConfig_value() { return config_value; }
    public String getConfig_type() { return config_type; }
    public String getConfig_label() { return config_label; }

    // ── camelCase 别名（统一契约）───────────────────────────────────

    public String getConfigKey() { return config_key; }
    public String getConfigValue() { return config_value; }
    public String getConfigType() { return config_type; }
    public String getConfigLabel() { return config_label; }
    public String getDescription() { return description; }
}
