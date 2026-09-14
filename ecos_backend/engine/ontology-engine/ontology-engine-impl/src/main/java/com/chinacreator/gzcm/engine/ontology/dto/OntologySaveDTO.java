package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体（Ontology）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyService.createOntology / updateOntology} 实际消费的业务语义：
 * <ul>
 *   <li>code        — 本体编码（新增必填）</li>
 *   <li>name        — 本体名称</li>
 *   <li>description — 本体描述</li>
 *   <li>status      — 状态（仅更新时使用）</li>
 * </ul>
 */
@Data
public class OntologySaveDTO {

    /** 本体编码（新增时必填） */
    private String code;

    /** 本体名称 */
    private String name;

    /** 本体描述（可选） */
    private String description;

    /** 状态（仅 update 时使用，如 ACTIVE / ARCHIVED） */
    private String status;
}
