package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.sysman.service.CausalQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 因果图 wire 键面门（DI-2 #147DE）。
 *
 * <p>图 4 键 / 节点 4 键 / 边 5 键 / 路径 5 键 / 对比 3 键（场景 4、节点 5 含 impactFactor、
 * 边 6 含 projectedWeight、差异 4）。内存兜底腿（DB ping 抛错）不用连库。</p>
 */
class CausalWireShapeTest {

    private CausalController controller;

    @BeforeEach
    void setUp() {
        CausalQueryService service = mock(CausalQueryService.class);
        when(service.ping()).thenThrow(new RuntimeException("db down"));
        controller = new CausalController(service);
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
    @DisplayName("图：4 键（nodes/edges/nodeCount/edgeCount），节点 4 键、边 5 键")
    void graphShape() {
        JsonNode data = json(controller.getGraph().getData());

        assertEquals(List.of("nodes", "edges", "nodeCount", "edgeCount"), keys(data));
        assertEquals(8, data.path("nodeCount").asInt());
        assertEquals(10, data.path("edgeCount").asInt());
        assertEquals(List.of("id", "name", "type", "group"), keys(data.path("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "weight", "type"),
                keys(data.path("edges").get(0)));
    }

    @Test
    @DisplayName("路径：2 键（paths/total），路径 5 键且长度/权重为数值")
    void pathsShape() {
        JsonNode data = json(controller.getPaths("n1", "n3", 5).getData());

        assertEquals(List.of("paths", "total"), keys(data));
        assertTrue(data.path("total").asInt() >= 1, "n1→n2→n3 至少一条路径");
        assertEquals(List.of("nodes", "length", "edges", "totalWeight", "avgWeight"),
                keys(data.path("paths").get(0)));
        assertEquals(2, data.path("paths").get(0).path("length").asInt());
        assertEquals(0.85 + 0.72, data.path("paths").get(0).path("totalWeight").asDouble(), 1e-9);
    }

    @Test
    @DisplayName("对比：3 键；场景 4 键、投影节点 5 键、投影边 6 键、差异 4 键")
    void compareShape() {
        JsonNode data = json(controller.compare(Map.of("scenarioIds", List.of("s1", "s2"))).getData());

        assertEquals(List.of("scenarios", "differences", "comparedCount"), keys(data));
        assertEquals(2, data.path("comparedCount").asInt());

        JsonNode scenario = data.path("scenarios").get(0);
        assertEquals(List.of("id", "name", "nodes", "edges"), keys(scenario));
        assertEquals(List.of("id", "name", "type", "group", "impactFactor"),
                keys(scenario.path("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "weight", "type", "projectedWeight"),
                keys(scenario.path("edges").get(0)));

        assertEquals(List.of("scenarioA", "scenarioB", "nodeCountDiff", "edgeCountDiff"),
                keys(data.path("differences").get(0)));
    }

    @Test
    @DisplayName("对比：场景不足两个 ⇒ 400，不合成空对比")
    void compareRequiresTwoScenarios() {
        assertEquals(400, controller.compare(Map.of("scenarioIds", List.of("s1"))).getCode());
    }
}
