package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体规则（Ontology Rule）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyRuleService.createRule / updateRule} 实际消费的业务语义。
 * 不更新字段使用 null（service 内 hasKey 语义判断是否送更新）。
 */
@Data
public class OntologyRuleSaveDTO {

    /** 规则编码（新增必填；同 entity 内唯一） */
    private String code;

    /** 规则名称（新增必填） */
    private String name;

    /** 规则类型（默认 VALIDATION） */
    private String ruleType;

    /** 规则表达式（null=不更新） */
    private String expression;

    /** 规则动作（null=不更新） */
    private String action;

    /** 优先级（null=不更新，新增默认 0） */
    private Integer priority;

    /** 启用标志（null=不更新，新增默认 1） */
    private Integer enabled;

    /** 规则描述（null=不更新） */
    private String description;
}
