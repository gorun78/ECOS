package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/**
 * 本体自动发现结果 VO。
 *
 * <p>{@code POST /api/v1/ecos/domains/{domainCode}/auto-discover}
 * 返回的 List 单元，字段对齐 {@code AutoDiscoverService.autoDiscover}
 * 内 {@code result} Map（entityCode / entityName / domainCode /
 * propertyCount / mapping）。
 *
 * <p>{@code mapping} 为 {@code createMapping} 返回的 PG 行 Map
 * （ecos_entity_table_mapping SELECT * 直出），动态嵌套数据豁免
 * （字段随 schema 演化，保留 raw Map）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyAutoDiscoverResultVO {

    /** 自动生成的本体内实体编码 */
    private String entityCode;

    /** 原资源名（表名） */
    private String entityName;

    /** 所属域 code */
    private String domainCode;

    /** 生成属性数量 */
    private Integer propertyCount;

    /** 实体表映射记录（PG 行 Map，动态 payload 豁免） */
    private Map<String, Object> mapping;
}
