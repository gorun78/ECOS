package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.LineageParseResponse;
import com.chinacreator.gzcm.engine.ontology.dto.SqlLineageRequest;
import com.chinacreator.gzcm.engine.ontology.lineage.OntologyLineageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 血缘端点 wire 保真（DI-2 #147DV）。
 *
 * <p>手工建边腿记录 8 键（含 transform/description、无 eventId）；解析腿记录 7 键
 * （含 eventId、无 transform/description）；图 nodes[{id}] / edges[{id,source,target,lineageType}]；
 * 追溯 3 键；影响分析 6 键（impactedObjects 动态行原样透传，impactedNodes 补 path 缺省）。</p>
 */
class LineageWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LineageController controller;

    /** 只覆盖解析与影响分析，安全引擎依赖不参与。 */
    private static final class FakeLineageService extends OntologyLineageService {
        private FakeLineageService() {
            super(null);
        }

        @Override
        public LineageParseResponse parse(SqlLineageRequest req) {
            LineageParseResponse resp = new LineageParseResponse();
            resp.setFormat("sql");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "t_a");
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", "t_a");
            edge.put("target", "t_b");
            edge.put("type", "TRANSFORMED");
            resp.setNodes(List.of(node));
            resp.setEdges(List.of(edge));
            return resp;
        }

        @Override
        public List<Map<String, Object>> impactAnalysis(String rootId,
                                                        List<Map<String, Object>> edges,
                                                        Integer depth) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("id", "B");
            hit.put("hopCount", 1);
            hit.put("riskScore", 100);
            return List.of(hit);
        }
    }

    @BeforeEach
    void setUp() {
        controller = new LineageController(null, null, new FakeLineageService());
    }

    @Test
    @DisplayName("手工建边：8 键（无 eventId）；部分更新只覆盖出现的键")
    void createAndUpdateKeepConditionalKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "A");
        body.put("target", "B");
        body.put("transform", "sum(x)");
        JsonNode created = dataOf(controller.createLineage(body));

        assertEquals(List.of("id", "source", "target", "lineageType", "transform", "description",
                "createdAt", "updatedAt"), fieldNames(created));
        String id = created.get("id").asText();

        JsonNode updated = dataOf(controller.updateLineage(id, Map.of("lineageType", "COPIED")));
        assertEquals("COPIED", updated.get("lineageType").asText());
        assertEquals("sum(x)", updated.get("transform").asText(), "未出现的键保持原值");
        assertEquals(List.of("id", "source", "target", "lineageType", "transform", "description",
                "createdAt", "updatedAt"), fieldNames(updated));
    }

    @Test
    @DisplayName("解析建边：7 键含 eventId、无 transform/description；图与追溯键面固定")
    void parseRecordsKeepEventShapeAndGraph() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "A");
        body.put("target", "B");
        controller.createLineage(body);
        dataOf(controller.parseLineage(new SqlLineageRequest()));

        JsonNode list = MAPPER.valueToTree(controller.listLineages(null, null, null)).get("data");
        JsonNode parsed = null;
        for (JsonNode row : list) {
            if (row.get("id").asText().startsWith("lin_parse_")) {
                parsed = row;
            }
        }
        assertTrue(parsed != null, "解析腿入内存 store");
        assertEquals(List.of("id", "source", "target", "lineageType", "eventId", "createdAt",
                "updatedAt"), fieldNames(parsed));

        JsonNode graph = dataOf(controller.getLineageGraph());
        assertEquals(List.of("nodes", "edges"), fieldNames(graph));
        assertEquals(List.of("id"), fieldNames(graph.get("nodes").get(0)));
        assertEquals(List.of("id", "source", "target", "lineageType"),
                fieldNames(graph.get("edges").get(0)));

        JsonNode trace = dataOf(controller.traceLineage("A"));
        assertEquals(List.of("nodeId", "upstream", "downstream"), fieldNames(trace));
        assertTrue(trace.get("downstream").toString().contains("B"));
    }

    @Test
    @DisplayName("影响分析：6 键；totalRisk 求和、严重级 HIGH、path 缺省补齐")
    void impactKeepsShape() throws Exception {
        JsonNode data = dataOf(controller.impactAnalysis("A", null, null, 3));

        assertEquals(List.of("rootObject", "impactedObjects", "impactedNodes", "totalRisk",
                "severity", "depth"), fieldNames(data));
        assertEquals("A", data.get("rootObject").asText());
        assertEquals(100, data.get("totalRisk").asInt());
        assertEquals("HIGH", data.get("severity").asText());
        assertEquals("[\"A\",\"B\"]", data.get("impactedNodes").get(0).get("path").toString());
        assertEquals(3, data.get("depth").asInt());
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
