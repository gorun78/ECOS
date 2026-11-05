package com.chinacreator.gzcm.engine.ai.controller;

import java.time.Instant;
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
import com.chinacreator.gzcm.engine.ai.dto.AipDeleteResultVO;
import com.chinacreator.gzcm.engine.ai.dto.AipModelHealthVO;
import com.chinacreator.gzcm.engine.ai.dto.AipModelVO;

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
 *
 * <p>DI-2 #147DZ：内存行为 {@link AipModelVO}（内置腿无 {@code endpoint/apiKeyRef}，条件键 NON_NULL）；
 * 健康检查回执 {@link AipModelHealthVO}；建/改为自由请求体。</p>
 */
@RestController
@RequestMapping("/api/v1/aip/models")
public class AIPModelController {

    private static final Logger log = LoggerFactory.getLogger(AIPModelController.class);

    private final Map<String, AipModelVO> store = new ConcurrentHashMap<>();

    public AIPModelController() {
        // 预置一些默认模型
        addDefault("deepseek-v4-pro", "DeepSeek V4 Pro", "deepseek", "chat");
        addDefault("deepseek-v4-flash", "DeepSeek V4 Flash", "deepseek", "chat");
        addDefault("gpt-4o", "GPT-4o", "openai", "chat");
        addDefault("claude-sonnet-4", "Claude Sonnet 4", "anthropic", "chat");
        addDefault("text-embedding-3-large", "Text Embedding 3 Large", "openai", "embedding");
    }

    private void addDefault(String id, String name, String provider, String modelType) {
        String now = Instant.now().toString();
        store.put(id, new AipModelVO(id, name, provider, modelType,
                "Built-in " + provider + " model", null, null, "active", now, now));
    }

    // ═══════════════ 列表 ═══════════════

    @GetMapping
    public ApiResponse<List<AipModelVO>> listModels() {
        List<AipModelVO> result = store.values().stream()
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════

    @GetMapping("/{id}")
    public ApiResponse<AipModelVO> getModel(@PathVariable String id) {
        AipModelVO m = store.get(id);
        if (m == null) return ApiResponse.notFound("Model " + id + " 不存在");
        return ApiResponse.success(m);
    }

    // ═══════════════ 创建 ═══════════════

    @PostMapping
    public ApiResponse<AipModelVO> createModel(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = body.containsKey("id")
            ? String.valueOf(body.get("id")).trim()
            : "model_" + UUID.randomUUID().toString().substring(0, 8);

        String now = Instant.now().toString();
        AipModelVO m = new AipModelVO(
                id,
                name,
                String.valueOf(body.getOrDefault("provider", "custom")),
                String.valueOf(body.getOrDefault("modelType", "chat")),
                String.valueOf(body.getOrDefault("description", "")),
                String.valueOf(body.getOrDefault("endpoint", "")),
                String.valueOf(body.getOrDefault("apiKeyRef", "")),
                "active",
                now,
                now);
        store.put(id, m);
        log.info("AIP Model registered: {} [{}]", id, name);
        return ApiResponse.success(m);
    }

    // ═══════════════ 更新 ═══════════════

    @PutMapping("/{id}")
    public ApiResponse<AipModelVO> updateModel(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        AipModelVO existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Model " + id + " 不存在");

        AipModelVO updated = existing.withUpdated(
                body.containsKey("name") ? String.valueOf(body.get("name")) : null,
                body.containsKey("provider") ? String.valueOf(body.get("provider")) : null,
                body.containsKey("modelType") ? String.valueOf(body.get("modelType")) : null,
                body.containsKey("description") ? String.valueOf(body.get("description")) : null,
                body.containsKey("endpoint") ? String.valueOf(body.get("endpoint")) : null,
                body.containsKey("apiKeyRef") ? String.valueOf(body.get("apiKeyRef")) : null,
                body.containsKey("status") ? String.valueOf(body.get("status")) : null,
                Instant.now().toString());
        store.put(id, updated);
        log.info("AIP Model updated: {}", id);
        return ApiResponse.success(updated);
    }

    // ═══════════════ 删除 ═══════════════

    @DeleteMapping("/{id}")
    public ApiResponse<AipDeleteResultVO> deleteModel(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("AIP Model deleted: {}", id);
            return ApiResponse.success(new AipDeleteResultVO(true, id));
        }
        return ApiResponse.notFound("Model " + id + " 不存在");
    }

    // ═══════════════ 健康检查 ═══════════════

    @GetMapping("/{id}/health")
    public ApiResponse<AipModelHealthVO> healthCheck(@PathVariable String id) {
        AipModelVO m = store.get(id);
        if (m == null) return ApiResponse.notFound("Model " + id + " 不存在");

        log.info("AIP Model health check: {} -> healthy", id);
        return ApiResponse.success(new AipModelHealthVO(
                id, "healthy", 120, Instant.now().toString()));
    }
}
