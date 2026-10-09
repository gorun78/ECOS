package com.chinacreator.gzcm.engine.ai.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiErrorClass;
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
 *
 * <h3>错误语义（F06-15）：</h3>
 * <p>控制器层禁兜底吞异常；未预期异常经 {@code AiErrorAdvice} 分类为
 * {@code ECOS-AI-*}。依赖未装配则经 {@code AiResponseSupport.dependencyUnavailable} 返回 502。</p>
 */
@RestController
@RequestMapping("/api/v1/aip/studio")
public class AgentStudioController {

    @Autowired(required = false)
    private AgentStudioService studioService;

    // ═══════════════ Agent 测试 ═══════════════════

    /**
     * POST /api/v1/aip/studio/agents/{id}/test
     * <p>对指定 Agent 发送测试消息并返回结果。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。</p>
     */
    @PostMapping("/agents/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AiResponseSupport.<Map<String, Object>>dependencyUnavailable("AgentStudioService"));
        }

        String testMessage = String.valueOf(body.getOrDefault("testMessage",
                body.getOrDefault("message", "请介绍一下你自己")));

        // 构建 agentDef — 测试时以请求体中的参数覆盖 Agent 定义
        Map<String, Object> agentDef = new LinkedHashMap<>(body);
        agentDef.putIfAbsent("systemPrompt", body.getOrDefault("systemPrompt", ""));
        agentDef.putIfAbsent("model", body.getOrDefault("model", "deepseek-chat"));
        agentDef.putIfAbsent("temperature", body.getOrDefault("temperature", 0.7));
        agentDef.putIfAbsent("maxTokens", body.getOrDefault("maxTokens", 4096));

        AgentTestResult result = studioService.createAndTest(agentDef, testMessage);
        Map<String, Object> data = result.toMap();
        data.put("agentId", id); // 使用路径中的 agentId

        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(data));
        }
        // F06-14/15：失败态走 D.5 分类状态，禁 ApiResponse.success(...) 伪成功（A3）
        return AiResponseSupport.failure(result.getErrorClass(), result.getErrorCode(),
                result.getErrorMsg(), result.getErrorTraceId(), data);
    }

    // ═══════════════ Agent 对比 ═══════════════════

    /**
     * POST /api/v1/aip/studio/agents/compare
     * <p>对比两个 Agent 在相同测试消息集上的表现。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。</p>
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> compareAgents(
            @RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AiResponseSupport.<Map<String, Object>>dependencyUnavailable("AgentStudioService"));
        }

        String agentIdA = String.valueOf(body.getOrDefault("agentIdA", "")).trim();
        String agentIdB = String.valueOf(body.getOrDefault("agentIdB", "")).trim();

        if (agentIdA.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AiResponseSupport.<Map<String, Object>>classified(
                            AiErrorClass.INVALID_ARGUMENT, "AIP-010: 'agentIdA' is required"));
        }
        if (agentIdB.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AiResponseSupport.<Map<String, Object>>classified(
                            AiErrorClass.INVALID_ARGUMENT, "AIP-011: 'agentIdB' is required"));
        }

        @SuppressWarnings("unchecked")
        List<String> testMessages = (List<String>) body.get("testMessages");

        AgentCompareResult result = studioService.compare(agentIdA, agentIdB, testMessages);
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        }
        // F06-14/15：禁 internalError（code=-1 无 D.5 码）与 200 伪成功
        return AiResponseSupport.failure(result.getErrorClass(), result.getErrorCode(),
                result.getErrorMsg(), result.getErrorTraceId(), result.toMap());
    }

    // ═══════════════ 流水线执行 ═══════════════════

    /**
     * POST /api/v1/aip/studio/pipelines/{id}/execute
     * <p>启动指定流水线的异步执行，返回执行ID用于后续查询。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。</p>
     */
    @PostMapping("/pipelines/{id}/execute")
    public ApiResponse<Map<String, Object>> executePipeline(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> params) {
        if (studioService == null) {
            return AiResponseSupport.dependencyUnavailable("AgentStudioService");
        }

        Map<String, Object> effectiveParams = params != null ? params : new LinkedHashMap<>();
        PipelineExecution exec = studioService.startPipeline(id, effectiveParams);
        return ApiResponse.success(exec.toMap());
    }

    /**
     * GET /api/v1/aip/studio/pipelines/{id}/executions
     * <p>查询流水线的执行状态（当前仅支持按 executionId 查询）。</p>
     */
    @GetMapping("/pipelines/{id}/executions")
    public ApiResponse<Map<String, Object>> getPipelineExecutions(@PathVariable String id) {
        if (studioService == null) {
            return AiResponseSupport.dependencyUnavailable("AgentStudioService");
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
     * <p>多Agent智能编排 — 根据用户消息路由到合适的Agent。失败经 {@code AiErrorAdvice} 分类为 {@code ECOS-AI-*}。</p>
     *
     * <pre>
     * {
     *   "message": "帮我分析销售数据并生成报告",
     *   "sessionId": "sess-xxx"  // 可选
     * }
     * </pre>
     */
    @PostMapping("/orchestrate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> orchestrate(
            @RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(AiResponseSupport.<Map<String, Object>>dependencyUnavailable("AgentStudioService"));
        }

        String message = String.valueOf(body.getOrDefault("message",
                body.getOrDefault("userMessage", ""))).trim();
        if (message.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AiResponseSupport.<Map<String, Object>>classified(
                            AiErrorClass.INVALID_ARGUMENT, "AIP-012: 'message' is required"));
        }

        String sessionId = String.valueOf(body.getOrDefault("sessionId", ""));

        OrchestrationResult result = studioService.orchestrate(message, sessionId);
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.toMap()));
        }
        // F06-14/15：编排失败态走 D.5 分类状态，禁 success(...) 伪成功（A3）
        return AiResponseSupport.failure(result.getErrorClass(), result.getErrorCode(),
                result.getErrorMsg(), result.getErrorTraceId(), result.toMap());
    }
}
