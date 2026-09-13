package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体对象类型（Ontology Object / Entity）新增/编辑 DTO — T16-2（备用）。
 *
 * <p>由 {@code OntologyDomainApiController} 的 {@code POST/PUT /api/v1/ontology/objects}
 * 强类型入参承载。与 T16-1 既有 {@link OntologyEntitySaveDTO} 字段对齐，
 * 本类保留以区分"对象"业务语义（避免 Api 直接复用 {@code OntologyEntitySaveDTO}
 * 失去读意）；实际字段语义同。
 */
@Data
public class OntologyObjectSaveDTO {

    /** 实体编码（必填） */
    private String code;

    /** 实体名称（必填） */
    private String name;

    /** 实体描述（可选） */
    private String description;

    /** 实体类型（可选，默认 MASTER） */
    private String entityType;

    /** 所属本体 id（可选，默认 ont001） */
    private String ontologyId;

    /** 所属域 id（可选） */
    private String domainId;
}
