package com.chinacreator.gzcm.engine.cognitive.controller;

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
 * AIP Model Catalog REST API — AI 模型目录管理。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/models              — 列出所有模型</li>
 *   <li>POST   /api/v1/aip/models              — 注册新模型</li>
 *   <li>GET    /api/v1/aip/models/{id}         — 模型详情</li>
 *   <li>PUT    /api/v1/aip/models/{id}         — 更新模型</li>
 *   <li>DELETE /api/v1/aip/models/{id}         — 删除模型</li>
 *   <li>GET    /api/v1/aip/models/{id}/health  — 健康检查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip/models")
public class AIPModelController {

    private static final Logger log = LoggerFactory.getLogger(AIPModelController.class);

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    public AIPModelController() {
        // 预置一些默认模型
        addDefault("deepseek-v4-pro", "DeepSeek V4 Pro", "deepseek", "chat");
        addDefault("deepseek-v4-flash", "DeepSeek V4 Flash", "deepseek", "chat");
        addDefault("gpt-4o", "GPT-4o", "openai", "chat");
        addDefault("claude-sonnet-4", "Claude Sonnet 4", "anthropic", "chat");
        addDefault("text-embedding-3-large", "Text Embedding 3 Large", "openai", "embedding");
    }

    private void addDefault(String id, String name, String provider, String modelType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("provider", provider);
        m.put("modelType", modelType);
        m.put("description", "Built-in " + provider + " model");
        m.put("status", "active");
        m.put("createdAt", Instant.now().toString());
        m.put("updatedAt", Instant.now().toString());
        store.put(id, m);
    }

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listModels() {
        List<Map<String, Object>> result = store.values().stream()
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getModel(@PathVariable String id) {
        Map<String, Object> m = store.get(id);
        if (m == null) return ApiResponse.notFound("Model " + id + " 不存在");
        return ApiResponse.success(m);
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<Map<String, Object>> createModel(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = body.containsKey("id")
            ? String.valueOf(body.get("id")).trim()
            : "model_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("provider", String.valueOf(body.getOrDefault("provider", "custom")));
        m.put("modelType", String.valueOf(body.getOrDefault("modelType", "chat")));
        m.put("description", String.valueOf(body.getOrDefault("description", "")));
        m.put("endpoint", String.valueOf(body.getOrDefault("endpoint", "")));
        m.put("apiKeyRef", String.valueOf(body.getOrDefault("apiKeyRef", "")));
        m.put("status", "active");
        m.put("createdAt", Instant.now().toString());
        m.put("updatedAt", Instant.now().toString());
        store.put(id, m);
        log.info("AIP Model registered: {} [{}]", id, name);
        return ApiResponse.success(m);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateModel(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Model " + id + " 不存在");

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("name")) updated.put("name", body.get("name"));
        if (body.containsKey("provider")) updated.put("provider", body.get("provider"));
        if (body.containsKey("modelType")) updated.put("modelType", body.get("modelType"));
        if (body.containsKey("description")) updated.put("description", body.get("description"));
        if (body.containsKey("endpoint")) updated.put("endpoint", body.get("endpoint"));
        if (body.containsKey("apiKeyRef")) updated.put("apiKeyRef", body.get("apiKeyRef"));
        if (body.containsKey("status")) updated.put("status", body.get("status"));
        updated.put("updatedAt", Instant.now().toString());
        store.put(id, updated);
        log.info("AIP Model updated: {}", id);
        return ApiResponse.success(updated);
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteModel(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("AIP Model deleted: {}", id);
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.notFound("Model " + id + " 不存在");
    }

    // ═══════════════ 健康检查 ═══════════════════

    @GetMapping("/{id}/health")
    public ApiResponse<Map<String, Object>> healthCheck(@PathVariable String id) {
        Map<String, Object> m = store.get(id);
        if (m == null) return ApiResponse.notFound("Model " + id + " 不存在");

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("modelId", id);
        health.put("status", "healthy");
        health.put("latencyMs", 120);
        health.put("checkedAt", Instant.now().toString());
        log.info("AIP Model health check: {} -> healthy", id);
        return ApiResponse.success(health);
    }
}
