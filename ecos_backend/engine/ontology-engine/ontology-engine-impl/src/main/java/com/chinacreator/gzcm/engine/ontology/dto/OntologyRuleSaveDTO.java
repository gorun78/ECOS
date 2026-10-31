package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

/**
 * 本体规则（Ontology Rule）新增/编辑 DTO。
 *
 * <p>字段对应 {@code OntologyRuleService.createRule / updateRule} 实际消费的业务语义。
 * 不更新字段使用 null（service 内 hasKey 语义判断是否送更新）。
 *
 * <p>#147B（R10/R11 契约面 I-3/I-4/§6.2.6⑧）新增双维路由与发布就绪件套：
 * {@code ruleCarrier/ruleLayer} 值域闭集校验（非法值拒 400 ONT-030）；
 * {@code status="PUBLISHED"} 触发发布前置门——五字段（rule_carrier/rule_layer/
 * scope/version_no/git_ref）与 rule_type×rule_layer 正交登记全量就绪方可发布，
 * 缺 version_no/git_ref 或 rule_layer 空即拒发布（400 ONT-031，C-9/DF-5）。</p>
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

    /** 规则载体（I-4 消歧名；闭集 ONTOLOGY_ELEMENT/RULE_OBJECT/KNOWLEDGE_ASSET；null=不更新） */
    private String ruleCarrier;

    /** 规则层（闭集 STRUCTURAL/VALIDATION/BUSINESS/POLICY/COGNITIVE，与 ruleType 正交；null=不更新） */
    private String ruleLayer;

    /** 作用域（租户/组织/领域；发布就绪件套之一；null=不更新） */
    private String scope;

    /** 版本号（发布就绪件套之一，缺则拒发布；null=不更新） */
    private String versionNo;

    /** Git 归档引用（发布就绪件套之一，缺则拒发布；null=不更新） */
    private String gitRef;

    /** 生命周期状态（"PUBLISHED" 触发发布前置门；null=不更新） */
    private String status;
}
