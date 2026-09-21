package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

import lombok.Data;

/**
 * 词条（Wiki）新增 / 编辑 SaveDTO。
 *
 * <p>字段对齐 {@code GlossaryService.createTerm / updateTerm} 实际消费的业务语义
 * （与 {@code GlossaryEntity} 字段一致 + 状态流转）。更新时所有字段可空
 * （null 表示不动），状态需走合法流转。
 */
@Data
public class OntologyGlossarySaveDTO {

    /** 词条编码（同 domain 内唯一） */
    private String code;

    /** 词条名称 */
    private String name;

    /** 词条定义 */
    private String definition;

    /** 所属领域（glossary_domain 字典 code） */
    private String domain;

    /** 业务负责人 */
    private String owner;

    /** 状态（DRAFT/REVIEW/PUBLISHED/DEPRECATED，更新时按状态流转） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 词条分类 ENTITY / RELATION / METRIC / FUNCTION / CONCEPT */
    private String termType;

    /** 同义词 / 别名列表 */
    private List<String> aliases;

    /** 引用的本体实体主键 */
    private String objectTypeId;

    /** 上位词条 id */
    private Long parentTermId;

    /** 定义版本号 */
    private Integer version;

    /** 示例值列表 */
    private List<String> examples;

    /** 方法论 / 场景标签 */
    private List<String> tags;
}