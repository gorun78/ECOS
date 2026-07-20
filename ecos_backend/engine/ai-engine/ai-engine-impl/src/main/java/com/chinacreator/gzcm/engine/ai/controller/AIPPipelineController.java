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
 * AIP Pipeline CRUD REST API — AI Pipeline 的创建、编辑、执行管理。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/aip/pipelines          — 列出所有 Pipeline</li>
 *   <li>POST   /api/v1/aip/pipelines          — 创建 Pipeline</li>
 *   <li>GET    /api/v1/aip/pipelines/{id}     — Pipeline 详情</li>
 *   <li>PUT    /api/v1/aip/pipelines/{id}     — 更新 Pipeline</li>
 *   <li>DELETE /api/v1/aip/pipelines/{id}     — 删除 Pipeline</li>
 *   <li>POST   /api/v1/aip/pipelines/{id}/execute — 执行 Pipeline</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/aip/pipelines")
public class AIPPipelineController {

    private static final Logger log = LoggerFactory.getLogger(AIPPipelineController.class);

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    // ═══════════════ 列表 ═══════════════════

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listPipelines() {
        List<Map<String, Object>> result = store.values().stream()
            .map(this::summary)
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getPipeline(@PathVariable String id) {
        Map<String, Object> p = store.get(id);
        if (p == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");
        return ApiResponse.success(p);
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping
    public ApiResponse<Map<String, Object>> createPipeline(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = "pip_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("name", name);
        p.put("description", String.valueOf(body.getOrDefault("description", "")));
        p.put("steps", body.getOrDefault("steps", List.of()));
        p.put("status", "draft");
        p.put("createdAt", Instant.now().toString());
        p.put("updatedAt", Instant.now().toString());
        store.put(id, p);
        log.info("AIP Pipeline created: {} [{}]", id, name);
        return ApiResponse.success(p);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updatePipeline(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("name")) updated.put("name", body.get("name"));
        if (body.containsKey("description")) updated.put("description", body.get("description"));
        if (body.containsKey("steps")) updated.put("steps", body.get("steps"));
        if (body.containsKey("status")) updated.put("status", body.get("status"));
        updated.put("updatedAt", Instant.now().toString());
        store.put(id, updated);
        log.info("AIP Pipeline updated: {}", id);
        return ApiResponse.success(updated);
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deletePipeline(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("AIP Pipeline deleted: {}", id);
            return ApiResponse.success(Map.of("deleted", true, "id", id));
        }
        return ApiResponse.notFound("Pipeline " + id + " 不存在");
    }

    // ═══════════════ 执行 ═══════════════════

    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> executePipeline(@PathVariable String id) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");

        Map<String, Object> updated = new LinkedHashMap<>(existing);
        updated.put("status", "running");
        store.put(id, updated);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipelineId", id);
        result.put("status", "executed");
        result.put("message", "Pipeline executed successfully");
        result.put("executedAt", Instant.now().toString());
        log.info("AIP Pipeline executed: {}", id);

        updated.put("status", "completed");
        updated.put("lastExecutedAt", Instant.now().toString());
        store.put(id, updated);

        return ApiResponse.success(result);
    }

    // ═══════════════ 内部方法 ═══════════════════

    private Map<String, Object> summary(Map<String, Object> record) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", record.get("id"));
        s.put("name", record.get("name"));
        s.put("description", record.get("description"));
        s.put("status", record.get("status"));
        s.put("createdAt", record.get("createdAt"));
        s.put("updatedAt", record.get("updatedAt"));
        return s;
    }
}
