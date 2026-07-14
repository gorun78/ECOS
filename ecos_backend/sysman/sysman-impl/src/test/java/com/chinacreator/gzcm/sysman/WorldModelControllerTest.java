package com.chinacreator.gzcm.sysman;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.worldmodel.WorldModelService;
import com.chinacreator.gzcm.worldmodel.controller.WorldModelController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorldModelController 单元测试 — 目标/场景/因果链 CRUD + 因果图 + 目标树
 */
@ExtendWith(MockitoExtension.class)
class WorldModelControllerTest {

    @Mock
    private WorldModelService worldModelService;

    private WorldModelController controller;

    @BeforeEach
    void setUp() {
        controller = new WorldModelController(worldModelService);
    }

    // ── 辅助 ──

    private Map<String, Object> createGoal(int id, String name) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("name", name);
        g.put("description", "目标描述");
        g.put("status", "ACTIVE");
        g.put("currentValue", 50);
        g.put("targetValue", 100);
        g.put("unit", "%");
        g.put("category", "SERVICE");
        g.put("priority", 1);
        return g;
    }

    // ═══════════════ 目标查询 ═══════════════════

    @Test
    void listGoals_shouldReturnAllGoals() {
        when(worldModelService.listGoals())
            .thenReturn(List.of(createGoal(1, "提升通行效率"), createGoal(2, "降低养护成本")));

        ApiResponse<Map<String, Object>> resp = controller.listGoals();

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getData().get("data");
        assertEquals("提升通行效率", data.get(0).get("name"));
    }

    @Test
    void listGoals_shouldHandleEmpty() {
        when(worldModelService.listGoals()).thenReturn(List.of());

        ApiResponse<Map<String, Object>> resp = controller.listGoals();

        assertTrue(resp.isSuccess());
        assertEquals(0, resp.getData().get("total"));
    }

    // ═══════════════ 目标树 ═══════════════════

    @Test
    void goalTree_shouldReturnNestedStructure() {
        Map<String, Object> root = createGoal(1, "根目标");
        root.put("children", List.of(createGoal(2, "子目标")));
        when(worldModelService.goalTree()).thenReturn(List.of(root));

        ApiResponse<List<Map<String, Object>>> resp = controller.goalTree();

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
    }

    // ═══════════════ 场景查询 ═══════════════════

    @Test
    void listScenarios_shouldReturnAllScenarios() {
        when(worldModelService.listScenarios())
            .thenReturn(List.of(Map.of("id", 1, "name", "暴雨洪水", "probability", 0.3)));

        ApiResponse<Map<String, Object>> resp = controller.listScenarios();

        assertTrue(resp.isSuccess());
    }

    // ═══════════════ 因果链接 ═══════════════════

    @Test
    void listCausalLinks_shouldReturnLinks() {
        when(worldModelService.listCausalLinks())
            .thenReturn(List.of(Map.of("id", 1, "sourceGoalId", 1, "targetGoalId", 2, "relationType", "ENABLES")));

        ApiResponse<Map<String, Object>> resp = controller.listCausalLinks();

        assertTrue(resp.isSuccess());
    }

    // ═══════════════ 因果图 ═══════════════════

    @Test
    void causalGraph_shouldReturnNodesAndEdges() {
        Map<String, Object> graph = Map.of(
            "nodes", List.of(Map.of("id", 1, "label", "目标A")),
            "edges", List.of(Map.of("source", 1, "target", 2))
        );
        when(worldModelService.causalGraph()).thenReturn(graph);

        ApiResponse<Map<String, Object>> resp = controller.causalGraph();

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData().get("nodes"));
    }

    // ═══════════════ CRUD ═══════════════════

    @Test
    void create_shouldCreateGoal() {
        Map<String, Object> body = Map.of("name", "新目标", "category", "FINANCIAL");
        when(worldModelService.createGoal(body)).thenReturn(body);

        ApiResponse<Map<String, Object>> resp = controller.create("goals", body);

        assertTrue(resp.isSuccess());
        assertEquals("新目标", resp.getData().get("name"));
    }

    @Test
    void create_shouldReturn400_whenBadType() {
        ApiResponse<Map<String, Object>> resp = controller.create("bad-type", Map.of());

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("未知类型"));
    }

    @Test
    void update_shouldUpdateGoal() {
        Map<String, Object> body = Map.of("name", "已更新");
        when(worldModelService.updateGoal(eq(1L), eq(body))).thenReturn(Optional.of(body));

        ApiResponse<Map<String, Object>> resp = controller.update("goals", 1L, body);

        assertTrue(resp.isSuccess());
        assertEquals("已更新", resp.getData().get("name"));
    }

    @Test
    void update_shouldReturn404_whenNotFound() {
        when(worldModelService.updateGoal(eq(99L), anyMap())).thenReturn(Optional.empty());

        ApiResponse<Map<String, Object>> resp = controller.update("goals", 99L, Map.of());

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("99"));
    }

    @Test
    void delete_shouldDeleteGoal() {
        when(worldModelService.deleteGoal(1L)).thenReturn(true);

        ApiResponse<String> resp = controller.delete("goals", 1L);

        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().contains("已删除"));
    }

    @Test
    void delete_shouldReturn404_whenNotFound() {
        when(worldModelService.deleteGoal(999L)).thenReturn(false);

        ApiResponse<String> resp = controller.delete("goals", 999L);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("999"));
    }

    // ═══════════════ 场景对比 ═══════════════════

    @Test
    void compare_shouldReturnComparison() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenarioIds", List.of(1, 2));
        when(worldModelService.compareScenarios(anyList()))
            .thenReturn(List.of(Map.of("id", 1, "name", "场景A")));

        ApiResponse<List<Map<String, Object>>> resp = controller.compare(body);

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());
    }
}
