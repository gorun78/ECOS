package com.chinacreator.gzcm.workspace.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.StateMachineEngine;

/**
 * 对象状态机 Controller — 状态转换查询与执行。
 *
 * <h3>端点</h3>
 * <pre>
 * GET    /api/v1/ecos/objects/{entityCode}/{id}/transitions  — 查询可用状态转换
 * POST   /api/v1/ecos/objects/{entityCode}/{id}/transition   — 执行状态转换
 * GET    /api/v1/ecos/statemachine/{entityCode}/definitions  — 状态机定义列表
 * POST   /api/v1/ecos/statemachine/definitions               — 创建状态机定义
 * DELETE /api/v1/ecos/statemachine/definitions/{id}          — 删除状态机定义
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class ObjectStateMachineController {

    private static final Logger log = LoggerFactory.getLogger(ObjectStateMachineController.class);
    private static final Map<String, String> ENTITY_TABLE = Map.of(
        "Customer", "demo_customer",
        "Supplier", "demo_supplier",
        "Invoice",  "demo_invoice"
    );

    private final JdbcTemplate jdbc;
    private final StateMachineEngine engine;

    public ObjectStateMachineController(JdbcTemplate jdbc, StateMachineEngine engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    // ═══════════════ 状态转换查询 ═══════════════════

    /**
     * GET /api/v1/ecos/objects/{entityCode}/{id}/transitions
     * 查询指定对象当前状态下可用的状态转换。
     */
    @GetMapping("/objects/{entityCode}/{id}/transitions")
    public ApiResponse<Map<String, Object>> getTransitions(
            @PathVariable String entityCode,
            @PathVariable String id) {
        // 获取对象当前状态
        String currentStatus = getObjectStatus(entityCode, id);
        if (currentStatus == null) {
            return ApiResponse.notFound("OBJ-001: 对象 " + entityCode + "/" + id + " 不存在");
        }
        Map<String, Object> result = engine.getTransitionsInfo(entityCode, currentStatus);
        return ApiResponse.success(result);
    }

    // ═══════════════ 执行状态转换 ═══════════════════

    /**
     * POST /api/v1/ecos/objects/{entityCode}/{id}/transition
     * 对指定对象执行状态转换。
     */
    @PostMapping("/objects/{entityCode}/{id}/transition")
    public ApiResponse<Map<String, Object>> executeTransition(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String transitionCode = (String) body.get("transition");
        if (transitionCode == null || transitionCode.isEmpty()) {
            return ApiResponse.badRequest("缺少 transition 参数");
        }

        // 获取对象当前状态
        String currentStatus = getObjectStatus(entityCode, id);
        if (currentStatus == null) {
            return ApiResponse.notFound("OBJ-001: 对象 " + entityCode + "/" + id + " 不存在");
        }

        String comment = (String) body.getOrDefault("comment", "");
        String actor = (String) body.getOrDefault("actor", "system");

        try {
            Map<String, Object> result = engine.executeTransition(
                entityCode, id, currentStatus, transitionCode, actor, comment);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Transition execution failed for {}/{}: {}", entityCode, id, e.getMessage());
            return ApiResponse.internalError("状态转换失败: " + e.getMessage());
        }
    }

    // ═══════════════ 状态机定义查询（新增简化端点） ═══════════════════

    /**
     * GET /api/v1/ecos/state-machine/{entityCode}
     * 返回指定实体的完整状态机定义（所有状态和转换规则）。
     */
    @GetMapping("/state-machine/{entityCode}")
    public ApiResponse<Map<String, Object>> getStateMachine(@PathVariable String entityCode) {
        try {
            List<Map<String, Object>> defs = engine.listDefinitions(entityCode);

            // Build state machine model: states + transitions
            Set<String> states = new LinkedHashSet<>();
            List<Map<String, Object>> transitions = new ArrayList<>();

            for (Map<String, Object> def : defs) {
                String fromStatus = (String) def.get("from_status");
                String toStatus = (String) def.get("to_status");
                if (fromStatus != null) states.add(fromStatus);
                if (toStatus != null) states.add(toStatus);

                Map<String, Object> t = new LinkedHashMap<>();
                t.put("from", fromStatus);
                t.put("to", toStatus);
                t.put("transitionCode", def.get("transition_code"));
                t.put("transitionName", def.get("transition_name"));
                t.put("requireRole", def.get("require_role"));
                t.put("guardRule", def.get("guard_rule"));
                t.put("sideEffect", def.get("side_effect"));
                t.put("sortOrder", def.get("sort_order"));
                transitions.add(t);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityCode", entityCode);
            result.put("states", new ArrayList<>(states));
            result.put("transitions", transitions);
            result.put("totalTransitions", transitions.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to get state machine for {}: {}", entityCode, e.getMessage());
            return ApiResponse.internalError("获取状态机定义失败: " + e.getMessage());
        }
    }

    // ═══════════════ 状态机定义管理 ═══════════════════

    @GetMapping("/statemachine/{entityCode}/definitions")
    public ApiResponse<List<Map<String, Object>>> listDefinitions(@PathVariable String entityCode) {
        return ApiResponse.success(engine.listDefinitions(entityCode));
    }

    @PostMapping("/statemachine/definitions")
    public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(engine.createDefinition(body));
        } catch (Exception e) {
            log.error("Failed to create state machine def: {}", e.getMessage());
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/statemachine/definitions/{id}")
    public ApiResponse<String> deleteDefinition(@PathVariable String id) {
        if (engine.deleteDefinition(id)) {
            return ApiResponse.success("定义 " + id + " 已删除");
        }
        return ApiResponse.notFound("定义 " + id + " 不存在");
    }

    // ═══════════════ Helper ═══════════════════

    /**
     * 从 demo 表中查询对象当前状态。
     */
    private String getObjectStatus(String entityCode, String objectId) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return null;

        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status FROM " + table + " WHERE id = ?", objectId);
            if (rows.isEmpty()) return null;

            Object status = rows.get(0).get("status");
            if (status == null) return "Draft";
            String s = status.toString().trim();
            return s.isEmpty() ? "Draft" : s;
        } catch (Exception e) {
            log.warn("Failed to get status for {}/{}: {}", entityCode, objectId, e.getMessage());
            return null;
        }
    }
}
