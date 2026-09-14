package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyActionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyActionResultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyActionVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyActionService;

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
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型 DTO/VO
 * （{@code OntologyActionSaveDTO} / {@code OntologyActionVO} / {@code OntologyActionResultVO}）。
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

    /** 列出实体的动作（强类型版本）。 */
    @GetMapping("/entities/{entityId}/actions")
    public ApiResponse<List<OntologyActionVO>> listActions(@PathVariable String entityId) {
        return ApiResponse.success(actionService.listActionsByEntityVO(entityId));
    }

    /** 列出全部动作（强类型版本）。 */
    @GetMapping("/actions")
    public ApiResponse<List<OntologyActionVO>> listAllActions() {
        return ApiResponse.success(actionService.listAllActionsVO());
    }

    /** 动作详情 — 强类型版本。 */
    @GetMapping("/actions/{actionId}")
    public ApiResponse<OntologyActionVO> getAction(@PathVariable String actionId) {
        OntologyActionVO act = actionService.getActionVO(actionId);
        if (act == null) {
            return ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found");
        }
        return ApiResponse.success(act);
    }

    /** 创建动作 — 接收 {@code OntologyActionSaveDTO}。 */
    @PostMapping("/entities/{entityId}/actions")
    public ApiResponse<OntologyActionVO> createAction(
            @PathVariable String entityId,
            @RequestBody OntologyActionSaveDTO dto) {
        OntologyActionVO act = actionService.createAction(entityId, dto);
        log.info("Action created via DB: {} [{}] for entity {}", act.getId(), act.getCode(), entityId);
        return ApiResponse.success(act);
    }

    /** 更新动作 — 接收 {@code OntologyActionSaveDTO}。 */
    @PutMapping("/actions/{actionId}")
    public ApiResponse<OntologyActionVO> updateAction(
            @PathVariable String actionId,
            @RequestBody OntologyActionSaveDTO dto) {
        return actionService.updateAction(actionId, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found"));
    }

    /** 删除动作（保留 String 简返，与旧路径一致）。 */
    @DeleteMapping("/actions/{actionId}")
    public ApiResponse<String> deleteAction(@PathVariable String actionId) {
        if (actionService.deleteAction(actionId)) {
            return ApiResponse.success("Action '" + actionId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Action '" + actionId + "' not found");
    }

    /** 测试动作（模拟执行摘要） — 强类型版本。 */
    @PostMapping("/actions/{actionId}/test")
    public ApiResponse<OntologyActionResultVO> testAction(@PathVariable String actionId) {
        try {
            return ApiResponse.success(actionService.testActionVO(actionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
