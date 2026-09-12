package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 规则测试/评估 VO — {@code POST /rules/{id}/test}（单条）
 * 与 {@code POST /rules/evaluate}（批量）返回单元。
 *
 * <p>字段对齐 {@code OntologyRuleService.testRule / evaluateRules} 输出：
 * <ul>
 *   <li>{@code ruleId / code / expression / ruleType / enabled / parsable}（testRule 主输出）</li>
 *   <li>{@code entityId}（仅 evaluateRules 携带，testRule 时为 null）</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyRuleEvaluationVO {

    /** 规则主键 */
    private String ruleId;

    /** 规则编码 */
    private String code;

    /** 规则表达式 */
    private String expression;

    /** 规则类型 */
    private String ruleType;

    /** 启用标志（仅 testRule 路径写入；Boolean，便于前端直接消费） */
    private Boolean enabled;

    /** 表达式可解析（true=至少包含比较/逻辑关键字） */
    private boolean parsable;

    /** 关联实体 id（仅 evaluateRules 路径写入；testRule 时为 null） */
    private String entityId;
}
