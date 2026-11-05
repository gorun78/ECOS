package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.dto.TelemetryHealthVO;
import com.chinacreator.gzcm.gateway.dto.TelemetryTokenSummaryVO;
import com.chinacreator.gzcm.gateway.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @Operation(summary = "遥测系统状态", description = "检查遥测系统的数据库连接和数据存储状态")
    @GetMapping("/health")
    public ApiResponse<TelemetryHealthVO> health() {
        String service = "ecos-telemetry";
        String ts = Instant.now().toString();
        try {
            var counts = telemetryService.queryHealthCounts();
            return ApiResponse.success(new TelemetryHealthVO(
                    service, "UP", ts,
                    counts.get("spans_stored"), counts.get("token_records"), "connected"));
        } catch (Exception e) {
            return ApiResponse.success(new TelemetryHealthVO(
                    service, "UP", ts, null, null, "error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Trace 列表", description = "获取最近的 Trace 列表")
    @GetMapping("/traces")
    public ApiResponse<List<Map<String, Object>>> getTraces(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 100);
        try {
            List<Map<String, Object>> traces = telemetryService.queryTraces(limit);
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
            List<Map<String, Object>> spans = telemetryService.queryTraceDetail(traceId);
            return ApiResponse.success(spans);
        } catch (Exception e) {
            log.warn("Failed to query trace detail: {}", e.getMessage());
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @Operation(summary = "Token 用量汇总", description = "按天汇总 LLM Token 的使用量")
    @GetMapping("/tokens/summary")
    public ApiResponse<TelemetryTokenSummaryVO> getTokenSummary(
            @RequestParam(defaultValue = "7d") String range) {
        try {
            int days = 7;
            if (range.endsWith("d")) {
                days = Integer.parseInt(range.replace("d", ""));
            }

            var totals = telemetryService.queryTokenSummaryTotals(days);
            var byModel = telemetryService.queryTokenSummaryByModel(days);
            var byOperation = telemetryService.queryTokenSummaryByOperation(days);

            return ApiResponse.success(new TelemetryTokenSummaryVO(
                    range, totals, byModel, byOperation, null));
        } catch (Exception e) {
            log.warn("Failed to query token summary: {}", e.getMessage());
            return ApiResponse.success(new TelemetryTokenSummaryVO(
                    range, null, null, null, e.getMessage()));
        }
    }
}
