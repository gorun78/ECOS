package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体规则（Ontology Rule）列表项 VO。
 *
 * <p>字段对齐 {@code OntologyRuleService.toMap} 输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyRuleVO {

    /** 主键（如 rule1001） */
    private String id;

    /** 所属实体 id */
    private String entityId;

    /** 规则编码（同 entity 内唯一） */
    private String code;

    /** 规则名称 */
    private String name;

    /** 规则类型（VALIDATION / CALCULATION / DECISION / AGENT） */
    private String ruleType;

    /** 规则表达式（如 {@code age > 0 AND SET(approved)}） */
    private String expression;

    /** 规则动作（如 APPROVE / NOTIFY） */
    private String action;

    /** 优先级（数值越大越先执行） */
    private Integer priority;

    /** 启用标志（1=启用 0=禁用） */
    private Integer enabled;

    /** 规则描述 */
    private String description;

    /** 规则载体（I-4 消歧名；闭集 ONTOLOGY_ELEMENT/RULE_OBJECT/KNOWLEDGE_ASSET） */
    private String ruleCarrier;

    /** 规则层（闭集 STRUCTURAL/VALIDATION/BUSINESS/POLICY/COGNITIVE，与 ruleType 正交） */
    private String ruleLayer;

    /** 作用域（租户/组织/领域；发布就绪件套之一） */
    private String scope;

    /** 版本号（发布就绪件套之一） */
    private String versionNo;

    /** Git 归档引用（发布就绪件套之一） */
    private String gitRef;

    /** 生命周期状态（ACTIVE/PUBLISHED/ARCHIVED；16 态单源属 #147D） */
    private String status;

    /** 领域归属 */
    private String domain;

    /** 创建时间 ISO 字符串（live 列 create_time） */
    private String createdAt;

    /** 更新时间 ISO 字符串（live 列 update_time） */
    private String updatedAt;
}
