package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.engine.data.service.DataLineageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 血缘兼容面 wire 键面门（DI-2 #147DI）。
 *
 * <p>影响 7 键 + 节点 6 键（风险分按 hop 衰减）、解析 5 键 + 血缘视图 2 键；
 * 节点/边集合仍承载解析器原样 JSON（动态 schema 不冻结）。</p>
 */
class LineageCompatWireShapeTest {

    private DataLineageService lineageService;
    private LineageCompatController controller;

    @BeforeEach
    void setUp() {
        lineageService = mock(DataLineageService.class);
        controller = new LineageCompatController(lineageService);
    }

    private static Map<String, Object> lineage(Map<String, Object> node, Map<String, Object> edge) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodes", List.of(node));
        m.put("edges", List.of(edge));
        return m;
    }

    private static Map<String, Object> node(String id) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("label", "表 " + id);
        n.put("type", "TABLE");
        return n;
    }

    private static Map<String, Object> edge(String src, String tgt) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", src);
        e.put("target", tgt);
        return e;
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("影响分析：7 键，节点 6 键，一跳风险 80、二跳 64，顶层风险取最大")
    void impactShape() {
        when(lineageService.getLineage(isNull(), eq("t1")))
                .thenReturn(Map.of("nodes", List.of(node("t1"), node("t2")),
                        "edges", List.of(edge("t1", "t2"))));
        when(lineageService.getLineage(isNull(), eq("t2")))
                .thenReturn(Map.of("nodes", List.of(node("t2")), "edges", List.of()));

        // 纯表名 startNode：tableName=t1，datasourceId=null
        JsonNode data = json(controller.lineageImpact("t1").getData());

        assertEquals(List.of("success", "startNode", "impactedNodes", "totalRisk", "severity",
                "nodes", "edges"), keys(data));
        JsonNode impacted = data.path("impactedNodes").get(0);
        assertEquals(List.of("id", "label", "type", "hopCount", "path", "riskScore"),
                keys(impacted));
        assertEquals("t2", impacted.path("id").asText());
        assertEquals(1, impacted.path("hopCount").asInt());
        assertEquals(80, impacted.path("riskScore").asInt());
        assertEquals(80, data.path("totalRisk").asInt());
    }

    @Test
    @DisplayName("解析：5 键 + 血缘视图 2 键（nodes/links），行数如实汇总")
    void parseShape() {
        when(lineageService.getLineage(isNull(), eq("t1")))
                .thenReturn(Map.of("nodes", List.of(node("t1"), node("t2")),
                        "edges", List.of(edge("t1", "t2"))));

        JsonNode data = json(controller.parseLineage(Map.of("tableName", "t1")).getData());

        assertEquals(List.of("success", "message", "addedNodes", "addedLinks", "lineage"),
                keys(data));
        assertEquals(2, data.path("addedNodes").asInt());
        assertEquals(1, data.path("addedLinks").asInt());
        assertEquals(List.of("nodes", "links"), keys(data.path("lineage")));
    }

    @Test
    @DisplayName("解析：payload 无表名 ⇒ 空结果（success=true 非错误）")
    void parseEmptyShape() {
        JsonNode data = json(controller.parseLineage(Map.of("foo", "bar")).getData());

        assertEquals(5, keys(data).size());
        assertTrue(data.path("success").asBoolean());
        assertEquals(0, data.path("addedNodes").asInt());
        assertEquals(0, data.path("lineage").path("nodes").size());
    }
}
