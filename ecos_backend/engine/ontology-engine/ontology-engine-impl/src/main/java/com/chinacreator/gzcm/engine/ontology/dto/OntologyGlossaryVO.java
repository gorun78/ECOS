package com.chinacreator.gzcm.engine.ontology.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 词条（Wiki）列表项 / 详情 VO。
 *
 * <p>字段对齐 {@code GlossaryService} 输出（与 {@code GlossaryEntity} 字段一致，
 * 时间字段序列化为 ISO 字符串）。V143 起补入 7 个语义字段。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyGlossaryVO {

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

    /** 状态（DRAFT/REVIEW/PUBLISHED/DEPRECATED） */
    private String status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;

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

    /** 是否为所属本体实体的主术语（V144） */
    private Boolean isPrimary;
}