package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.List;

/**
 * 本体自动发现请求 Query。
 *
 * <p>{@code POST /api/v1/ecos/domains/{domainCode}/auto-discover}
 * 请求体字段：
 * <ul>
 *   <li>{@code datasourceId} — 必填，数据源 id</li>
 *   <li>{@code resourceNames} — 必填，待发现资源名列表</li>
 * </ul>
 */
@Data
public class OntologyAutoDiscoverQuery {

    /** 必填，数据源 id */
    private String datasourceId;

    /** 必填，待发现资源名列表 */
    private List<String> resourceNames;
}
