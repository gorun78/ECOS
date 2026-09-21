package com.chinacreator.gzcm.engine.ontology.glossary;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 词条（Wiki）持久化实体 — 对应 ecos_glossary_term 表。
 *
 * <p>原为「名词解释字典」（仅 name/definition/domain/status），V143 起扩展 7 个
 * 语义字段（分类/别名/示例/标签/版本/所属实体/上位词条），使其成为可被本体
 * 模型引用与检索的语义资产。
 */
@Getter
@Setter
@NoArgsConstructor
public class GlossaryEntity {

    /** 主键 */
    private Long id;

    /** 词条编码 */
    private String code;

    /** 词条名称 */
    private String name;

    /** 词条定义 */
    private String definition;

    /** 所属领域（glossary_domain 字典 code） */
    private String domain;

    /** 业务负责人 */
    private String owner;

    /** 状态 DRAFT / REVIEW / PUBLISHED / DEPRECATED */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 词条分类 ENTITY / RELATION / METRIC / FUNCTION / CONCEPT */
    private String termType;

    /** 同义词 / 别名列表 */
    private List<String> aliases;

    /** 引用的本体实体主键（ecos_ontology_entity.id） */
    private String objectTypeId;

    /** 上位词条 id（自引用） */
    private Long parentTermId;

    /** 定义版本号 */
    private Integer version;

    /** 示例值列表 */
    private List<String> examples;

    /** 方法论 / 场景标签 */
    private List<String> tags;

    /** 是否为其所属本体实体（objectTypeId）的主术语（V144，每实体至多一条） */
    private Boolean isPrimary;
}