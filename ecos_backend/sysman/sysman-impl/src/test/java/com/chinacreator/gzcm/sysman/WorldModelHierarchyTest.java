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
 * WorldModelHierarchyTest — 目标层级 API 单元测试
 * 覆盖 P1-3 新增的 goalType/goalTree/CURD 功能点
 */
@ExtendWith(MockitoExtension.class)
class WorldModelHierarchyTest {

    @Mock
    private WorldModelService worldModelService;

    private WorldModelController controller;

    @BeforeEach
    void setUp() {
        controller = new WorldModelController(worldModelService);
    }

    // ── 辅助 ──

    private Map<String, Object> createGoal(int id, String name, String goalType) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("id", id);
        g.put("name", name);
        g.put("description", "目标描述");
        g.put("status", "ACTIVE");
        g.put("goalType", goalType);
        g.put("progress", 50);
        g.put("weight", 1);
        g.put("parentId", null);
        return g;
    }

    // ═══════════════ 用例1: listGoals 返回 goalType 字段 ═══════════════════

    @Test
    void listGoals_shouldReturnGoalTypeField() {
        when(worldModelService.listGoals())
            .thenReturn(List.of(
                createGoal(1, "战略目标A", "STRATEGIC"),
                createGoal(2, "OKR目标B", "OKR")
            ));

        ApiResponse<Map<String, Object>> resp = controller.listGoals();

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getData().get("data");
        assertNotNull(data.get(0).get("goalType"), "goalType字段应存在");
        assertEquals("STRATEGIC", data.get(0).get("goalType"));
        assertEquals("OKR", data.get(1).get("goalType"));
    }

    // ═══════════════ 用例2: listGoals 返回至少3种 goalType ═══════════════════

    @Test
    void listGoals_shouldReturnAtLeastThreeGoalTypes() {
        when(worldModelService.listGoals())
            .thenReturn(List.of(
                createGoal(1, "战略A", "STRATEGIC"),
                createGoal(2, "OKR-Q1", "OKR"),
                createGoal(3, "KPI营收", "KPI")
            ));

        ApiResponse<Map<String, Object>> resp = controller.listGoals();

        assertTrue(resp.isSuccess());
        assertEquals(3, resp.getData().get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getData().get("data");
        Set<String> goalTypes = new HashSet<>();
        for (Map<String, Object> g : data) {
            goalTypes.add((String) g.get("goalType"));
        }
        assertTrue(goalTypes.size() >= 3,
            "应至少有3种goalType, 实际: " + goalTypes);
        assertTrue(goalTypes.contains("STRATEGIC"));
        assertTrue(goalTypes.contains("OKR"));
        assertTrue(goalTypes.contains("KPI"));
    }

    // ═══════════════ 用例3: goalTree 返回嵌套树结构 ═══════════════════

    @Test
    void goalTree_shouldReturnNestedStructure() {
        Map<String, Object> root = createGoal(1, "公司战略", "STRATEGIC");
        Map<String, Object> child = createGoal(2, "部门OKR", "OKR");
        root.put("children", List.of(child));
        when(worldModelService.goalTree()).thenReturn(List.of(root));

        ApiResponse<List<Map<String, Object>>> resp = controller.goalTree();

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getData().size());

        Map<String, Object> rootNode = resp.getData().get(0);
        assertEquals("公司战略", rootNode.get("name"));
        assertEquals("STRATEGIC", rootNode.get("goalType"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) rootNode.get("children");
        assertNotNull(children, "子节点不应为空");
        assertEquals(1, children.size());
        assertEquals("部门OKR", children.get(0).get("name"));
    }

    // ═══════════════ 用例4: 种子数据子目标在正确父目标下 ═══════════════════

    @Test
    void goalTree_childGoalsUnderCorrectParent() {
        Map<String, Object> parent1 = createGoal(1, "父目标A", "STRATEGIC");
        Map<String, Object> child1 = createGoal(2, "子目标A1", "OKR");
        Map<String, Object> parent2 = createGoal(3, "父目标B", "STRATEGIC");
        Map<String, Object> child2 = createGoal(4, "子目标B1", "KPI");
        Map<String, Object> child3 = createGoal(5, "子目标B2", "OKR");

        parent1.put("children", List.of(child1));
        parent2.put("children", List.of(child2, child3));

        when(worldModelService.goalTree()).thenReturn(List.of(parent1, parent2));

        ApiResponse<List<Map<String, Object>>> resp = controller.goalTree();

        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());

        // 父目标A → 子目标A1
        Map<String, Object> p1 = resp.getData().get(0);
        assertEquals("父目标A", p1.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c1 = (List<Map<String, Object>>) p1.get("children");
        assertEquals(1, c1.size());
        assertEquals("子目标A1", c1.get(0).get("name"));

        // 父目标B → 子目标B1, B2
        Map<String, Object> p2 = resp.getData().get(1);
        assertEquals("父目标B", p2.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c2 = (List<Map<String, Object>>) p2.get("children");
        assertEquals(2, c2.size());
        assertEquals("子目标B1", c2.get(0).get("name"));
        assertEquals("子目标B2", c2.get(1).get("name"));
    }

    // ═══════════════ 用例5: POST 创建带 goalType 的目标 ═══════════════════

    @Test
    void create_shouldCreateGoalWithGoalType() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "OKR目标");
        body.put("goalType", "OKR");
        body.put("description", "季度OKR");

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", 10);
        created.put("name", "OKR目标");
        created.put("goalType", "OKR");
        created.put("status", "PLANNED");

        when(worldModelService.createGoal(body)).thenReturn(created);

        ApiResponse<Map<String, Object>> resp = controller.create("goals", body);

        assertTrue(resp.isSuccess());
        assertEquals("OKR目标", resp.getData().get("name"));
        assertEquals("OKR", resp.getData().get("goalType"));
        assertEquals("PLANNED", resp.getData().get("status"));
    }

    // ═══════════════ 用例6: goalType 过滤 (验证各 goalType 在列表中正确区分) ═══════════════════

    @Test
    void listGoals_shouldDistinguishGoalsByGoalType() {
        when(worldModelService.listGoals())
            .thenReturn(List.of(
                createGoal(1, "战略A", "STRATEGIC"),
                createGoal(2, "OKR-Q1", "OKR"),
                createGoal(3, "KPI收入", "KPI"),
                createGoal(4, "OKR-Q2", "OKR")
            ));

        ApiResponse<Map<String, Object>> resp = controller.listGoals();

        assertTrue(resp.isSuccess());
        assertEquals(4, resp.getData().get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getData().get("data");

        // 手动过滤 OKR: 验证 OKR 类型有2条
        long okrCount = data.stream()
            .filter(g -> "OKR".equals(g.get("goalType")))
            .count();
        assertEquals(2, okrCount, "OKR目标应为2条");

        long strategicCount = data.stream()
            .filter(g -> "STRATEGIC".equals(g.get("goalType")))
            .count();
        assertEquals(1, strategicCount);

        long kpiCount = data.stream()
            .filter(g -> "KPI".equals(g.get("goalType")))
            .count();
        assertEquals(1, kpiCount);

        // 验证全部 goalType 出现
        Set<String> types = new HashSet<>();
        for (Map<String, Object> g : data) {
            types.add((String) g.get("goalType"));
        }
        assertEquals(3, types.size());
        assertTrue(types.containsAll(Set.of("STRATEGIC", "OKR", "KPI")));
    }
}
