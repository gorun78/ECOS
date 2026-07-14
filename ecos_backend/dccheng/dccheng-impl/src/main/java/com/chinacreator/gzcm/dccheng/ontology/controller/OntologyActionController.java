package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyActionService;

/**
 * 动作设计器 REST API — 动作 CRUD（PostgreSQL 持久化）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/actions           — 指定实体的动作列表</li>
 *   <li>GET    /api/v1/ecos/actions                                — 全部动作列表</li>
 *   <li>POST   /api/v1/ecos/entities/{entityId}/actions           — 创建动作</li>
 *   <li>PUT    /api/v1/ecos/entities/{entityId}/actions/{id}      — 更新动作</li>
 *   <li>DELETE /api/v1/ecos/entities/{entityId}/actions/{id}      — 删除动作</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyActionController {

    private static final Logger log = LoggerFactory.getLogger(OntologyActionController.class);

    private final OntologyActionService actionService;

    public OntologyActionController(OntologyActionService actionService) {
        this.actionService = actionService;
    }

    // ═══════════════ 动作 CRUD ═══════════════════

    @GetMapping("/entities/{entityId}/actions")
    public ApiResponse<List<Map<String, Object>>> listActions(@PathVariable String entityId) {
        return ApiResponse.success(actionService.listActionsByEntity(entityId));
    }

    @GetMapping("/actions")
    public ApiResponse<List<Map<String, Object>>> listAllActions() {
        return ApiResponse.success(actionService.listAllActions());
    }

    @GetMapping("/actions/{actionId}")
    public ApiResponse<Map<String, Object>> getAction(@PathVariable String actionId) {
        Map<String, Object> act = actionService.getAction(actionId);
        if (act == null) return ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found");
        return ApiResponse.success(act);
    }

    @PostMapping("/entities/{entityId}/actions")
    public ApiResponse<Map<String, Object>> createAction(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> act = actionService.createAction(entityId, body);
        log.info("Action created via DB: {} [{}] for entity {}", act.get("id"), act.get("code"), entityId);
        return ApiResponse.success(act);
    }

    @PutMapping("/actions/{actionId}")
    public ApiResponse<Map<String, Object>> updateAction(
            @PathVariable String actionId,
            @RequestBody Map<String, Object> body) {
        return actionService.updateAction(actionId, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found"));
    }

    @DeleteMapping("/actions/{actionId}")
    public ApiResponse<String> deleteAction(@PathVariable String actionId) {
        if (actionService.deleteAction(actionId)) {
            return ApiResponse.success("Action '" + actionId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found");
    }

    @PostMapping("/actions/{actionId}/test")
    public ApiResponse<Map<String, Object>> testAction(@PathVariable String actionId) {
        try {
            return ApiResponse.success(actionService.testAction(actionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
