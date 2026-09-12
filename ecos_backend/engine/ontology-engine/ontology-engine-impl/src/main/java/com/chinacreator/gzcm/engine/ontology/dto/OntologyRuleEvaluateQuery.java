package com.chinacreator.gzcm.engine.ontology.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量评估规则 — {@code POST /rules/evaluate} 入参。
 *
 * <p>字段对齐 {@code OntologyRuleController.evaluateRules} 实际消费的 {@code entityIds}。
 */
@Data
public class OntologyRuleEvaluateQuery {

    /** 参与评估的实体 id 列表 */
    private List<String> entityIds;
}
