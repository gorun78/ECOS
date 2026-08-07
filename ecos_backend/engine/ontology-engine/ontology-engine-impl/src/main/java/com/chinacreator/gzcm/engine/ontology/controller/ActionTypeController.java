package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ActionType REST API — 动作类型 CRUD + execute。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>POST   /api/v1/ontology/actions              — 创建 ActionType</li>
 *   <li>GET    /api/v1/ontology/actions              — 列表（支持 ?objectTypeId= 过滤）</li>
 *   <li>GET    /api/v1/ontology/actions/{id}         — 详情</li>
 *   <li>PUT    /api/v1/ontology/actions/{id}         — 更新</li>
 *   <li>DELETE /api/v1/ontology/actions/{id}         — 删除</li>
 *   <li>POST   /api/v1/ontology/actions/{id}/execute — 执行</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/action-types")
public class ActionTypeController {

    private static final Logger log = LoggerFactory.getLogger(ActionTypeController.class);

    private final ActionTypeService actionTypeService;

    public ActionTypeController(ActionTypeService actionTypeService) {
        this.actionTypeService = actionTypeService;
    }

    // ═══════════════ CRUD ═══════════════════

    @PostMapping
    public ApiResponse<ActionType> create(@RequestBody ActionType body) {
        if (body.getName() == null || body.getName().isBlank()) {
            return ApiResponse.badRequest("ONT-002: 'name' is required");
        }
        if (body.getObjectTypeId() == null || body.getObjectTypeId().isBlank()) {
            return ApiResponse.badRequest("ONT-002: 'objectTypeId' is required");
        }
        ActionType created = actionTypeService.createActionType(body);
        log.info("ActionType created: {}", created.getId());
        return ApiResponse.success(created);
    }

    @GetMapping
    public ApiResponse<List<ActionType>> list(
            @RequestParam(value = "objectTypeId", required = false) String objectTypeId) {
        return ApiResponse.success(actionTypeService.listActionTypes(objectTypeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActionType> get(@PathVariable String id) {
        return actionTypeService.getActionType(id)
            .map(ApiResponse::success)
            .orElse(ApiResponse.notFound("ONT-001: ActionType '" + id + "' not found"));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActionType> update(@PathVariable String id, @RequestBody ActionType body) {
        ActionType updated = actionTypeService.updateActionType(id, body);
        if (updated == null) {
            return ApiResponse.notFound("ONT-001: ActionType '" + id + "' not found");
        }
        return ApiResponse.success(updated);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable String id) {
        if (actionTypeService.deleteActionType(id)) {
            return ApiResponse.success("ActionType '" + id + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: ActionType '" + id + "' not found");
    }

    // ═══════════════ execute ═══════════════════

    @PostMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> execute(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> result = actionTypeService.executeAction(id, payload);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("ActionType execute failed: id={}", id, e);
            return ApiResponse.internalError("Execution failed: " + e.getMessage());
        }
    }
}
