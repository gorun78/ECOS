package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体配置缓存刷新结果 VO。
 *
 * <p>{@code POST /api/v1/engine/ontology/settings/refresh} 返回。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyConfigRefreshVO {

    /** 刷新后缓存条目数 */
    private Integer cacheSize;
}
