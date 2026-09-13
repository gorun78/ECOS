package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体对象来源查询 Query — T16-2。
 *
 * <p>{@code GET /api/v1/ecos/ontology/sources} 参数：
 * {@code domainCode} 可选（默认全量）、{@code limit} 默认 100、上限 500。
 */
@Data
public class OntologySourceQuery {

    /** 可选，按业务域 code 过滤 */
    private String domainCode;

    /** 返回行数上限，默认 100，最大 500 */
    private Integer limit;
}
