package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * P3-5 遥测查询 API。
 */
@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry", description = "遥测查询 — Trace 查询、Token 用量统计")
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final JdbcTemplate jdbc;

    public TelemetryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "遥测系统状态", description = "检查遥测系统的数据库连接和数据存储状态")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "ecos-telemetry");
        status.put("status", "UP");
        status.put("timestamp", Instant.now().toString());

        try {
            Long spanCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_spans", Long.class);
            Long tokenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_token_usage", Long.class);
            status.put("spans_stored", spanCount != null ? spanCount : 0L);
            status.put("token_records", tokenCount != null ? tokenCount : 0L);
            status.put("db", "connected");
        } catch (Exception e) {
            status.put("db", "error: " + e.getMessage());
        }

        return ApiResponse.success(status);
    }

    @Operation(summary = "Trace 列表", description = "获取最近的 Trace 列表")
    @GetMapping("/traces")
    public ApiResponse<List<Map<String, Object>>> getTraces(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 100);
        try {
            List<Map<String, Object>> traces = jdbc.queryForList(
                "SELECT DISTINCT ON (trace_id) " +
                "  trace_id, " +
                "  MIN(operation_name) AS first_operation, " +
                "  COUNT(*) AS span_count, " +
                "  MAX(http_status) AS max_status, " +
                "  SUM(duration_ms) AS total_duration_ms, " +
                "  MIN(start_time) AS first_start, " +
                "  MAX(end_time) AS last_end " +
                "FROM ecos_spans " +
                "GROUP BY trace_id " +
                "ORDER BY trace_id DESC " +
                "LIMIT ?", limit);
            return ApiResponse.success(traces);
        } catch (Exception e) {
            log.warn("Failed to query traces: {}", e.getMessage());
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @Operation(summary = "Trace 详情", description = "获取指定 Trace 的所有 Span 详情")
    @GetMapping("/traces/{traceId}")
    public ApiResponse<List<Map<String, Object>>> getTraceDetail(
            @PathVariable String traceId) {
        try {
            List<Map<String, Object>> spans = jdbc.queryForList(
                "SELECT span_id, trace_id, parent_span_id, operation_name, " +
                "       service_name, http_method, http_path, http_status, " +
                "       start_time, end_time, duration_ms, status, attributes " +
                "FROM ecos_spans " +
                "WHERE trace_id = ? " +
                "ORDER BY start_time ASC", traceId);
            return ApiResponse.success(spans);
        } catch (Exception e) {
            log.warn("Failed to query trace detail: {}", e.getMessage());
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @Operation(summary = "Token 用量汇总", description = "按天汇总 LLM Token 的使用量")
    @GetMapping("/tokens/summary")
    public ApiResponse<Map<String, Object>> getTokenSummary(
            @RequestParam(defaultValue = "7d") String range) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("range", range);

        try {
            int days = 7;
            if (range.endsWith("d")) {
                days = Integer.parseInt(range.replace("d", ""));
            }

            Map<String, Object> totals = jdbc.queryForMap(
                "SELECT " +
                "  COALESCE(SUM(prompt_tokens), 0) AS total_prompt, " +
                "  COALESCE(SUM(completion_tokens), 0) AS total_completion, " +
                "  COALESCE(SUM(total_tokens), 0) AS grand_total, " +
                "  COUNT(*) AS record_count " +
                "FROM ecos_token_usage " +
                "WHERE created_at >= NOW() - (? || ' days')::INTERVAL", days);
            summary.put("totals", totals);

            List<Map<String, Object>> byModel = jdbc.queryForList(
                "SELECT model, " +
                "  SUM(prompt_tokens) AS prompt, " +
                "  SUM(completion_tokens) AS completion, " +
                "  SUM(total_tokens) AS total, " +
                "  COUNT(*) AS calls " +
                "FROM ecos_token_usage " +
                "WHERE created_at >= NOW() - (? || ' days')::INTERVAL " +
                "GROUP BY model " +
                "ORDER BY total DESC", days);
            summary.put("by_model", byModel);

            List<Map<String, Object>> byOperation = jdbc.queryForList(
                "SELECT operation, " +
                "  SUM(total_tokens) AS total, " +
                "  COUNT(*) AS calls " +
                "FROM ecos_token_usage " +
                "WHERE created_at >= NOW() - (? || ' days')::INTERVAL " +
                "GROUP BY operation " +
                "ORDER BY total DESC", days);
            summary.put("by_operation", byOperation);

        } catch (Exception e) {
            log.warn("Failed to query token summary: {}", e.getMessage());
            summary.put("error", e.getMessage());
        }

        return ApiResponse.success(summary);
    }
}
