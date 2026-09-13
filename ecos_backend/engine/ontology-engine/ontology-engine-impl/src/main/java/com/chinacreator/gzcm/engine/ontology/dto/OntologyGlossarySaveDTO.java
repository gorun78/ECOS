package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 术语库新增/编辑 SaveDTO。
 *
 * <p>字段对齐 {@code GlossaryController.createTerm / updateTerm} 实际
 * 消费的业务语义（与 {@code GlossaryEntity} 字段一致 + 状态流转）。
 * 更新时所有字段可空（null 表示不动），状态需走合法流转。
 */
@Data
public class OntologyGlossarySaveDTO {

    /** 术语编码（同 domain 内唯一） */
    private String code;

    /** 术语名称 */
    private String name;

    /** 术语定义 */
    private String definition;

    /** 所属领域 */
    private String domain;

    /** 业务负责人 */
    private String owner;

    /** 状态（DRAFT/REVIEW/PUBLISHED/DEPRECATED，更新时按状态流转） */
    private String status;

    /** 创建人 */
    private String createdBy;
}
