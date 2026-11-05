package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 企业知识图谱 wire 保真（DI-2 #147ER）。
 *
 * <p>该控制器为 gateway 聚合期 dead-twin（{@code @Deprecated}，已从组件扫描排除），
 * 本次只做载体强类型化，不改 handler 集合与键面：图谱 3 键（nodes/edges/stats）、
 * 节点 5 键（含 {@code type=OntologyEntity}）、边 4 键、统计 3 键、同步回执 4 键。</p>
 */
class EcosKnowledgeGraphWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("图谱/节点/边/统计键面与计数不变")
    void graphKeepsKeys() throws Exception {
        EcosKnowledgeGraphController controller = new EcosKnowledgeGraphController();
        JsonNode graph = MAPPER.valueToTree(controller.getGraph().getData());

        assertEquals(List.of("nodes", "edges", "stats"), fieldNames(graph));
        assertEquals(11, graph.get("nodes").size());
        assertEquals(11, graph.get("edges").size());
        assertEquals(List.of("code", "name", "domainId", "domainName", "type"),
                fieldNames(graph.get("nodes").get(0)));
        assertEquals("OntologyEntity", graph.get("nodes").get(0).get("type").asText());
        assertEquals(List.of("source", "target", "relationshipType", "label"),
                fieldNames(graph.get("edges").get(0)));
        assertEquals(List.of("nodeCount", "edgeCount", "domains"),
                fieldNames(graph.get("stats")));
        assertEquals(11, graph.get("stats").get("nodeCount").asInt());
        assertEquals(4, graph.get("stats").get("domains").size());
    }

    @Test
    @DisplayName("同步回执 4 键")
    void syncKeepsKeys() throws Exception {
        JsonNode sync = MAPPER.valueToTree(new EcosKnowledgeGraphController().syncToNeo4j().getData());
        assertEquals(List.of("status", "message", "nodesAvailable", "edgesAvailable"),
                fieldNames(sync));
        assertEquals("ready_to_sync", sync.get("status").asText());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
