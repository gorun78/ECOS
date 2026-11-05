package com.chinacreator.gzcm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Map;

/**
 * 监控仪表盘（{@code GET /api/monitor}）载荷。
 *
 * <p>键面/顺序：{@code system{ cpu_cores, cpu_load, heap_used_mb, heap_max_mb }}、
 * {@code active_alerts}、{@code alerts_today}、{@code recent_alerts}、{@code open_dq_issues}、
 * {@code chartData[{time,cpu,memory}]}、{@code processes[{name,type,uptime,items,status}]}。
 * {@code recent_alerts} 为告警表动态列（属主 {@code MonitorService} 行，不冻结）。</p>
 */
@JsonPropertyOrder({"system", "active_alerts", "alerts_today", "recent_alerts", "open_dq_issues",
        "chartData", "processes"})
public record MonitorDashboardVO(
        SystemStats system,
        @JsonProperty("active_alerts") int activeAlerts,
        @JsonProperty("alerts_today") int alertsToday,
        @JsonProperty("recent_alerts") List<Map<String, Object>> recentAlerts,
        @JsonProperty("open_dq_issues") int openDqIssues,
        List<ChartPoint> chartData,
        List<ProcessRow> processes) {

    @JsonPropertyOrder({"cpu_cores", "cpu_load", "heap_used_mb", "heap_max_mb"})
    public record SystemStats(
            @JsonProperty("cpu_cores") int cpuCores,
            @JsonProperty("cpu_load") String cpuLoad,
            @JsonProperty("heap_used_mb") long heapUsedMb,
            @JsonProperty("heap_max_mb") long heapMaxMb) {
    }

    public record ChartPoint(String time, double cpu, double memory) {
    }

    public record ProcessRow(String name, String type, String uptime, int items, String status) {
    }
}
