package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.AgentMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 指标兼容层 — PMO-40 批次3 T1。
 *
 * <p>前端 {@code fetchAgentMetrics} / {@code fetchAgentErrors} 使用
 * {@code /api/v1/agent-metrics/{agentId}} 路径，而主路径在
 * {@code /api/v1/aip/agent-metrics/{agentId}}（{@link AgentMetricsController}）。
 * 本 Controller 是薄路径兼容层，**1-to-1 委托**给已有的
 * {@link AgentMetricsService}，响应字段与主路径完全一致（验收 §T1：
 * <code>curl GET /api/v1/agent-metrics/{id}</code> 与
 * <code>curl GET /api/v1/aip/agent-metrics/{id}</code> 响应 diff 通过）。
 * 不新增业务逻辑，不改动主路径（§0.2 API 只增不改）。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>GET /api/v1/agent-metrics/{agentId} — Agent 指标汇总</li>
 *   <li>GET /api/v1/agent-metrics/{agentId}/errors — Agent 错误/告警列表</li>
 *   <li>裸路径 /api/agent-metrics/... 同样可达（双路径）</li>
 * </ul>
 *
 * <h3>错误语义</h3>
 * <p>与主路径完全一致：
 * <ul>
 *   <li>已存在的 agentId（ecos_agent_metrics 有记录）→ 200 + 业务字段</li>
 *   <li>不存在 / 无记录 → 404（通过 Service 侧 {@code exists(agentId)} 探测，
 *       不由本层捕获 SQLException）</li>
 *   <li>其它业务异常 → 透传给 GlobalExceptionHandler（默认 500）</li>
 * </ul>
 *
 * @author PMO-40 Batch3
 */
@RestController
@RequestMapping({"/api/v1/agent-metrics", "/api/agent-metrics"})
public class AgentMetricsCompatController {

    private static final Logger log = LoggerFactory.getLogger(AgentMetricsCompatController.class);

    private final AgentMetricsService agentMetricsService;

    /**
     * 注入 {@link AgentMetricsService}（§2.2：必须通过 {@code @Qualifier}
     * 显式 bean 名引用，避免与同模块其它 bean 冲突）。
     */
    public AgentMetricsCompatController(
            @Qualifier("agentMetricsService") AgentMetricsService agentMetricsService) {
        this.agentMetricsService = agentMetricsService;
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/agent-metrics/{agentId} — 兼容路径
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询 Agent 指标汇总 — 与 {@code /api/v1/aip/agent-metrics/{agentId}}
     * 响应字段完全一致（验收 §T1）。
     */
    @GetMapping("/{agentId}")
    public ApiResponse<Map<String, Object>> getMetrics(@PathVariable String agentId) {
        // §T1 验收: 404 agentId 返回 404 不 500（抛 NotFoundException，
        // 由 GlobalExceptionHandler 统一映射为 HTTP 404）
        if (!agentMetricsService.exists(agentId)) {
            log.warn("Compat: agent {} not found in ecos_agent_metrics", agentId);
            throw new com.chinacreator.gzcm.common.exception.NotFoundException(
                "Agent 不存在: " + agentId);
        }
        try {
            // 总计
            Map<String, Object> total = agentMetricsService.queryTotal(agentId);

            // P50 / P99 — 单独查询
            Map<String, Object> pct = agentMetricsService.queryPercentiles(agentId);

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
            // 与主路径 AgentMetricsController 一致：透传给 GlobalExceptionHandler
            log.error("Compat: failed to get metrics for agentId={}", agentId, e);
            throw new RuntimeException("查询 Agent 指标失败: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GET /api/v1/agent-metrics/{agentId}/errors — 兼容路径
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询 Agent 错误/告警记录 — 与 {@code /api/v1/aip/agent-metrics/{agentId}/errors}
     * 响应字段完全一致（验收 §T1）。
     */
    @GetMapping("/{agentId}/errors")
    public ApiResponse<Map<String, Object>> getErrors(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        // §T1 验收: 404 agentId 返回 404 不 500（NotFoundException → HTTP 404）
        if (!agentMetricsService.exists(agentId)) {
            log.warn("Compat: agent {} not found for errors query", agentId);
            throw new com.chinacreator.gzcm.common.exception.NotFoundException(
                "Agent 不存在: " + agentId);
        }
        try {
            List<Map<String, Object>> rows = agentMetricsService.queryErrors(agentId, limit);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("agentId", agentId);
            data.put("total", rows.size());
            data.put("errors", rows);

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Compat: failed to get errors for agentId={}", agentId, e);
            throw new RuntimeException("查询 Agent 错误失败: " + e.getMessage(), e);
        }
    }
}
