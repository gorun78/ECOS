package com.chinacreator.gzcm.engine.ai.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;

/**
 * AIP Agent CRUD REST API — AI 工作台 Agent 管理。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/agents          — 列出所有 Agent</li>
 *   <li>POST   /api/v1/aip/agents          — 创建 Agent</li>
 *   <li>GET    /api/v1/aip/agents/{id}     — Agent 详情</li>
 *   <li>PUT    /api/v1/aip/agents/{id}     — 更新 Agent</li>
 *   <li>DELETE /api/v1/aip/agents/{id}     — 删除 Agent</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip/agents")
public class AIPAgentController {

    private static final Logger log = LoggerFactory.getLogger(AIPAgentController.class);

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAgents() {
        List<Map<String, Object>> result = store.values().stream()
            .map(this::summary)
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getAgent(@PathVariable String id) {
        Map<String, Object> a = store.get(id);
        if (a == null) return ApiResponse.notFound("Agent " + id + " 不存在");
        return ApiResponse.success(a);
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = "aip_agent_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", id);
        a.put("name", name);
        a.put("description", String.valueOf(body.getOrDefault("description", "")));
        a.put("modelProvider", String.valueOf(body.getOrDefault("modelProvider", "deepseek")));
        a.put("modelName", String.valueOf(body.getOrDefault("modelName", "deepseek-v4-flash")));
        a.put("systemPrompt", String.valueOf(body.getOrDefault("systemPrompt", "")));
        a.put("tools", body.getOrDefault("tools", List.of()));
        a.put("status", "draft");
        a.put("createdAt", Instant.now().toString());
        a.put("updatedAt", Instant.now().toString());
        store.put(id, a);
        log.info("AIP Agent created: {} [{}]", id, name);
        return ApiResponse.success(a);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateAgent(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Agent " + id + " 不存在");

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("name")) updated.put("name", body.get("name"));
        if (body.containsKey("description")) updated.put("description", body.get("description"));
        if (body.containsKey("modelProvider")) updated.put("modelProvider", body.get("modelProvider"));
        if (body.containsKey("modelName")) updated.put("modelName", body.get("modelName"));
        if (body.containsKey("systemPrompt")) updated.put("systemPrompt", body.get("systemPrompt"));
        if (body.containsKey("tools")) updated.put("tools", body.get("tools"));
        if (body.containsKey("status")) updated.put("status", body.get("status"));
        updated.put("updatedAt", Instant.now().toString());
        store.put(id, updated);
        log.info("AIP Agent updated: {}", id);
        return ApiResponse.success(updated);
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteAgent(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("AIP Agent deleted: {}", id);
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.notFound("Agent " + id + " 不存在");
    }

    // ═══════════════ 内部方法 ═══════════════════

    private Map<String, Object> summary(Map<String, Object> record) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", record.get("id"));
        s.put("name", record.get("name"));
        s.put("modelProvider", record.get("modelProvider"));
        s.put("modelName", record.get("modelName"));
        s.put("status", record.get("status"));
        s.put("createdAt", record.get("createdAt"));
        return s;
    }
}
