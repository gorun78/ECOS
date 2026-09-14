package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体动作（Ontology Action）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyActionService.createAction / updateAction} 实际消费的业务语义。
 * 更新语义：null 字段不送更新（与旧 Map {@code containsKey=false} 等价）。
 */
@Data
public class OntologyActionSaveDTO {

    /** 动作编码（新增必填；同 entity 内唯一） */
    private String code;

    /** 动作名称（新增必填） */
    private String name;

    /** 动作类型（默认 CUSTOM） */
    private String actionType;

    /** 动作描述（null=不更新） */
    private String description;

    /**
     * 前置条件。
     *
     * <p>类型保留 {@code Object} 是为了与旧 Map 路径行为等价：service 会用 Jackson
     * 把对象写为 JSON String 后落库；前端可传 Map/List/String。属于"嵌套数据动态结构"，
     * 豁免于"接口强类型 VO"硬约束（只豁免嵌套值，不豁免外层强类型）。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object preconditions;

    /** 后置效果（JSON 序列化后存 DB；null=不更新） */
    private Object effects;

    /** 规则 JSON 原文（透传；null=不更新） */
    private String ruleJson;

    /** 执行策略（默认 SYNC；null=不更新） */
    private String strategy;

    /** 状态（默认 ACTIVE；null=不更新） */
    private String status;
}
