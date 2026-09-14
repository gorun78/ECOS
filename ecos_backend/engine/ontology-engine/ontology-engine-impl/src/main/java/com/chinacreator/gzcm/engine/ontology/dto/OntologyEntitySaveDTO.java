package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体实体（Ontology Entity）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyService.createEntity / updateEntity} 实际消费的业务语义。
 * 新增时 {@code code} + {@code name} 必填，其余可选；更新时所有字段可空（null 表示不动）。
 */
@Data
public class OntologyEntitySaveDTO {

    /** 实体编码（新增必填；同 ontology 内唯一） */
    private String code;

    /** 实体名称（新增必填） */
    private String name;

    /** 实体描述（可选） */
    private String description;

    /** 实体类型（默认 MASTER） */
    private String entityType;
}
