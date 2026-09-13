package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体对象来源 — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologySourceController.listSources} 实际 SELECT 列：
 * ontologyId / sourceDomain / originManifest / createdAt，
 * 来自 {@code ecos_entity_table_mapping} + {@code ecos_ontology_entity} +
 * {@code ecos_domain} 三表 JOIN。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologySourceVO {

    /** 本体对象 ID（e.id） */
    private String ontologyId;

    /** 来源域 code（d.code） */
    private String sourceDomain;

    /** 原始表名（m.resource_name） */
    private String originManifest;

    /** 创建时间（e.created_at，ISO / LocalDateTime 序列化字符串） */
    private String createdAt;
}
