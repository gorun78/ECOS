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

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
