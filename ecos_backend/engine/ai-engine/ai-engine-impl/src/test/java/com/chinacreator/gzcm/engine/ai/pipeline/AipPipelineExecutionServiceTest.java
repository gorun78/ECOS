package com.chinacreator.gzcm.engine.ai.pipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逻辑编排「按已存定义执行」的解析/排序单测（2026-11-23 场景智能逻辑承接批）。
 *
 * <p>锁三面：① definition_json.steps.graph 的节点/边解析（含 data 内嵌形态）；
 * ② 拓扑排序确定性 + 环检测如实报错；③ 缺图时不产出节点（由服务层抛 400）。</p>
 */
class AipPipelineExecutionServiceTest {

    private static String definition(String nodesJson, String edgesJson) {
        return "{\"steps\":{\"blocks\":[],\"graph\":{\"nodes\":" + nodesJson
                + ",\"edges\":" + edgesJson + "}}}";
    }

    @Test
    @DisplayName("解析画布快照：type/label/config 落在 data 内时归一成顶层字段")
    void parsesNestedDataShape() {
        String def = definition(
                "[{\"id\":\"n1\",\"type\":\"trigger\",\"position\":{\"x\":0,\"y\":0},"
                        + "\"data\":{\"type\":\"trigger\",\"label\":\"触发\",\"config\":{\"cronExpr\":\"0 0 * * *\"}}}]",
                "[]");
        List<Map<String, Object>> nodes = AipPipelineExecutionService.nodeGraphOf(def);
        assertEquals(1, nodes.size());
        Map<String, Object> n = nodes.get(0);
        assertEquals("n1", n.get("id"));
        assertEquals("trigger", n.get("type"));
        assertEquals("触发", n.get("label"));
        assertTrue(((Map<?, ?>) n.get("config")).containsKey("cronExpr"));
    }

    @Test
    @DisplayName("脏 JSON/缺 graph → 空节点表（服务层据此 400，禁空跑）")
    void dirtyDefinitionYieldsNoNodes() {
        assertTrue(AipPipelineExecutionService.nodeGraphOf("not-json").isEmpty());
        assertTrue(AipPipelineExecutionService.nodeGraphOf("{\"steps\":[]}").isEmpty());
        assertTrue(AipPipelineExecutionService.nodeGraphOf(null).isEmpty());
    }

    @Test
    @DisplayName("拓扑排序：边决定顺序，且保持同层原始顺序")
    void topoOrderRespectsEdges() {
        List<Map<String, Object>> nodes = List.of(node("a"), node("b"), node("c"));
        List<String[]> edges = java.util.Arrays.asList(new String[]{"a", "c"}, new String[]{"c", "b"});
        assertEquals(List.of("a", "c", "b"), AipPipelineExecutionService.topoOrder(nodes, edges));
        // 无边 → 原始顺序
        assertEquals(List.of("a", "b", "c"), AipPipelineExecutionService.topoOrder(nodes, List.of()));
    }

    @Test
    @DisplayName("环 / 悬空边 / 重复 id → 如实抛错（不猜测性排序）")
    void topoOrderRejectsInvalidGraphs() {
        List<Map<String, Object>> nodes = List.of(node("a"), node("b"));
        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
                () -> AipPipelineExecutionService.topoOrder(nodes,
                        java.util.Arrays.asList(new String[]{"a", "b"}, new String[]{"b", "a"})));
        assertTrue(cycle.getMessage().contains("AIP-EXEC-02"));

        IllegalArgumentException dangling = assertThrows(IllegalArgumentException.class,
                () -> AipPipelineExecutionService.topoOrder(nodes,
                        java.util.Collections.singletonList(new String[]{"a", "ghost"})));
        assertTrue(dangling.getMessage().contains("AIP-EXEC-02"));

        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> AipPipelineExecutionService.topoOrder(List.of(node("a"), node("a")), List.of()));
        assertTrue(dup.getMessage().contains("AIP-EXEC-02"));
    }

    private static Map<String, Object> node(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", "trigger");
        return m;
    }
}
