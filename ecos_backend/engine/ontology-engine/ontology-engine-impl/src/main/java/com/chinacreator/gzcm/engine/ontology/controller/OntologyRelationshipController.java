package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * Relationship Controller — 关系设计器（循环检测/图谱/基数）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/relationships         — 实体关系列表</li>
 *   <li>POST   /api/v1/ecos/relationships                            — 创建关系</li>
 *   <li>GET    /api/v1/ecos/relationships/{relId}                     — 关系详情</li>
 *   <li>PUT    /api/v1/ecos/relationships/{relId}                     — 更新关系</li>
 *   <li>DELETE /api/v1/ecos/relationships/{relId}                     — 删除关系</li>
 *   <li>POST   /api/v1/ecos/relationships/validate                    — 验证关系（循环检测）</li>
 *   <li>GET    /api/v1/ecos/relationships/graph                       — 全局关系图谱数据</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyRelationshipController {

    private static final Logger log = LoggerFactory.getLogger(OntologyRelationshipController.class);

    private final OntologyService ontologyService;

    public OntologyRelationshipController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping("/entities/{entityId}/relationships")
    public ApiResponse<List<Map<String, Object>>> listEntityRelationships(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listEntityRelationships(entityId));
    }

    @GetMapping("/relationships")
    public ApiResponse<List<Map<String, Object>>> listAllRelationships() {
        return ApiResponse.success(ontologyService.listAllRelationships());
    }

    @GetMapping("/relationships/{relId}")
    public ApiResponse<Map<String, Object>> getRelationship(@PathVariable String relId) {
        return ontologyService.listAllRelationships().stream()
            .filter(r -> relId.equals(r.get("id")))
            .findFirst()
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found"));
    }

    @PostMapping("/entities/{entityId}/relationships")
    public ApiResponse<Map<String, Object>> createRelationship(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> rel = ontologyService.createRelationship(entityId, body);
        log.info("Relationship created: {} {}→{}", rel.get("id"), entityId, rel.get("targetEntityId"));
        return ApiResponse.success(rel);
    }

    @PostMapping("/relationships")
    public ApiResponse<Map<String, Object>> createRelationshipDirect(@RequestBody Map<String, Object> body) {
        String sourceEntityId = String.valueOf(body.getOrDefault("sourceEntityId", ""));
        Map<String, Object> rel = ontologyService.createRelationship(sourceEntityId, body);
        log.info("Relationship created directly: {} [{}]", rel.get("id"), rel.get("code"));
        return ApiResponse.success(rel);
    }

    @PutMapping("/relationships/{relId}")
    public ApiResponse<Map<String, Object>> updateRelationship(
            @PathVariable String relId,
            @RequestBody Map<String, Object> body) {
        // 关系更新: 通过查找并重新映射
        return ontologyService.listAllRelationships().stream()
            .filter(r -> relId.equals(r.get("id")))
            .findFirst()
            .map(existing -> {
                // 删除旧关系并创建新关系
                ontologyService.deleteRelationship(relId);
                String src = String.valueOf(body.getOrDefault("sourceEntityId", existing.get("sourceEntityId")));
                return ApiResponse.success(ontologyService.createRelationship(src, body));
            })
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found"));
    }

    @DeleteMapping("/entities/{entityId}/relationships/{relId}")
    public ApiResponse<String> deleteRelationshipByEntity(
            @PathVariable String entityId,
            @PathVariable String relId) {
        if (ontologyService.deleteRelationship(relId)) {
            return ApiResponse.success("Relationship '" + relId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found");
    }

    @DeleteMapping("/relationships/{relId}")
    public ApiResponse<String> deleteRelationship(@PathVariable String relId) {
        if (ontologyService.deleteRelationship(relId)) {
            return ApiResponse.success("Relationship '" + relId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found");
    }

    @PostMapping("/relationships/validate")
    public ApiResponse<Map<String, Object>> validateRelationship(@RequestBody Map<String, Object> body) {
        String source = String.valueOf(body.getOrDefault("sourceEntityId", ""));
        String target = String.valueOf(body.getOrDefault("targetEntityId", ""));
        Map<String, Object> result = ontologyService.validateRelationship(source, target);
        return ApiResponse.success(result);
    }

    @GetMapping("/relationships/graph")
    public ApiResponse<List<Map<String, Object>>> getRelationshipGraph() {
        return ApiResponse.success(ontologyService.getRelationshipGraph());
    }
}
