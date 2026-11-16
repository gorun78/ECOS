package com.chinacreator.gzcm.sysman.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.service.WorldModelGraphQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World Model 目标/因果图 wire 保真（DI-2 #147DT）。
 *
 * <p>目标行 22 键在两源同键不同型下保持原值类型：DB 路径 id/parentId 数值、createdAt ISO 字符串；
 * 内存兜底 id/parentId 业务字符串、createdAt 毫秒数。树形 children 仅非空时出现；
 * 因果图节点 DB 5 键 / 内存 4 键（type 条件键），边 DB 5 键 / 内存 4 键（description 条件键）。</p>
 */
class WorldModelGraphWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> GOAL_KEYS = List.of("id", "name", "description", "parentId",
            "progress", "status", "goalType", "weight", "orgId", "ownerUserId", "startDate",
            "endDate", "targetValue", "currentValue", "unit", "linkedWorkflowId", "kpiFormula",
            "measureFrequency", "alertThresholdWarn", "alertThresholdCritical", "createdAt",
            "updatedAt");

    /** DB 可用/不可用两态 + 两种行源。 */
    private static final class FakeQueryService extends WorldModelGraphQueryService {
        private final boolean dbUp;

        private FakeQueryService(boolean dbUp) {
            super(null);
            this.dbUp = dbUp;
        }

        @Override
        public Integer ping() {
            if (!dbUp) {
                throw new IllegalStateException("db down");
            }
            return 1;
        }

        @Override
        public List<Map<String, Object>> listGoals() {
            if (!dbUp) {
                throw new IllegalStateException("db down");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("id", 1L);
            root.put("name", "总体目标");
            root.put("description", null);
            root.put("parentId", null);
            root.put("progress", 0);
            root.put("status", "PLANNED");
            root.put("goalType", null);
            root.put("weight", null);
            root.put("orgId", null);
            root.put("ownerUserId", null);
            root.put("startDate", null);
            root.put("endDate", null);
            root.put("targetValue", null);
            root.put("currentValue", null);
            root.put("unit", null);
            root.put("linkedWorkflowId", null);
            root.put("kpiFormula", null);
            root.put("measureFrequency", null);
            root.put("alertThresholdWarn", null);
            root.put("alertThresholdCritical", null);
            root.put("createdAt", "2026-11-05T10:00");
            root.put("updatedAt", "2026-11-05T10:00");
            rows.add(root);

            Map<String, Object> child = new LinkedHashMap<>(root);
            child.put("id", 2L);
            child.put("name", "子目标");
            child.put("parentId", 1L);
            rows.add(child);
            return rows;
        }

        @Override
        public List<Map<String, Object>> listCausalGraphNodes() {
            if (!dbUp) {
                throw new IllegalStateException("db down");
            }
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", "goal-1");
            n.put("label", "总体目标");
            n.put("description", "desc");
            n.put("status", "PLANNED");
            n.put("type", "STRATEGIC");
            return List.of(n);
        }

        @Override
        public List<Map<String, Object>> listCausalGraphEdges() {
            if (!dbUp) {
                throw new IllegalStateException("db down");
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "edge-1");
            e.put("source", "goal-1");
            e.put("target", "goal-2");
            e.put("label", "causal");
            e.put("description", "关系说明");
            return List.of(e);
        }
    }

    @Test
    @DisplayName("DB 路径：目标 22 键、id/parentId 保持数值、createdAt 保持 ISO 字符串")
    void dbGoalsKeepNativeTypes() throws Exception {
        WorldModelGraphController controller = new WorldModelGraphController(new FakeQueryService(true), null);

        JsonNode data = dataOf(controller.listGoals());

        assertEquals(List.of("data", "total"), fieldNames(data));
        assertEquals(2, data.get("total").asInt());
        JsonNode goal = data.get("data").get(1);
        assertEquals(GOAL_KEYS, fieldNames(goal), "空值键也在（旧 Map 同形）");
        assertTrue(goal.get("id").isNumber(), "DB 路径 id 为数值");
        assertTrue(goal.get("parentId").isNumber());
        assertTrue(goal.get("createdAt").isTextual(), "DB 路径时间为 ISO 字符串");
        assertEquals("2026-11-05T10:00", goal.get("createdAt").asText());
    }

    @Test
    @DisplayName("D103 订正：DB 不可用时不再拿内存假数据兜底，如实报错")
    void memoryFallbackRemoved() throws Exception {
        WorldModelGraphController controller = new WorldModelGraphController(
                new FakeQueryService(false), null);

        ApiResponse<?> listResp = controller.listGoals();
        assertFalse(listResp.isSuccess(), "DB 不可用必须如实失败，不得用 root-goal 假数据冒充");
        ApiResponse<?> graphResp = controller.causalGraph();
        assertFalse(graphResp.isSuccess(), "因果图同口径：不可用即失败");
    }

    @Test
    @DisplayName("因果图：DB 路径节点/边各 5 键（内存兜底形态已随 D103 删除）")
    void causalGraphKeepsDbShape() throws Exception {
        JsonNode db = dataOf(new WorldModelGraphController(new FakeQueryService(true), null).causalGraph());
        assertEquals(List.of("nodes", "edges", "nodeCount", "edgeCount"), fieldNames(db));
        assertEquals(List.of("id", "label", "description", "status", "type"),
                fieldNames(db.get("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "label", "description"),
                fieldNames(db.get("edges").get(0)));
    }

    @Test
    @DisplayName("删除回执与因果链列表：键面固定")
    void deleteAndLinkListKeepKeys() throws Exception {
        WorldModelGraphController controller = new WorldModelGraphController(
                new FakeQueryService(false), null);

        // D103：删除目标在没有真实 DAO 时不应谎报成功
        ApiResponse<?> deletedGoal = controller.deleteGoal("child-goal");
        assertFalse(deletedGoal.isSuccess(), "无 DAO 时删除必须失败，不得返回 success:true");

        WorldModelGraphController dbController = new WorldModelGraphController(
                new FakeQueryService(true), null);
        JsonNode links = dataOf(dbController.listLinks());
        assertEquals(List.of("data", "total"), fieldNames(links));
        // D103：listLinks 现在与因果图同源（dbUp=true 的边源恒 1 条），不再是内存 Map 的空列表
        assertEquals(1, links.get("total").asInt());
        assertEquals("1", links.get("data").get(0).get("id").asText(), "edge- 前缀须剥掉");
        assertEquals("1", links.get("data").get(0).get("sourceGoalId").asText(), "goal- 前缀须剥掉");
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static JsonNode dataOf(ApiResponse<?> resp) throws Exception {
        assertEquals(0, resp.getCode(), "夹具必须走成功信封: " + resp.getMessage());
        return MAPPER.valueToTree(resp).get("data");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
