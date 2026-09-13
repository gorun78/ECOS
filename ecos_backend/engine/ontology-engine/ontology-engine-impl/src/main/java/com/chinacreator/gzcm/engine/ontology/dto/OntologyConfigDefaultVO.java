package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本体配置默认值 VO。
 *
 * <p>{@code GET /api/v1/engine/ontology/settings/defaults} 返回，
 * 结构为 {@code Map<configKey, configValue>}（最多 13 项默认项）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyConfigDefaultVO {

    /** 默认配置表（key=configKey） */
    private Map<String, String> defaults = new LinkedHashMap<>();
}
