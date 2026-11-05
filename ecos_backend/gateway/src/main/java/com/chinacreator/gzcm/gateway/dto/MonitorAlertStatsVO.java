package com.chinacreator.gzcm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * 告警统计（{@code GET /api/monitor/alerts}）：
 * {@code today_by_level}（按级别分组的告警行，属主列动态）、{@code total}、{@code open}。
 */
@JsonPropertyOrder({"today_by_level", "total", "open"})
public record MonitorAlertStatsVO(
        @JsonProperty("today_by_level") List<Map<String, Object>> todayByLevel,
        int total,
        int open) {
}
