package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 监控调度计划 VO — 对应 {@code ecos_dq.dq_schedule}（PMO-48-C T11）。
 *
 * <p>ruleIds 列 JSONB 由 Mapper 层反序列化为 {@code List<String>}。</p>
 *
 * @author PMO-48-C T11
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqScheduleVO {

    /** 调度 ID (VARCHAR(64) PK) */
    private String id;

    /** 计划名称 */
    private String name;

    /** 触发类型: SCHEDULE / EVENT / MANUAL */
    private String triggerType;

    /** cron 表达式（triggerType=SCHEDULE） */
    private String cronExpression;

    /** 事件类型常量（triggerType=EVENT） */
    private String eventType;

    /** 关联规则 ID 列表 */
    private List<String> ruleIds;

    /** 限流作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM */
    private String scopeType;

    /** 限流作用域 ID */
    private String scopeId;

    /** 启用开关 */
    private Boolean enabled;

    /** 单次执行超时秒数 */
    private Integer maxRuntimeSeconds;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
