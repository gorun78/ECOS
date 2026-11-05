package com.chinacreator.gzcm.engine.kb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 同步触发回执（全量 / 单类型两腿同表）：
 * 全量腿 {@code syncId/jobId/status}；单类型腿 {@code syncId/jobId/objectType/status}。
 * {@code objectType} 为条件键（NON_NULL 省略），保持两腿各自键面。
 */
@JsonPropertyOrder({"syncId", "jobId", "objectType", "status"})
public record KgSyncTriggerVO(
        String syncId,
        String jobId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String objectType,
        String status) {
}
