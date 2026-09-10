package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 规则列表查询条件 — DqGovernanceController 列表端点入参。
 * <p>全部字段可选，null/空串 表示不过滤；pageNum/pageSize 缺省 1/20。</p>
 *
 * @author PMO-48-A T3
 */
@Data
public class DqRuleQuery {

    /** 规则类别: BUSINESS / TECHNICAL / COMPLIANCE */
    private String category;

    /** 规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED */
    private String status;

    /** 业务域 */
    private String domain;

    /** 规则类型 (如 NOT_NULL / UNIQUE / FORMAT ...) */
    private String ruleType;

    /** 目标类型: TABLE / FIELD / PIPELINE */
    private String targetKind;

    /** 规则名称模糊匹配 (rule_name LIKE %keyword%) */
    private String keyword;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页大小 */
    private Integer pageSize = 20;
}
