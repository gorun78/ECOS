package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.List;

import lombok.Data;

/**
 * DQ 监控调度计划入参 DTO — 创建/更新共用（PMO-48-C T11）。
 *
 * <p>字段语义与 {@code ecos_dq.dq_schedule} 对齐：</p>
 * <ul>
 *   <li>{@code triggerType} 三类触发: SCHEDULE(定时) / EVENT(Pipeline 事件) / MANUAL(仅手动)</li>
 *   <li>{@code ruleIds} 关联的 dq_rule.id 列表（JSONB 数组），批内规则需为 ACTIVE</li>
 *   <li>{@code scopeType}/{@code scopeId} 限流作用域，缺省 DATASOURCE（用于 dq_throttle 防雪崩）</li>
 * </ul>
 *
 * @author PMO-48-C T11
 */
@Data
public class DqScheduleDTO {

    /** 计划名称（必填，≤191） */
    private String name;

    /** 触发类型: SCHEDULE / EVENT / MANUAL（必填） */
    private String triggerType;

    /** cron 表达式（triggerType=SCHEDULE 时必填） */
    private String cronExpression;

    /** 事件类型常量（triggerType=EVENT 时必填，如 PIPELINE_EXECUTION_SUCCEEDED） */
    private String eventType;

    /** 关联规则 ID 列表（dq_rule.id） */
    private List<String> ruleIds;

    /** 限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM（缺省 DATASOURCE） */
    private String scopeType;

    /** 限流作用域 ID（与 dq_throttle.scope_id 对齐） */
    private String scopeId;

    /** 启用开关（缺省 true） */
    private Boolean enabled;

    /** 单次执行超时秒数（缺省 60） */
    private Integer maxRuntimeSeconds;
}
