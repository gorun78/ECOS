package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体关系（Ontology Relationship）列表项 VO。
 *
 * <p>字段对齐 {@code OntologyService.relToMap} 输出（与 {@code OntologyRepository.REL_MAPPER} 一致）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyRelationshipVO {

    /** 主键（如 rel501） */
    private String id;

    /** 源实体 id */
    private String sourceEntityId;

    /** 目标实体 id */
    private String targetEntityId;

    /** 关系编码（同 source→target 内唯一） */
    private String code;

    /** 关系名称 */
    private String name;

    /** 关系类型（如 ONE_TO_ONE / ONE_TO_MANY / MANY_TO_MANY） */
    private String relationshipType;

    /** 创建时间 ISO 字符串 */
    private String createdAt;
}
