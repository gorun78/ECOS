package com.chinacreator.gzcm.engine.ai.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.AgentCompareResult;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.AgentTestResult;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.OrchestrationResult;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.PipelineExecution;

/**
 * AI工作台统一控制器 — Agent测试/对比/流水线/编排。
 *
 * <h3>端点 (base: /api/v1/aip/studio)：</h3>
 * <ul>
 *   <li>POST /api/v1/aip/studio/agents/{id}/test      — Agent 单条消息测试</li>
 *   <li>POST /api/v1/aip/studio/agents/compare         — 两Agent对比测试</li>
 *   <li>POST /api/v1/aip/studio/pipelines/{id}/execute — 执行流水线</li>
 *   <li>GET  /api/v1/aip/studio/pipelines/{id}/executions — 查询流水线执行状态</li>
 *   <li>POST /api/v1/aip/studio/orchestrate            — 多Agent编排</li>
 * </ul>
 *
 * <p>注：使用 /api/v1/aip/studio 前缀避免与 AIPPipelineController (/api/v1/aip/pipelines)
 * 的 execute 端点冲突。AIPAgentController 上另有一个快捷 test 端点
 * POST /api/v1/aip/agents/{id}/test。</p>
 *
 * <h3>注入：</h3>
 * {@link AgentStudioService} — 统一服务入口
 */
@RestController
@RequestMapping("/api/v1/aip/studio")
public class AgentStudioController {

    private static final Logger log = LoggerFactory.getLogger(AgentStudioController.class);

    @Autowired(required = false)
    private AgentStudioService studioService;

    // ═══════════════ Agent 测试 ═══════════════════

    /**
     * POST /api/v1/aip/studio/agents/{id}/test
     * <p>对指定 Agent 发送测试消息并返回结果。</p>
     */
    @PostMapping("/agents/{id}/test")
    public ApiResponse<Map<String, Object>> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }

        String testMessage = String.valueOf(body.getOrDefault("testMessage",
                body.getOrDefault("message", "请介绍一下你自己")));

        // 构建 agentDef — 测试时以请求体中的参数覆盖 Agent 定义
        Map<String, Object> agentDef = new LinkedHashMap<>(body);
        agentDef.putIfAbsent("systemPrompt", body.getOrDefault("systemPrompt", ""));
        agentDef.putIfAbsent("model", body.getOrDefault("model", "deepseek-chat"));
        agentDef.putIfAbsent("temperature", body.getOrDefault("temperature", 0.7));
        agentDef.putIfAbsent("maxTokens", body.getOrDefault("maxTokens", 4096));

        try {
            AgentTestResult result = studioService.createAndTest(agentDef, testMessage);
            Map<String, Object> data = result.toMap();
            data.put("agentId", id); // 使用路径中的 agentId

            if (result.isSuccess()) {
                return ApiResponse.success(data);
            } else {
                return ApiResponse.success("Agent 测试未成功完成", data);
            }
        } catch (Exception e) {
            log.error("[AgentStudio] testAgent failed for id={}", id, e);
            return ApiResponse.internalError("Agent 测试失败: " + e.getMessage());
        }
    }

    // ═══════════════ Agent 对比 ═══════════════════

    /**
     * POST /api/v1/aip/studio/agents/compare
     * <p>对比两个 Agent 在相同测试消息集上的表现。</p>
     *
     * <pre>
     * {
     *   "agentIdA": "aip_agent_xxx",
     *   "agentIdB": "aip_agent_yyy",
     *   "testMessages": ["消息1", "消息2"]
     * }
     * </pre>
     */
    @PostMapping("/agents/compare")
    public ApiResponse<Map<String, Object>> compareAgents(@RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }

        String agentIdA = String.valueOf(body.getOrDefault("agentIdA", "")).trim();
        String agentIdB = String.valueOf(body.getOrDefault("agentIdB", "")).trim();

        if (agentIdA.isEmpty()) {
            return ApiResponse.badRequest("AIP-010: 'agentIdA' is required");
        }
        if (agentIdB.isEmpty()) {
            return ApiResponse.badRequest("AIP-011: 'agentIdB' is required");
        }

        @SuppressWarnings("unchecked")
        List<String> testMessages = (List<String>) body.get("testMessages");

        try {
            AgentCompareResult result = studioService.compare(agentIdA, agentIdB, testMessages);
            if (result.isSuccess()) {
                return ApiResponse.success(result.toMap());
            } else {
                return ApiResponse.internalError(result.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("[AgentStudio] compareAgents failed: {} vs {}", agentIdA, agentIdB, e);
            return ApiResponse.internalError("Agent 对比失败: " + e.getMessage());
        }
    }

    // ═══════════════ 流水线执行 ═══════════════════

    /**
     * POST /api/v1/aip/studio/pipelines/{id}/execute
     * <p>启动指定流水线的异步执行，返回执行ID用于后续查询。</p>
     */
    @PostMapping("/pipelines/{id}/execute")
    public ApiResponse<Map<String, Object>> executePipeline(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> params) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }

        try {
            Map<String, Object> effectiveParams = params != null ? params : new LinkedHashMap<>();
            PipelineExecution exec = studioService.startPipeline(id, effectiveParams);
            return ApiResponse.success(exec.toMap());
        } catch (Exception e) {
            log.error("[AgentStudio] executePipeline failed for id={}", id, e);
            return ApiResponse.internalError("流水线执行失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/aip/studio/pipelines/{id}/executions
     * <p>查询流水线的执行状态（当前仅支持按 executionId 查询）。</p>
     */
    @GetMapping("/pipelines/{id}/executions")
    public ApiResponse<Map<String, Object>> getPipelineExecutions(@PathVariable String id) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }

        PipelineExecution exec = studioService.getPipelineStatus(id);
        if (exec == null) {
            return ApiResponse.notFound("Pipeline execution " + id + " 不存在");
        }
        return ApiResponse.success(exec.toMap());
    }

    // ═══════════════ 编排 ═══════════════════

    /**
     * POST /api/v1/aip/studio/orchestrate
     * <p>多Agent智能编排 — 根据用户消息路由到合适的Agent。</p>
     *
     * <pre>
     * {
     *   "message": "帮我分析销售数据并生成报告",
     *   "sessionId": "sess-xxx"  // 可选
     * }
     * </pre>
     */
    @PostMapping("/orchestrate")
    public ApiResponse<Map<String, Object>> orchestrate(@RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }

        String message = String.valueOf(body.getOrDefault("message",
                body.getOrDefault("userMessage", ""))).trim();
        if (message.isEmpty()) {
            return ApiResponse.badRequest("AIP-012: 'message' is required");
        }

        String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));

        try {
            OrchestrationResult result = studioService.orchestrate(message, sessionId);
            if (result.isSuccess()) {
                return ApiResponse.success(result.toMap());
            } else {
                return ApiResponse.success("编排未成功完成", result.toMap());
            }
        } catch (Exception e) {
            log.error("[AgentStudio] orchestrate failed", e);
            return ApiResponse.internalError("编排失败: " + e.getMessage());
        }
    }
}
