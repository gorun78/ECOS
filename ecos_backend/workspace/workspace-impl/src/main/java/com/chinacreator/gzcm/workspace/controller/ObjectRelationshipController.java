package com.chinacreator.gzcm.workspace.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ObjectRuntimeService;

/**
 * 对象关系图谱 Controller — 关系 CRUD + 图查询。
 *
 * <h3>端点</h3>
 * <pre>
 * GET    /api/v1/ecos/objects/{entityCode}/{id}/relationships       — 查询对象关系列表
 * POST   /api/v1/ecos/objects/{entityCode}/{id}/relationships       — 创建对象间关系
 * DELETE /api/v1/ecos/objects/{entityCode}/{id}/relationships/{relId} — 删除关系
 * GET    /api/v1/ecos/objects/{entityCode}/{id}/graph               — 获取关系图谱（N层展开）
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/objects")
public class ObjectRelationshipController {

    private static final Logger log = LoggerFactory.getLogger(ObjectRelationshipController.class);

    private final ObjectRuntimeService runtimeService;

    public ObjectRelationshipController(ObjectRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    // ═══════════════ 关系 CRUD ═══════════════════

    /**
     * GET /{entityCode}/{id}/relationships
     * 查询指定对象的所有直接关系。
     */
    @GetMapping("/{entityCode}/{id}/relationships")
    public ApiResponse<List<Map<String, Object>>> getRelationships(
            @PathVariable String entityCode,
            @PathVariable String id) {
        List<Map<String, Object>> rels = runtimeService.getRelationships(entityCode, id);
        return ApiResponse.success(rels);
    }

    /**
     * POST /{entityCode}/{id}/relationships
     * 创建对象间关系。
     *
     * Request body:
     * {
     *   "targetObjectId": "...",
     *   "targetEntityCode": "...",
     *   "relationshipCode": "...",
     *   "relationshipType": "OneToOne|OneToMany|ManyToMany",
     *   "properties": {...}
     * }
     */
    @PostMapping("/{entityCode}/{id}/relationships")
    public ApiResponse<Map<String, Object>> createRelationship(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        String targetObjectId = (String) body.get("targetObjectId");
        String targetEntityCode = (String) body.get("targetEntityCode");
        String relationshipCode = (String) body.get("relationshipCode");
        String relationshipType = (String) body.getOrDefault("relationshipType", "OneToMany");

        if (targetObjectId == null || targetEntityCode == null || relationshipCode == null) {
            return ApiResponse.badRequest("缺少必填参数: targetObjectId, targetEntityCode, relationshipCode");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) body.get("properties");

        try {
            Map<String, Object> result = runtimeService.createRelationship(
                id, targetObjectId, entityCode, targetEntityCode,
                relationshipCode, relationshipType, properties);

            // Record timeline
            runtimeService.recordTimeline(id, entityCode, "RelationshipCreated",
                "创建关系 " + relationshipCode + " → " + targetObjectId, "system",
                Map.of("relationshipCode", relationshipCode, "targetObjectId", targetObjectId));

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to create relationship for {}/{}: {}", entityCode, id, e.getMessage());
            return ApiResponse.internalError("关系创建失败: " + e.getMessage());
        }
    }

    /**
     * DELETE /{entityCode}/{id}/relationships/{relId}
     * 删除指定关系。
     */
    @DeleteMapping("/{entityCode}/{id}/relationships/{relId}")
    public ApiResponse<String> deleteRelationship(
            @PathVariable String entityCode,
            @PathVariable String id,
            @PathVariable String relId) {
        if (runtimeService.deleteRelationship(relId)) {
            runtimeService.recordTimeline(id, entityCode, "RelationshipDeleted",
                "删除关系 " + relId, "system", null);
            return ApiResponse.success("关系 " + relId + " 已删除");
        }
        return ApiResponse.notFound("OBJ-004: 关系 " + relId + " 不存在");
    }

    // ═══════════════ 关系图谱（图查询） ═══════════════════

    /**
     * GET /{entityCode}/{id}/graph?depth=2
     * 获取以指定对象为中心的关系图谱，支持 N 层展开。
     */
    @GetMapping("/{entityCode}/{id}/graph")
    public ApiResponse<Map<String, Object>> getGraph(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestParam(defaultValue = "2") int depth) {
        try {
            Map<String, Object> graph = runtimeService.getGraph(entityCode, id, depth);
            return ApiResponse.success(graph);
        } catch (Exception e) {
            log.error("Failed to build graph for {}/{}: {}", entityCode, id, e.getMessage());
            return ApiResponse.internalError("图谱生成失败: " + e.getMessage());
        }
    }
}
