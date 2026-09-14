package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * DQ 工单 VO — 对应 {@code ecos_dq.dq_work_order} 字段（驼峰）。
 *
 * <p>T12 骨架：基础字段（状态/级别/处理模式/指派/修复/验证/重试/时间）。</p>
 * <p>T13 扩展：RCA 字段（rcaResult / rcaConfidence / rcaAnalyzedAt）+ verifyNote /
 * preventiveActions（DB schema V111 已定义，T13 状态机需读写）。</p>
 *
 * @author PMO-48-C T12/T13
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqWorkOrderVO {

    /** 工单 ID (VARCHAR(64) PK) */
    private String id;

    /** 工单号 (UNIQUE, WO-yyyyMMdd-NNN) */
    private String orderNo;

    /** 关联告警 ID (dq_alert_record.id) */
    private String alertId;

    /** 关联规则 ID (dq_rule.id) */
    private String ruleId;

    /** 资产 ID */
    private String assetId;

    /** 工单标题 */
    private String title;

    /** 工单描述 */
    private String description;

    /** 工单状态: PENDING / ASSIGNED / IN_WORK / RESOLVED / VERIFIED / CLOSED / REJECTED */
    private String status;

    /** 处理模式: MANUAL / AUTO_REPAIR / EXEMPT */
    private String handlingMode;

    /** 严重级别（规则 severity 快照） */
    private String severity;

    /** 指派人 */
    private String assignedTo;

    /** 指派时间 */
    private LocalDateTime assignedAt;

    /** 修复动作（白名单场景码，如 DATASOURCE_DISCONNECT） */
    private String repairAction;

    /** 修复状态: SUCCESS / FAILED / DEGRADED（未修复为 null） */
    private String repairStatus;

    /** 修复日志（JSON 文本：重试记录/异常摘要） */
    private String repairLog;

    /** 验证人 */
    private String verifiedBy;

    /** 验证时间 */
    private LocalDateTime verifiedAt;

    /** 是否验证通过 */
    private Boolean verifyPass;

    /** 验证备注（验证失败原因 / 通过说明） */
    private String verifyNote;

    /** 处理人 */
    private String resolvedBy;

    /** 处理完成时间 */
    private LocalDateTime resolvedAt;

    /** 处理说明 */
    private String resolutionNote;

    /** 预防性措施（JSONB，Phase 4 闭环评估增加） */
    private Map<String, Object> preventiveActions;

    /** RCA 结果（JSONB 文本；stub 阶段含 rootCause=STUB:xxx / confidence=0.0 / causalChain=[]） */
    private String rcaResult;

    /** RCA 置信度 0.0 ~ 1.0 */
    private Double rcaConfidence;

    /** RCA 完成时间 */
    private LocalDateTime rcaAnalyzedAt;

    /** 自动修复尝试次数（verify 失败自增；达 3 → 强制 handling_mode=MANUAL） */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    // ==================== 工单状态机 7 态常量（T13 复用） ====================

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_IN_WORK = "IN_WORK";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String MODE_MANUAL = "MANUAL";
    public static final String MODE_AUTO_REPAIR = "AUTO_REPAIR";
    public static final String MODE_EXEMPT = "EXEMPT";

    public static final String SEV_CRITICAL = "CRITICAL";
    public static final String SEV_HIGH = "HIGH";
    public static final String SEV_MEDIUM = "MEDIUM";
    public static final String SEV_LOW = "LOW";
}
