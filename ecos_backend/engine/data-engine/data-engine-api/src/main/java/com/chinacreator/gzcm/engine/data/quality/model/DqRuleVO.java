package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 规则列表摘要 VO — 对应 {@code ecos_dq.dq_rule} 主表字段（驼峰）。
 *
 * @author PMO-48-A T3
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqRuleVO {

    /** 规则 ID (VARCHAR(64) PK) */
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

    /** 规则参数 (JSONB 原样字符串, 由 Service 反序列化并对敏感键脱敏) */
    private String parametersJson;

    /** 规则状态: DRAFT / IN_REVIEW / ACTIVE / DEPRECATED / SUPERSEDED / REJECTED */
    private String status;

    /** 当前版本号 */
    private Integer version;

    /** 审批人 */
    private String approvedBy;

    /** 生效时间戳 (epoch ms) */
    private Long effectiveDate;

    /** 失效时间戳 (epoch ms) */
    private Long expiryDate;

    /** 规则来源: MANUAL / LEGACY_V1 / LEGACY_V2 / LEGACY_QUALITY / KB_SYNC */
    private String sourceType;

    /** 来源追溯 (legacy 表:原始行id) */
    private String sourceRef;

    /** 备注说明 */
    private String description;

    /** 创建人 */
    private String createdBy;

    /** 更新人 */
    private String updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
