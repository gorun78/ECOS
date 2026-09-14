package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.ActionTypeService;
import com.chinacreator.gzcm.engine.ontology.dto.ActionTypeExecuteQuery;
import com.chinacreator.gzcm.engine.ontology.dto.ActionTypeExecuteVO;
import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ActionType REST API — 动作类型 CRUD + execute（T16-5 execute 强类型化）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>POST   /api/v1/ontology/action-types              — 创建 ActionType</li>
 *   <li>GET    /api/v1/ontology/action-types              — 列表（支持 ?objectTypeId= 过滤）</li>
 *   <li>GET    /api/v1/ontology/action-types/{id}         — 详情</li>
 *   <li>PUT    /api/v1/ontology/action-types/{id}         — 更新</li>
 *   <li>DELETE /api/v1/ontology/action-types/{id}         — 删除</li>
 *   <li>POST   /api/v1/ontology/action-types/{id}/execute — 执行（T16-5 强类型入出参）</li>
 * </ul>
 *
 * <p>T16-5：/execute 入参由 {@code Map} 改 {@link ActionTypeExecuteQuery}，
 * 返回由 {@code Map} 改 {@link ActionTypeExecuteVO}（嵌套
 * preconditionCheck/execution/postActions 动态结构豁免，Object 保留）。
 * CRUD 端点本就使用 {@code ActionType} POJO（非 Map）。
 */
@RestController
@RequestMapping("/api/v1/ontology/action-types")
public class ActionTypeController {

    private static final Logger log = LoggerFactory.getLogger(ActionTypeController.class);

    /** Controller 层 Map → VO 转换用的 Jackson Mapper（static 单例，零开销）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * 执行 ActionType（T16-5 强类型入出参）。
     *
     * <p>底层 {@code ActionTypeService.executeAction} 为 ontology-engine-api 契约
     * （Map 签名，只增不改）；本控制器仅在 Controller 层做
     * {@link ActionTypeExecuteQuery} → Map 入参组装与 Jackson Map →
     * {@link ActionTypeExecuteVO} 转换。{@code preconditionCheck / execution /
     * postActions} 为运行时动态结构，VO 内保持 Object 豁免（POJO 序列化 == Map 序列化）。
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<ActionTypeExecuteVO> execute(
            @PathVariable String id,
            @RequestBody ActionTypeExecuteQuery query) {
        try {
            Map<String, Object> result = actionTypeService.executeAction(id, toPayload(query));
            return ApiResponse.success(toVO(result));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("ActionType execute failed: id={}", id, e);
            return ApiResponse.internalError("Execution failed: " + e.getMessage());
        }
    }

    // ═══════════════ 内部转换（强类型 ↔ service 旧 Map 契约）═══════════════

    /**
     * Query → service 旧 Map 入参。
     * <p>仅组装 service 消费的 {@code objectId / context} 两键；缺省时 service 走
     * {@code getOrDefault("")} / 空 Map 默认路径（与既有 Map 入参行为等价）。
     * {@code context} 非 Map 时不放入（契约收紧：旧 Map 强制拆箱会 CCE，现安全降级为空）。
     */
    private Map<String, Object> toPayload(ActionTypeExecuteQuery query) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (query == null) {
            return payload;
        }
        if (query.getObjectId() != null) {
            payload.put("objectId", query.getObjectId());
        }
        if (query.getContext() instanceof Map<?, ?> contextMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) contextMap;
            payload.put("context", context);
        }
        return payload;
    }

    /** service 旧 Map 返回 → ActionTypeExecuteVO（字段名等价，嵌套动态结构 Object 承载）。 */
    private ActionTypeExecuteVO toVO(Map<String, Object> row) {
        if (row == null) {
            return new ActionTypeExecuteVO();
        }
        return MAPPER.convertValue(row, ActionTypeExecuteVO.class);
    }
}
