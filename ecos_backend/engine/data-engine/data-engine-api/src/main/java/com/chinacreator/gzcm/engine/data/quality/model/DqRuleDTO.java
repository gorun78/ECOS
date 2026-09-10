package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 规则 DTO — 规则保存/更新入参载体（Phase 2 写端点用，本 Phase 仅定义）。
 *
 * @author PMO-48-A T3
 */
@Data
public class DqRuleDTO {

    /** 规则 ID (VARCHAR(64) PK, 创建时为空) */
    private String id;

    /** 规则名称 */
    private String ruleName;

    /** 规则编码 (UNIQUE) */
    private String ruleCode;

    /** 规则类别: BUSINESS / TECHNICAL / COMPLIANCE */
    private String category;

    /** 业务域 */
    private String domain;

    /** 规则类型 (如 NOT_NULL / UNIQUE / FORMAT ...) */
    private String ruleType;

    /** 严重级别: HIGH / MEDIUM / LOW */
    private String severity;

    /** 目标类型: TABLE / FIELD / PIPELINE */
    private String targetKind;

    /** 目标 ID */
    private String targetId;

    /** 目标表 */
    private String targetTable;

    /** 目标字段 */
    private String targetField;

    /** 目标 Pipeline ID */
    private String targetPipelineId;

    /** 规则参数 JSON 字符串 (JSONB) */
    private String parameters;

    /** 规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED */
    private String status;

    /** 审批人 */
    private String approvedBy;

    /** 生效时间戳 (epoch ms) */
    private Long effectiveDate;

    /** 失效时间戳 (epoch ms) */
    private Long expiryDate;

    /** 规则来源: MANUAL / LEGACY_V1 / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC */
    private String sourceType;

    /** 来源追溯 */
    private String sourceRef;

    /** 备注说明 */
    private String description;

    /** 操作人 ID（落 created_by / updated_by） */
    private String operator;
}
