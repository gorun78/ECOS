package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 告警记录 VO — 对应 {@code ecos_dq.dq_alert_record} 字段（驼峰）。
 *
 * @author PMO-48-C T12
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqAlertVO {

    /** 告警 ID (VARCHAR(36) PK) */
    private String id;

    /** 规则 ID (dq_rule.id) */
    private String ruleId;

    /** 告警级别: P0 / P1 / P2 / P3（severity 映射） */
    private String alertLevel;

    /** 告警类型（rule_type 或 EVALUATOR_ERROR / DATASOURCE_DISCONNECT / PIPELINE_NODE_RETRY） */
    private String alertType;

    /** 资产 ID（scopeId，受 VARCHAR(64) 列截断） */
    private String assetId;

    /** 资产名称 */
    private String assetName;

    /** 规则名称（冗余留痕） */
    private String ruleName;

    /** 告警消息（人可读摘要，已去敏感值） */
    private String message;

    /** 告警负载（脱敏后的 check 诊断位：pass_rate / total_rows / scope 等） */
    private Map<String, Object> payload;

    /** 告警状态: PENDING / NOTIFIED / ACKED / RESOLVED / IGNORED / ESCALATED */
    private String status;

    /** 升级到的告警级别 */
    private String escalatedTo;

    /** 通知次数（判重合并时自增） */
    private Integer notifyCount;

    /** 最近一次推送时间 */
    private LocalDateTime lastNotifyAt;

    /** 确认人 */
    private String ackBy;

    /** 确认时间 */
    private LocalDateTime ackAt;

    /** 处理人 */
    private String resolvedBy;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 处理说明 */
    private String resolvedNote;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
