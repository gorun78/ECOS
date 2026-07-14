package com.chinacreator.gzcm.common.engine;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.chinacreator.gzcm.common.service.ObjectRuntimeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 状态机引擎 — 管理对象状态流转。
 *
 * <p>从 ecos_object_state_machine 表读取状态转换定义，
 * 校验转换合法性，执行状态变更并发布事件。
 */
@Service
public class StateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngine.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(200);

    private final JdbcTemplate jdbc;
    private final ObjectRuntimeService runtimeService;

    public StateMachineEngine(JdbcTemplate jdbc, ObjectRuntimeService runtimeService) {
        this.jdbc = jdbc;
        this.runtimeService = runtimeService;
    }

    private String nextId() { return "smdef-" + ID_SEQ.incrementAndGet(); }

    // ═══════════════ 状态流转查询 ═══════════════════

    /**
     * 查询指定实体+当前状态下可用的状态转换。
     */
    public List<Map<String, Object>> getAvailableTransitions(String entityCode, String currentStatus) {
        String sql = """
            SELECT id, entity_code, from_status, to_status, transition_code, transition_name,
                   require_role, guard_rule, side_effect, sort_order
            FROM ecos_object_state_machine
            WHERE entity_code = ? AND from_status = ?
            ORDER BY sort_order
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, entityCode, currentStatus);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("to", row.get("to_status"));
                t.put("transitionCode", row.get("transition_code"));
                t.put("label", row.get("transition_name"));
                t.put("requireRole", row.get("require_role"));
                result.add(t);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to query transitions for entity={} status={}: {}", entityCode, currentStatus, e.getMessage());
            return List.of();
        }
    }

    /**
     * 查询完整的转换信息（含当前状态和可用转换列表）。
     */
    public Map<String, Object> getTransitionsInfo(String entityCode, String currentStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentStatus", currentStatus);
        result.put("availableTransitions", getAvailableTransitions(entityCode, currentStatus));
        return result;
    }

    // ═══════════════ 执行状态转换 ═══════════════════

    /**
     * 执行状态转换。
     *
     * @param entityCode    实体代码
     * @param objectId      对象ID
     * @param currentStatus 当前状态
     * @param transitionCode 转换动作编码
     * @param actor         操作人
     * @param comment       备注
     * @return 转换结果 {previousStatus, newStatus, timestamp}
     */
    public Map<String, Object> executeTransition(String entityCode, String objectId,
                                                  String currentStatus, String transitionCode,
                                                  String actor, String comment) {
        // 1. 查找转换定义
        String sql = """
            SELECT to_status, transition_name, require_role
            FROM ecos_object_state_machine
            WHERE entity_code = ? AND from_status = ? AND transition_code = ?
            """;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(sql, entityCode, currentStatus, transitionCode);
        } catch (Exception e) {
            log.error("Failed to query state machine for {}/{}/{}: {}", entityCode, currentStatus, transitionCode, e.getMessage());
            throw new IllegalArgumentException("状态机查询失败: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("OBJ-002: 状态转换非法 — " +
                entityCode + " 不支持从 " + currentStatus + " 执行 " + transitionCode);
        }

        String newStatus = (String) rows.get(0).get("to_status");
        String transitionName = (String) rows.get(0).get("transition_name");

        // 2. 更新对象状态（demo表）
        String table = ObjectRuntimeService.ENTITY_TABLE.get(entityCode);
        if (table != null) {
            try {
                int updated = jdbc.update("UPDATE " + table + " SET status = ? WHERE id = ?", newStatus, objectId);
                if (updated == 0) {
                    throw new IllegalArgumentException("OBJ-001: 对象 " + entityCode + "/" + objectId + " 不存在");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to update status for {}/{}: {}", entityCode, objectId, e.getMessage());
                throw new RuntimeException("状态更新失败: " + e.getMessage());
            }
        }

        // 3. 记录 Timeline
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transitionCode", transitionCode);
        details.put("transitionName", transitionName);
        details.put("comment", comment);
        runtimeService.recordTimeline(objectId, entityCode, "StatusChanged",
            transitionName + ": " + currentStatus + " → " + newStatus, actor, details);

        // 4. 发布事件
        runtimeService.publishStatusChanged(objectId, entityCode, currentStatus, newStatus);

        // 5. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("previousStatus", currentStatus);
        result.put("newStatus", newStatus);
        result.put("transitionCode", transitionCode);
        result.put("transitionName", transitionName);
        result.put("timestamp", java.time.Instant.now().toString());
        return result;
    }

    // ═══════════════ 状态机定义管理 (CRUD) ═══════════════════

    public List<Map<String, Object>> listDefinitions(String entityCode) {
        String sql = """
            SELECT id, entity_code, from_status, to_status, transition_code, transition_name,
                   require_role, guard_rule, side_effect, sort_order, created_at
            FROM ecos_object_state_machine
            WHERE entity_code = ?
            ORDER BY sort_order
            """;
        try {
            return jdbc.queryForList(sql, entityCode);
        } catch (Exception e) {
            log.warn("Failed to list state machine defs for {}: {}", entityCode, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> createDefinition(Map<String, Object> body) {
        String id = nextId();
        String entityCode = (String) body.get("entityCode");
        String fromStatus = (String) body.get("fromStatus");
        String toStatus = (String) body.get("toStatus");
        String transitionCode = (String) body.get("transitionCode");
        String transitionName = (String) body.getOrDefault("transitionName", transitionCode);
        String requireRole = (String) body.getOrDefault("requireRole", null);
        String guardRule = (String) body.getOrDefault("guardRule", null);
        String sideEffect = (String) body.getOrDefault("sideEffect", null);
        int sortOrder = body.containsKey("sortOrder") ? ((Number) body.get("sortOrder")).intValue() : 0;

        jdbc.update("""
            INSERT INTO ecos_object_state_machine (id, entity_code, from_status, to_status, transition_code, transition_name, require_role, guard_rule, side_effect, sort_order, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """, id, entityCode, fromStatus, toStatus, transitionCode, transitionName, requireRole, guardRule, sideEffect, sortOrder);

        log.info("State machine def created: {} [{}: {}→{} via {}]", id, entityCode, fromStatus, toStatus, transitionCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("entityCode", entityCode);
        result.put("fromStatus", fromStatus);
        result.put("toStatus", toStatus);
        result.put("transitionCode", transitionCode);
        result.put("transitionName", transitionName);
        return result;
    }

    public boolean deleteDefinition(String id) {
        int rows = jdbc.update("DELETE FROM ecos_object_state_machine WHERE id = ?", id);
        if (rows > 0) {
            log.info("State machine def deleted: {}", id);
        }
        return rows > 0;
    }
}
