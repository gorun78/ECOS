package com.chinacreator.gzcm.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 系统健康（{@code GET /api/monitor/health}）：
 * {@code database{status[,error]}}、{@code uptime_ms}、{@code version}、{@code status}。
 * {@code error} 仅在数据库探测抛异常时出现（NON_NULL 省略）。
 */
@JsonPropertyOrder({"database", "uptime_ms", "version", "status"})
public record MonitorHealthVO(
        DatabaseStatus database,
        @JsonProperty("uptime_ms") long uptimeMs,
        String version,
        String status) {

    public record DatabaseStatus(
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) String error) {

        public static DatabaseStatus up() {
            return new DatabaseStatus("UP", null);
        }

        public static DatabaseStatus down(String error) {
            return new DatabaseStatus("DOWN", error);
        }
    }
}
