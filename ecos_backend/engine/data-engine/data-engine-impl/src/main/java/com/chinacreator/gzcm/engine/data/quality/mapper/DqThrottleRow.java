package com.chinacreator.gzcm.engine.data.quality.mapper;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 限流行映射 — 对应 {@code ecos_dq.dq_throttle}（PMO-48-C T11 内部映射，不落 api 契约）。
 *
 * @author PMO-48-C T11
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqThrottleRow {

    /** 作用域类型: FIELD/TABLE/DATASOURCE/DOMAIN/SYSTEM */
    private String scopeType;

    /** 作用域 ID */
    private String scopeId;

    /** 每日检查上限 */
    private Integer maxPerDay;

    /** 当前已执行检查数 */
    private Integer currentCount;

    /** 计数重置时点（跨日判定） */
    private LocalDateTime resetAt;
}
