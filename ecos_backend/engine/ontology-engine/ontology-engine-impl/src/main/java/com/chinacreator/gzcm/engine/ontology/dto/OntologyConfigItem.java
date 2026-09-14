package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体配置批量更新项。
 *
 * <p>{@code PUT /api/v1/engine/ontology/settings} 请求体为
 * {@code List<OntologyConfigItem>}，每项 {@code configKey} +
 * {@code configValue} 必填。
 */
@Data
public class OntologyConfigItem {

    /** 配置 key */
    private String configKey;

    /** 配置 value */
    private String configValue;
}
