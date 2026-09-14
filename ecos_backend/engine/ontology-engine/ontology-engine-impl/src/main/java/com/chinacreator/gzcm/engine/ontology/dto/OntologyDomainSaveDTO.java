package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体领域（Ontology Domain）新增/编辑 DTO — T16-2。
 *
 * <p>字段对应 {@code OntologyDomainService.createDomain / updateDomain} 实际消费的业务语义：
 * 新增时 {@code code} + {@code name} 必填，其余可选；更新时所有字段可空（null 表示不动）。
 */
@Data
public class OntologyDomainSaveDTO {

    /** 领域编码（新增必填；唯一） */
    private String code;

    /** 领域名称（新增必填） */
    private String name;

    /** 所属 owner（可选） */
    private String owner;

    /** 领域描述（可选） */
    private String description;

    /** 状态（Draft / Published / Deprecated；可选，更新时 null 表示不动） */
    private String status;
}
