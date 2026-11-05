package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.cognitive2.dto.CognitiveDecisionVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionCheckVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionImpactVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionLinkResultVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.DecisionRecordResultVO;
import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策端点 wire 保真（DI-2 #147EB）。
 *
 * <p>决策行 8 键（createdAt 保持 JDBC Timestamp）；record 1 键 / link 3 键；
 * 影响图 {@code rootId/nodes/edges}（节点 3 键、边 3 键）；合规检查 4 键。</p>
 */
class DecisionWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("决策行：8 键（含 Timestamp createdAt）；record/link 回执键面")
    void decisionRowsAndReceiptsKeepKeys() throws Exception {
        Decision d = new Decision();
        d.setId("d1");
        d.setCategory("risk");
        d.setScenario("s");
        d.setReasoning("r");
        d.setOutcome("o");
        d.setConfidence(0.8);
        d.setDecisionMaker("u1");
        d.setCreatedAt(Timestamp.valueOf("2026-11-05 10:00:00"));

        JsonNode row = MAPPER.valueToTree(CognitiveDecisionVO.of(d));
        assertEquals(List.of("id", "category", "scenario", "reasoning", "outcome", "confidence",
                "decisionMaker", "createdAt"), fieldNames(row));
        assertEquals(0.8, row.get("confidence").asDouble(), 1e-9);

        JsonNode recorded = MAPPER.valueToTree(new DecisionRecordResultVO("d1"));
        assertEquals(List.of("decisionId"), fieldNames(recorded));

        JsonNode linked = MAPPER.valueToTree(new DecisionLinkResultVO(true, "d1", "d2"));
        assertEquals(List.of("linked", "sourceId", "targetId"), fieldNames(linked));
    }

    @Test
    @DisplayName("影响图：rootId/nodes/edges 三键；节点与边各 3 键")
    void impactKeepsNestedShape() throws Exception {
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", "d1");
        n.put("category", "risk");
        n.put("outcome", "o");
        nodes.add(n);
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source", "d1");
        e.put("target", "d2");
        e.put("relationship", "causes");
        edges.add(e);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("rootId", "d1");
        source.put("nodes", nodes);
        source.put("edges", edges);

        JsonNode data = MAPPER.valueToTree(DecisionImpactVO.of(source));

        assertEquals(List.of("rootId", "nodes", "edges"), fieldNames(data));
        assertEquals(List.of("id", "category", "outcome"), fieldNames(data.get("nodes").get(0)));
        assertEquals(List.of("source", "target", "relationship"),
                fieldNames(data.get("edges").get(0)));
        assertFalse(data.has("decisionId"));
    }

    @Test
    @DisplayName("合规检查：4 键；violations 字符串数组保序")
    void checkKeepsKeys() throws Exception {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("decisionId", "d1");
        source.put("compliant", false);
        source.put("violations", List.of("v1", "v2"));
        source.put("policiesChecked", 3);

        JsonNode data = MAPPER.valueToTree(DecisionCheckVO.of(source));

        assertEquals(List.of("decisionId", "compliant", "violations", "policiesChecked"),
                fieldNames(data));
        assertTrue(!data.get("compliant").asBoolean());
        assertEquals("v2", data.get("violations").get(1).asText());
        assertEquals(3, data.get("policiesChecked").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
