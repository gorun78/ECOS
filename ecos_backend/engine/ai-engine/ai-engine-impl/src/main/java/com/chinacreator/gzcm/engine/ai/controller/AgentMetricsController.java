package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 指标查询控制器 — 读取 ecos_agent_metrics 和 ecos_agent_alert 表。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /api/v1/aip/agent-metrics/{agentId} — Agent 指标汇总</li>
 *   <li>GET /api/v1/aip/agent-metrics/{agentId}/errors — Agent 错误/告警列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip/agent-metrics")
public class AgentMetricsController {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsController.class);

    private final JdbcTemplate jdbc;

    public AgentMetricsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/aip/agent-metrics/{agentId} — Agent 指标汇总
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询 Agent 的指标汇总数据。
     *
     * <p>返回: agentId, 总请求数, 成功/失败数, 成功率, 平均/P50/P99 耗时, 总 token。</p>
     */
    @GetMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> getMetrics(@PathVariable String agentId) {
        try {
            // 总计
            Map<String, Object> total = jdbc.queryForMap(
                "SELECT " +
                "  COUNT(*) AS total_count, " +
                "  COALESCE(SUM(CASE WHEN success THEN 1 ELSE 0 END), 0) AS success_count, " +
                "  COALESCE(SUM(CASE WHEN NOT success THEN 1 ELSE 0 END), 0) AS failure_count, " +
                "  ROUND(AVG(elapsed_ms)::numeric, 1) AS avg_elapsed_ms, " +
                "  COALESCE(SUM(tokens_in), 0) AS total_tokens_in, " +
                "  COALESCE(SUM(tokens_out), 0) AS total_tokens_out " +
                "FROM ecos_agent_metrics WHERE agent_id = ?",
                agentId
            );

            // P50 / P99 — 需要单独查询
            Map<String, Object> pct = jdbc.queryForMap(
                "SELECT " +
                "  COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p50_ms, " +
                "  COALESCE(percentile_cont(0.99) WITHIN GROUP (ORDER BY elapsed_ms), 0)::bigint AS p99_ms " +
                "FROM ecos_agent_metrics WHERE agent_id = ?",
                agentId
            );

            // 成功率
            long totalCount = ((Number) total.getOrDefault("total_count", 0L)).longValue();
            long successCount = ((Number) total.getOrDefault("success_count", 0L)).longValue();
            double successRate = totalCount > 0
                ? Math.round(successCount * 10000.0 / totalCount) / 100.0
                : 0.0;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("agentId", agentId);
            data.put("totalCount", totalCount);
            data.put("successCount", successCount);
            data.put("failureCount", ((Number) total.getOrDefault("failure_count", 0L)).longValue());
            data.put("successRate", successRate);
            data.put("avgElapsedMs", total.get("avg_elapsed_ms"));
            data.put("p50Ms", pct.get("p50_ms"));
            data.put("p99Ms", pct.get("p99_ms"));
            data.put("totalTokensIn", ((Number) total.getOrDefault("total_tokens_in", 0L)).longValue());
            data.put("totalTokensOut", ((Number) total.getOrDefault("total_tokens_out", 0L)).longValue());

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to get metrics for agentId={}", agentId, e);
            return ApiResponse.internalError("查询 Agent 指标失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/aip/agent-metrics/{agentId}/errors — 错误/告警列表
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询 Agent 的错误/告警记录（来自 ecos_agent_alert 表）。
     *
     * <p>支持可选参数 ?limit=N（默认 50）。</p>
     */
    @GetMapping("/{agentId}/errors")
    public ApiResponse<Map<String, Object>> getErrors(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, trace_id AS \"traceId\", agent_id AS \"agentId\", " +
                "alert_type AS \"alertType\", message, " +
                "created_at AS \"createdAt\" " +
                "FROM ecos_agent_alert WHERE agent_id = ? " +
                "ORDER BY created_at DESC LIMIT ?",
                agentId, limit
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("agentId", agentId);
            data.put("total", rows.size());
            data.put("errors", rows);

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to get errors for agentId={}", agentId, e);
            return ApiResponse.internalError("查询 Agent 错误失败: " + e.getMessage());
        }
    }
}
