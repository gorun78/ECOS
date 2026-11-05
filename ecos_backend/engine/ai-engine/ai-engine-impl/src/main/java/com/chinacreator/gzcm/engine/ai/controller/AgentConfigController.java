package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
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
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.AgentRow;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.AgentTestResponse;
import com.chinacreator.gzcm.engine.ai.config.AgentConfigService.PromptVersionRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 *
 * <p><b>#147DA 载体</b>：列表/详情/回执/模型/Prompt 模板全部改 record；请求体仍为自由 Map
 * （创建/更新字段按 {@code containsKey}/{@code getOrDefault} 语义透传）。</p>
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentConfigController {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigController.class);

    private final AgentConfigService agentConfigService;

    public AgentConfigController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    /** 列表响应（2 键：data/total）。 */
    public record AgentListVO(@JsonProperty("data") List<AgentRow> data,
                              @JsonProperty("total") long total) {
    }

    /** 删除回执（1 键）。 */
    public record DeleteResultVO(@JsonProperty("success") boolean success) {
    }

    /** 模型条目（3 键）。 */
    public record ModelVO(@JsonProperty("id") String id,
                          @JsonProperty("name") String name,
                          @JsonProperty("provider") String provider) {
    }

    /** Prompt 模板条目（{@code agentId} 仅来自 Agent 汇总时出现）。 */
    public record PromptTemplateVO(@JsonProperty("id") String id,
                                   @JsonProperty("title") String title,
                                   @JsonProperty("filename") String filename,
                                   @JsonProperty("content") String content,
                                   @JsonProperty("version") String version,
                                   @JsonProperty("category") String category,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("agentId") String agentId) {
    }

    // ═══════════════ 列表 ═══════════════

    @GetMapping
    public ApiResponse<AgentListVO> listAgents(
            @RequestParam(defaultValue = "100") int pageSize) {
        List<AgentRow> list = agentConfigService.listAgents(pageSize);
        return ApiResponse.success(new AgentListVO(list, agentConfigService.totalCount()));
    }

    // ═══════════════ 详情 ═══════════════

    @GetMapping("/{id}")
    public ApiResponse<AgentRow> getAgent(@PathVariable String id) {
        return agentConfigService.getAgent(id)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 创建 ═══════════════

    @PostMapping
    public ApiResponse<AgentRow> createAgent(@RequestBody Map<String, Object> body) {
        AgentRow agent = agentConfigService.createAgent(body);
        log.info("Agent created via DB: {} [{}]", agent.id(), agent.name());
        return ApiResponse.success(agent);
    }

    // ═══════════════ 更新 ═══════════════

    @PutMapping("/{id}")
    public ApiResponse<AgentRow> updateAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.updateAgent(id, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 删除 ═══════════════

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResultVO> deleteAgent(@PathVariable String id) {
        if (agentConfigService.deleteAgent(id)) {
            log.info("Agent deleted: {}", id);
            return ApiResponse.success(new DeleteResultVO(true));
        }
        return ApiResponse.notFound("Agent " + id + " 不存在");
    }

    // ═══════════════ 工具绑定 ═══════════════

    @PutMapping("/{id}/tools")
    public ApiResponse<AgentRow> bindTools(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.bindTools(id, body.getOrDefault("tools", List.of()))
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ 知识库绑定 ═══════════════

    @PutMapping("/{id}/knowledge")
    public ApiResponse<AgentRow> bindKnowledge(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return agentConfigService.bindKnowledge(id, body.getOrDefault("knowledge", List.of()))
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("Agent " + id + " 不存在"));
    }

    // ═══════════════ Prompt 版本列表 ═══════════════

    @GetMapping("/{id}/prompts")
    public ApiResponse<List<PromptVersionRow>> listPromptVersions(@PathVariable String id) {
        if (agentConfigService.getAgent(id).isEmpty()) {
            return ApiResponse.notFound("Agent " + id + " 不存在");
        }
        return ApiResponse.success(agentConfigService.listPromptVersions(id));
    }

    // ═══════════════ 测试 ═══════════════

    @PostMapping("/{id}/test")
    public ApiResponse<AgentTestResponse> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (agentConfigService.getAgent(id).isEmpty()) {
            return ApiResponse.notFound("Agent " + id + " 不存在");
        }
        AgentTestResponse result = agentConfigService.testAgent(id, body);
        log.info("Agent test completed: {}", id);
        return ApiResponse.success(result);
    }

    // ═══════════════ 模型列表 ═══════════════

    /** GET /api/v1/agents/models — 返回可用 LLM 模型列表 */
    @GetMapping("/models")
    public ApiResponse<List<ModelVO>> listModels() {
        List<ModelVO> models = new ArrayList<>();
        models.add(new ModelVO("deepseek-v4-pro", "DeepSeek V4 Pro", "deepseek"));
        models.add(new ModelVO("deepseek-v4-flash", "DeepSeek V4 Flash", "deepseek"));
        models.add(new ModelVO("gpt-4o", "GPT-4o", "openai"));
        models.add(new ModelVO("claude-sonnet-4", "Claude Sonnet 4", "anthropic"));
        return ApiResponse.success(models);
    }

    // ═══════════════ 全局 Prompt 列表 ═══════════════

    /** GET /api/v1/agents/prompts — 返回所有 Agent 的 Prompt 模板汇总 */
    @GetMapping("/prompts")
    public ApiResponse<List<PromptTemplateVO>> listAllPrompts() {
        List<PromptTemplateVO> prompts = new ArrayList<>();
        // 从所有 agent 收集 prompt 版本
        List<AgentRow> agents = agentConfigService.listAgents(100);
        for (AgentRow agent : agents) {
            List<PromptVersionRow> versions = agentConfigService.listPromptVersions(agent.id());
            for (PromptVersionRow v : versions) {
                prompts.add(new PromptTemplateVO("", "",
                        agent.name() + "-v" + v.version() + ".md",
                        v.content(), v.version(), "planning", agent.id()));
            }
        }
        // 如果没有 Prompt，返回硬编码默认值
        if (prompts.isEmpty()) {
            prompts.add(new PromptTemplateVO("default-planning", "Planning Prompt",
                    "planning-v1.0.md",
                    "You are an enterprise AI agent. Analyze the following situation and provide a structured plan.",
                    "1.0", "planning", null));
            prompts.add(new PromptTemplateVO("default-diagnostic", "Diagnostic Prompt",
                    "diagnostic-v1.0.md",
                    "You are a diagnostic expert. Identify root causes and recommend actionable solutions.",
                    "1.0", "diagnostic", null));
        }
        return ApiResponse.success(prompts);
    }
}
