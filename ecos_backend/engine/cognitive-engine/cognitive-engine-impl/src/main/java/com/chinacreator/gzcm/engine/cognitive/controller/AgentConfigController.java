package com.chinacreator.gzcm.engine.cognitive.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.aimod.AgentConfigService;

/**
 * Agent Builder REST API — Agent 配置 CRUD + 工具/知识库绑定 + 测试
 *
 * <pre>
 * GET    /api/v1/agents                — 列出所有 Agent
 * POST   /api/v1/agents                — 创建 Agent 配置
 * GET    /api/v1/agents/{id}           — Agent 详情
 * PUT    /api/v1/agents/{id}           — 更新 Agent 配置
 * DELETE /api/v1/agents/{id}           — 删除 Agent
 * PUT    /api/v1/agents/{id}/tools     — 绑定工具列表
 * PUT    /api/v1/agents/{id}/knowledge — 绑定知识库
 * GET    /api/v1/agents/{id}/prompts   — Prompt 版本列表
 * POST   /api/v1/agents/{id}/test      — 测试 Agent 对话
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentConfigController {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigController.class);

    private final AgentConfigService agentConfigService;

    public AgentConfigController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<Map<String, Object>> listAgents(
            @RequestParam(defaultValue = "100") int pageSize) {
        List<Map<String, Object>> list = agentConfigService.listAgents(pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("total", agentConfigService.totalCount());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAgent(@PathVariable String id) {
        return agentConfigService.getAgent(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        Map<String, Object> agent = agentConfigService.createAgent(body);
        log.info("Agent created via DB: {} [{}]", agent.get("id"), agent.get("name"));
        return ApiResponse.success(agent);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.updateAgent(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteAgent(@PathVariable String id) {
        if (agentConfigService.deleteAgent(id)) {
            log.info("Agent deleted: {}", id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            return ApiResponse.success(result);
        }
        return ApiResponse.notFound("Agent " + id + " 不存在");
    }

    // ═══════════════ 工具绑定 ═══════════════════

    @PutMapping("/{id}/tools")
    public ApiResponse<Map<String, Object>> bindTools(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.bindTools(id, body.getOrDefault("tools", List.of()))
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 知识库绑定 ═══════════════════

    @PutMapping("/{id}/knowledge")
    public ApiResponse<Map<String, Object>> bindKnowledge(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.bindKnowledge(id, body.getOrDefault("knowledge", List.of()))
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ Prompt 版本列表 ═══════════════════

    @GetMapping("/{id}/prompts")
    public ApiResponse<List<Map<String, Object>>> listPromptVersions(@PathVariable String id) {
        if (agentConfigService.getAgent(id).isEmpty()) {
            return ApiResponse.notFound("Agent " + id + " 不存在");
        }
        return ApiResponse.success(agentConfigService.listPromptVersions(id));
    }

    // ═══════════════ 测试 ═══════════════════

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (agentConfigService.getAgent(id).isEmpty()) {
            return ApiResponse.notFound("Agent " + id + " 不存在");
        }
        Map<String, Object> result = agentConfigService.testAgent(id, body);
        log.info("Agent test completed: {}", id);
        return ApiResponse.success(result);
    }

    // ═══════════════ 模型列表 ═══════════════════

    /** GET /api/v1/agents/models — 返回可用 LLM 模型列表 */
    @GetMapping("/models")
    public ApiResponse<List<Map<String, Object>>> listModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        models.add(Map.of("id", "deepseek-v4-pro", "name", "DeepSeek V4 Pro", "provider", "deepseek"));
        models.add(Map.of("id", "deepseek-v4-flash", "name", "DeepSeek V4 Flash", "provider", "deepseek"));
        models.add(Map.of("id", "gpt-4o", "name", "GPT-4o", "provider", "openai"));
        models.add(Map.of("id", "claude-sonnet-4", "name", "Claude Sonnet 4", "provider", "anthropic"));
        return ApiResponse.success(models);
    }

    // ═══════════════ 全局 Prompt 列表 ═══════════════════

    /** GET /api/v1/agents/prompts — 返回所有 Agent 的 Prompt 模板汇总 */
    @GetMapping("/prompts")
    public ApiResponse<List<Map<String, Object>>> listAllPrompts() {
        List<Map<String, Object>> prompts = new ArrayList<>();
        // 从所有 agent 收集 prompt 版本
        List<Map<String, Object>> agents = agentConfigService.listAgents(100);
        for (Map<String, Object> agent : agents) {
            String agentId = (String) agent.get("id");
            List<Map<String, Object>> versions = agentConfigService.listPromptVersions(agentId);
            for (Map<String, Object> v : versions) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", v.getOrDefault("id", ""));
                p.put("title", v.getOrDefault("name", v.getOrDefault("title", "")));
                p.put("filename", agent.get("name") + "-v" + v.getOrDefault("version", "1.0") + ".md");
                p.put("content", v.getOrDefault("template", v.getOrDefault("content", "")));
                p.put("version", v.getOrDefault("version", "1.0"));
                p.put("category", v.getOrDefault("category", "planning"));
                p.put("agentId", agentId);
                prompts.add(p);
            }
        }
        // 如果没有 Prompt，返回硬编码默认值
        if (prompts.isEmpty()) {
            prompts.add(Map.of(
                "id", "default-planning",
                "title", "Planning Prompt",
                "filename", "planning-v1.0.md",
                "content", "You are an enterprise AI agent. Analyze the following situation and provide a structured plan.",
                "version", "1.0",
                "category", "planning"
            ));
            prompts.add(Map.of(
                "id", "default-diagnostic",
                "title", "Diagnostic Prompt",
                "filename", "diagnostic-v1.0.md",
                "content", "You are a diagnostic expert. Identify root causes and recommend actionable solutions.",
                "version", "1.0",
                "category", "diagnostic"
            ));
        }
        return ApiResponse.success(prompts);
    }
}
