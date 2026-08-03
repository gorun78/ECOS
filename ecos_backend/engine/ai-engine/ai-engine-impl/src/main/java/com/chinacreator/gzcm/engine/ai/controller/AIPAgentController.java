package com.chinacreator.gzcm.engine.ai.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService;
import com.chinacreator.gzcm.engine.ai.service.AgentStudioService.AgentTestResult;
import com.chinacreator.gzcm.engine.ai.service.AgentTemplateService;
import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.runtime.core.agent.mesh.repository.AgentRegistryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AIP Agent CRUD REST API — AI 工作台 Agent 管理（PG 持久化）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/agents          — 列出所有 Agent</li>
 *   <li>POST   /api/v1/aip/agents          — 创建 Agent</li>
 *   <li>GET    /api/v1/aip/agents/{id}     — Agent 详情</li>
 *   <li>PUT    /api/v1/aip/agents/{id}     — 更新 Agent</li>
 *   <li>DELETE /api/v1/aip/agents/{id}     — 删除 Agent (builtin→403)</li>
 *   <li>GET    /api/v1/aip/agent-templates — Agent 模板列表</li>
 *   <li>POST   /api/v1/aip/agents/instantiate — 基于模板实例化 Agent</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip")
public class AIPAgentController {

    private static final Logger log = LoggerFactory.getLogger(AIPAgentController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    @Autowired(required = false)
    private AgentTemplateService templateService;

    @Autowired(required = false)
    private AgentStudioService studioService;

    // ═══════════════ 列表 ═══════════════════

    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> listAgents() {
        if (agentRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        List<Map<String, Object>> result = agentRepo.findAll().stream()
            .map(this::toSummaryMap)
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/agents/{id}")
    public ApiResponse<Map<String, Object>> getAgent(@PathVariable String id) {
        if (agentRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        AgentRegistryEntity a = agentRepo.findById(id);
        if (a == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        return ApiResponse.success(toDetailMap(a));
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping("/agents")
    public ApiResponse<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        if (agentRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");

        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }

        String id = "aip_agent_" + UUID.randomUUID().toString().substring(0, 8);
        AgentRegistryEntity entity = new AgentRegistryEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setRole("aip");
        entity.setStatus(String.valueOf(body.getOrDefault("status", "draft")));

        // Store extended fields in metadata JSONB
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("description", String.valueOf(body.getOrDefault("description", "")));
        meta.put("modelProvider", String.valueOf(body.getOrDefault("modelProvider", "deepseek")));
        meta.put("modelName", String.valueOf(body.getOrDefault("modelName", "deepseek-v4-flash")));
        meta.put("systemPrompt", String.valueOf(body.getOrDefault("systemPrompt", "")));
        try {
            entity.setMetadata(mapper.writeValueAsString(meta));
        } catch (Exception e) {
            entity.setMetadata("{}");
        }

        // Store tools in capability JSONB
        Object tools = body.getOrDefault("tools", List.of());
        try {
            entity.setCapability(mapper.writeValueAsString(Map.of("tools", tools)));
        } catch (Exception e) {
            entity.setCapability("{}");
        }

        agentRepo.insert(entity);
        log.info("AIP Agent created: {} [{}]", id, name);
        return ApiResponse.success(toDetailMap(entity));
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/agents/{id}")
    public ApiResponse<Map<String, Object>> updateAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (agentRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");

        AgentRegistryEntity existing = agentRepo.findById(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");

        if (body.containsKey("name")) existing.setName(String.valueOf(body.get("name")));
        if (body.containsKey("status")) existing.setStatus(String.valueOf(body.get("status")));

        // Merge metadata
        Map<String, Object> meta = parseMetadata(existing.getMetadata());
        if (body.containsKey("description")) meta.put("description", String.valueOf(body.get("description")));
        if (body.containsKey("modelProvider")) meta.put("modelProvider", String.valueOf(body.get("modelProvider")));
        if (body.containsKey("modelName")) meta.put("modelName", String.valueOf(body.get("modelName")));
        if (body.containsKey("systemPrompt")) meta.put("systemPrompt", String.valueOf(body.get("systemPrompt")));
        try {
            existing.setMetadata(mapper.writeValueAsString(meta));
        } catch (Exception ignored) {}

        // Merge tools in capability
        if (body.containsKey("tools")) {
            try {
                existing.setCapability(mapper.writeValueAsString(Map.of("tools", body.get("tools"))));
            } catch (Exception ignored) {}
        }

        agentRepo.update(existing);
        log.info("AIP Agent updated: {}", id);
        return ApiResponse.success(toDetailMap(existing));
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/agents/{id}")
    public ApiResponse<Map<String, Object>> deleteAgent(@PathVariable String id) {
        if (agentRepo == null) return ApiResponse.internalError("AgentRegistryRepository 未就绪");

        // 内置 Agent 删除保护
        AgentRegistryEntity existing = agentRepo.findById(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        if ("builtin".equals(existing.getRole())) {
            log.warn("Attempt to delete built-in agent: {}", id);
            return ApiResponse.forbidden("AIP-403: 内置 Agent '" + id + "' 不允许删除。内置 Agent 由系统管理。");
        }

        int rows = agentRepo.delete(id);
        if (rows > 0) {
            log.info("AIP Agent deleted: {}", id);
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.notFound("Agent " + id + " 不存在");
    }

    // ═══════════════ 模板列表 ═══════════════════

    @GetMapping("/agent-templates")
    public ApiResponse<List<Map<String, Object>>> listTemplates() {
        if (templateService == null) return ApiResponse.internalError("AgentTemplateService 未就绪");
        return ApiResponse.success(templateService.getTemplates());
    }

    // ═══════════════ 模板实例化 ═══════════════════

    @PostMapping("/agents/instantiate")
    public ApiResponse<Map<String, Object>> instantiateAgent(@RequestBody Map<String, Object> body) {
        if (templateService == null) return ApiResponse.internalError("AgentTemplateService 未就绪");

        String templateId = String.valueOf(body.getOrDefault("templateId", "")).trim();
        if (templateId.isEmpty()) {
            return ApiResponse.badRequest("AIP-002: 'templateId' is required");
        }

        String userId = String.valueOf(body.getOrDefault("userId", "system")).trim();

        try {
            AgentRegistryEntity entity = templateService.instantiate(templateId, userId, body);
            return ApiResponse.success(toDetailMap(entity));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.internalError(e.getMessage());
        }
    }

    // ═══════════════ Agent 测试 (→ AgentStudioService) ═══════════════════

    /**
     * POST /api/v1/aip/agents/{id}/test
     * <p>对指定 Agent 发送一条测试消息，委托 {@link AgentStudioService#createAndTest}。</p>
     *
     * <pre>
     * {
     *   "testMessage": "你好，请介绍你自己",
     *   "systemPrompt": "覆盖默认 system prompt",  // 可选
     *   "model": "deepseek-chat",                   // 可选
     *   "temperature": 0.7                          // 可选
     * }
     * </pre>
     */
    @PostMapping("/agents/{id}/test")
    public ApiResponse<Map<String, Object>> testAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (studioService == null) {
            return ApiResponse.internalError("AgentStudioService 未就绪");
        }
        if (agentRepo == null) {
            return ApiResponse.internalError("AgentRegistryRepository 未就绪");
        }

        AgentRegistryEntity agent = agentRepo.findById(id);
        if (agent == null) {
            return ApiResponse.notFound("Agent " + id + " 不存在");
        }

        String testMessage = String.valueOf(body.getOrDefault("testMessage",
                body.getOrDefault("message", "请介绍一下你自己")));

        // 构建 agentDef — 从已持久化的 Agent + 请求体覆盖参数
        Map<String, Object> agentDef = new LinkedHashMap<>();
        Map<String, Object> meta = parseMetadata(agent.getMetadata());
        agentDef.put("systemPrompt",
                body.getOrDefault("systemPrompt", meta.getOrDefault("systemPrompt", agent.getSystemPrompt())));
        agentDef.put("model",
                body.getOrDefault("model", meta.getOrDefault("model", agent.getModel())));
        agentDef.put("temperature",
                body.getOrDefault("temperature", meta.getOrDefault("temperature", 0.7)));
        agentDef.put("maxTokens",
                body.getOrDefault("maxTokens", meta.getOrDefault("maxTokens", 4096)));

        try {
            AgentTestResult result = studioService.createAndTest(agentDef, testMessage);
            Map<String, Object> data = result.toMap();
            data.put("agentId", id);

            if (result.isSuccess()) {
                return ApiResponse.success(data);
            } else {
                return ApiResponse.success("Agent 测试未成功完成", data);
            }
        } catch (Exception e) {
            log.error("[AIPAgent] testAgent failed for id={}", id, e);
            return ApiResponse.internalError("Agent 测试失败: " + e.getMessage());
        }
    }

    // ═══════════════ 内部方法 ═══════════════════

    /** 从 AgentRegistryEntity 提取列表摘要字段 */
    private Map<String, Object> toSummaryMap(AgentRegistryEntity entity) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", entity.getId());
        s.put("name", entity.getName());
        s.put("status", entity.getStatus());
        s.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);

        Map<String, Object> meta = parseMetadata(entity.getMetadata());
        s.put("modelProvider", meta.getOrDefault("modelProvider", ""));
        s.put("modelName", meta.getOrDefault("modelName", ""));
        return s;
    }

    /** 从 AgentRegistryEntity 提取详情字段（兼容旧 Map 格式） */
    private Map<String, Object> toDetailMap(AgentRegistryEntity entity) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", entity.getId());
        detail.put("name", entity.getName());
        detail.put("status", entity.getStatus());
        detail.put("role", entity.getRole());

        Map<String, Object> meta = parseMetadata(entity.getMetadata());
        detail.put("description", meta.getOrDefault("description", ""));
        detail.put("modelProvider", meta.getOrDefault("modelProvider", "deepseek"));
        detail.put("modelName", meta.getOrDefault("modelName", "deepseek-v4-flash"));
        detail.put("systemPrompt", meta.getOrDefault("systemPrompt", ""));

        // Parse tools from capability JSON
        try {
            if (entity.getCapability() != null && !entity.getCapability().isEmpty()) {
                var node = mapper.readTree(entity.getCapability());
                if (node.has("tools")) detail.put("tools", mapper.treeToValue(node.get("tools"), List.class));
                else detail.put("tools", List.of());
            } else {
                detail.put("tools", List.of());
            }
        } catch (Exception e) {
            detail.put("tools", List.of());
        }

        detail.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        detail.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return detail;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) return new LinkedHashMap<>();
        try {
            return mapper.readValue(metadataJson, LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
