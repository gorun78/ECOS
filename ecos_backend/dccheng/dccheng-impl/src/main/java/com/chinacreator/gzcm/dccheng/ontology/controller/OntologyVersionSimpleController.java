package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyVersionService;

/**
 * 简化版本端点 — 不依赖 ontologyId 的快捷版本操作
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/versions              — 全部版本列表</li>
 *   <li>POST   /api/v1/ecos/versions              — 创建版本快照（body 需含 ontologyId）</li>
 *   <li>GET    /api/v1/ecos/versions/{id}          — 版本详情</li>
 *   <li>GET    /api/v1/ecos/versions/{id}/diff     — 与前一版本 diff</li>
 *   <li>POST   /api/v1/ecos/versions/{id}/publish   — 发布版本</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos/versions")
public class OntologyVersionSimpleController {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionSimpleController.class);

    private final OntologyVersionService versionService;

    public OntologyVersionSimpleController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * GET /api/v1/ecos/versions — 列出所有版本（跨全部 ontology）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAllVersions() {
        return ApiResponse.success(versionService.listAllVersions());
    }

    /**
     * POST /api/v1/ecos/versions — 创建版本快照
     * Body: { "ontologyId": "...", "changeLog": "...", "publisher": "..." }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createVersion(@RequestBody Map<String, Object> body) {
        String ontologyId = String.valueOf(body.getOrDefault("ontologyId", "default"));
        Map<String, Object> ver = versionService.createVersion(ontologyId, body);
        log.info("Version created via simple endpoint: {} v{}", ver.get("id"), ver.get("versionNo"));
        return ApiResponse.success(ver);
    }

    /**
     * GET /api/v1/ecos/versions/{id} — 版本详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getVersion(@PathVariable String id) {
        Map<String, Object> ver = versionService.getVersionById(id);
        if (ver == null) return ApiResponse.notFound("ONT-001: Version '" + id + "' not found");
        return ApiResponse.success(ver);
    }

    /**
     * GET /api/v1/ecos/versions/{id}/diff — 与前一版本 diff
     */
    @GetMapping("/{id}/diff")
    public ApiResponse<Map<String, Object>> diffWithPrevious(@PathVariable String id) {
        try {
            return ApiResponse.success(versionService.diffWithPrevious(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * POST /api/v1/ecos/versions/{id}/publish — 发布版本（校验→Snapshot→版本号→Published）
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publishVersion(@PathVariable String id) {
        try {
            Map<String, Object> ver = versionService.publishVersionById(id);
            log.info("Version published via simple endpoint: {}", id);
            return ApiResponse.success(ver);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
