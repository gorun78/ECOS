package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyVersionService;

/**
 * Version Controller — 版本管理（Snapshot / Publish / Rollback / Deprecate / Diff）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions                          — 版本列表</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions                          — 创建新版本 (Draft)</li>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}              — 版本详情</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/publish       — 发布版本</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/rollback      — 回滚</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/deprecate     — 废弃</li>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{v1}/diff/{v2}            — 版本对比</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos/ontologies")
public class OntologyVersionController {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionController.class);

    private final OntologyVersionService versionService;

    public OntologyVersionController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping("/{ontologyId}/versions")
    public ApiResponse<List<Map<String, Object>>> listVersions(@PathVariable String ontologyId) {
        return ApiResponse.success(versionService.listVersions(ontologyId));
    }

    @PostMapping("/{ontologyId}/versions")
    public ApiResponse<Map<String, Object>> createVersion(
            @PathVariable String ontologyId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> ver = versionService.createVersion(ontologyId, body);
        log.info("Version created: {} v{}", ver.get("id"), ver.get("versionNo"));
        return ApiResponse.success(ver);
    }

    @GetMapping("/{ontologyId}/versions/{versionId}")
    public ApiResponse<Map<String, Object>> getVersion(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        Map<String, Object> ver = versionService.getVersion(ontologyId, versionId);
        if (ver == null) return ApiResponse.notFound("ONT-001: Version '" + versionId + "' not found");
        return ApiResponse.success(ver);
    }

    @PostMapping("/{ontologyId}/versions/{versionId}/publish")
    public ApiResponse<Map<String, Object>> publishVersion(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        try {
            Map<String, Object> ver = versionService.publishVersion(ontologyId, versionId);
            log.info("Version published: {} v{}", versionId, ver.get("versionNo"));
            return ApiResponse.success(ver);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{ontologyId}/versions/{versionId}/rollback")
    public ApiResponse<Map<String, Object>> rollback(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        try {
            return ApiResponse.success(versionService.rollback(ontologyId, versionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{ontologyId}/versions/{versionId}/deprecate")
    public ApiResponse<Map<String, Object>> deprecate(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        return ApiResponse.success(versionService.deprecate(ontologyId, versionId));
    }

    @GetMapping("/{ontologyId}/versions/{v1}/diff/{v2}")
    public ApiResponse<Map<String, Object>> diff(
            @PathVariable String ontologyId,
            @PathVariable String v1,
            @PathVariable String v2) {
        try {
            return ApiResponse.success(versionService.diff(ontologyId, v1, v2));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
