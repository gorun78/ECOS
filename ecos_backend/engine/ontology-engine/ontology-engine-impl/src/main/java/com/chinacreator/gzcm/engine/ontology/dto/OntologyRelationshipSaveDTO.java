package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体关系（Ontology Relationship）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyService.createRelationship} 实际消费的业务语义。
 * <p>sourceEntityId 通过 PATH 传入，不重复声明；targetEntityId + code + name + 关系类型为 body 字段。
 */
@Data
public class OntologyRelationshipSaveDTO {

    /** 源实体 id（在 body 形式 {@code POST /relationships} 必填；path 形式 {@code /entities/{id}/relationships} 由 path 填入） */
    private String sourceEntityId;

    /** 目标实体 id（必填） */
    private String targetEntityId;

    /** 关系编码（可选；同 source→target 内唯一，缺省为空串） */
    private String code;

    /** 关系名称（可选） */
    private String name;

    /** 关系类型（默认 ONE_TO_MANY） */
    private String relationshipType;
}
