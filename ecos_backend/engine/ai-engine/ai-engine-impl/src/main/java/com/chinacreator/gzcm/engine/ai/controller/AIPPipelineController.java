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
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineExecResultVO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineSummaryVO;
import com.chinacreator.gzcm.engine.ai.dto.AipPipelineVO;

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
 *
 * <p>DI-2 #147EC：内存行为 {@link AipPipelineVO}（{@code steps} 自由结构透传，
 * {@code lastExecutedAt} 条件键）；列表摘要 6 键、执行回执 4 键、删除复用 2 键回执。</p>
 */
@RestController
@RequestMapping("/api/v1/aip/pipelines")
public class AIPPipelineController {

    private static final Logger log = LoggerFactory.getLogger(AIPPipelineController.class);

    private final Map<String, AipPipelineVO> store = new ConcurrentHashMap<>();

    // ═══════════════ 列表 ═══════════════

    @GetMapping
    public ApiResponse<List<AipPipelineSummaryVO>> listPipelines() {
        List<AipPipelineSummaryVO> result = store.values().stream()
            .map(p -> new AipPipelineSummaryVO(p.id(), p.name(), p.description(), p.status(),
                    p.createdAt(), p.updatedAt()))
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════

    @GetMapping("/{id}")
    public ApiResponse<AipPipelineVO> getPipeline(@PathVariable String id) {
        AipPipelineVO p = store.get(id);
        if (p == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");
        return ApiResponse.success(p);
    }

    // ═══════════════ 创建 ═══════════════

    @PostMapping
    public ApiResponse<AipPipelineVO> createPipeline(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty()) {
            return ApiResponse.badRequest("AIP-001: 'name' is required");
        }
        String id = "pip_" + UUID.randomUUID().toString().substring(0, 8);
        String now = Instant.now().toString();
        AipPipelineVO p = new AipPipelineVO(
                id,
                name,
                String.valueOf(body.getOrDefault("description", "")),
                body.getOrDefault("steps", List.of()),
                "draft",
                now,
                now,
                null);
        store.put(id, p);
        log.info("AIP Pipeline created: {} [{}]", id, name);
        return ApiResponse.success(p);
    }

    // ═══════════════ 更新 ═══════════════

    @PutMapping("/{id}")
    public ApiResponse<AipPipelineVO> updatePipeline(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        AipPipelineVO existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");

        AipPipelineVO updated = existing.withUpdated(
                body.containsKey("name") ? String.valueOf(body.get("name")) : null,
                body.containsKey("description") ? String.valueOf(body.get("description")) : null,
                body.get("steps"),
                body.containsKey("status") ? String.valueOf(body.get("status")) : null,
                Instant.now().toString());
        store.put(id, updated);
        log.info("AIP Pipeline updated: {}", id);
        return ApiResponse.success(updated);
    }

    // ═══════════════ 删除 ═══════════════

    @DeleteMapping("/{id}")
    public ApiResponse<AipDeleteResultVO> deletePipeline(@PathVariable String id) {
        if (store.remove(id) != null) {
            log.info("AIP Pipeline deleted: {}", id);
            return ApiResponse.success(new AipDeleteResultVO(true, id));
        }
        return ApiResponse.notFound("Pipeline " + id + " 不存在");
    }

    // ═══════════════ 执行 ═══════════════

    @PostMapping("/{id}/execute")
    public ApiResponse<AipPipelineExecResultVO> executePipeline(@PathVariable String id) {
        AipPipelineVO existing = store.get(id);
        if (existing == null) return ApiResponse.notFound("Pipeline " + id + " 不存在");

        store.put(id, existing.withStatusAndExecution("running", existing.lastExecutedAt()));

        String executedAt = Instant.now().toString();
        AipPipelineExecResultVO result = new AipPipelineExecResultVO(
                id, "executed", "Pipeline executed successfully", executedAt);
        log.info("AIP Pipeline executed: {}", id);

        store.put(id, existing.withStatusAndExecution("completed", executedAt));
        return ApiResponse.success(result);
    }
}
