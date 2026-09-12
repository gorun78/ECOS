package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体动作（Ontology Action）列表项 VO。
 *
 * <p>字段对齐 {@code OntologyActionService.actionToMap} 输出。
 * <p>{@code preconditions} / {@code effects} / {@code validationRules} 为 JSON 解析后的
 * 结构化值（{@code Object}），保留 Map 输出语义便于前端直接渲染；不属于"接口强类型 VO"违规。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyActionVO {

    /** 主键（如 act101） */
    private String id;

    /** 所属实体 id */
    private String entityId;

    /** 动作编码（同 entity 内唯一） */
    private String code;

    /** 动作名称 */
    private String name;

    /** 动作类型（如 CREATE / UPDATE / DELETE / NOTIFY / CUSTOM） */
    private String actionType;

    /** 动作描述 */
    private String description;

    /** 前置条件（JSON 解析后对象） */
    private Object preconditions;

    /** 后置效果（JSON 解析后对象） */
    private Object effects;

    /** 规则 JSON 原文（透传） */
    private String ruleJson;

    /** 校验规则（由 ruleJson 解析，结构同 ruleJson） */
    private Object validationRules;

    /** 执行策略（如 SYNC / ASYNC） */
    private String strategy;

    /** 状态（如 ACTIVE / ARCHIVED） */
    private String status;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
