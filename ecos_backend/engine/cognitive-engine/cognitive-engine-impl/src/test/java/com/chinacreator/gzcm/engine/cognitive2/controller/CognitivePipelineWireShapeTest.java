package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineCreateDTO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineCreateVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineDetailVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineExecutionStatusVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineExecutionVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.CognitivePipelineSummaryVO;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;
import com.chinacreator.gzcm.engine.cognitive2.model.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认知管线端点 wire 保真（DI-2 #147EJ）。
 *
 * <p>创建回执 3 键 / 列表项 5 键 / 详情 7 键 / 执行回执 4 键 /
 * 执行状态 7 键（endTime 运行中为 null 但保留键）；创建请求体强类型解析。</p>
 */
class CognitivePipelineWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("创建回执 3 键 + 列表项 5 键 + 详情 7 键")
    void createListDetailKeepKeys() throws Exception {
        JsonNode create = MAPPER.valueToTree(new CognitivePipelineCreateVO("p-1", "demo", 2));
        assertEquals(List.of("pipelineId", "name", "nodeCount"), fieldNames(create));
        assertEquals(2, create.get("nodeCount").asInt());

        JsonNode summary = MAPPER.valueToTree(new CognitivePipelineSummaryVO(
                "p-1", "demo", "desc", 2, new Timestamp(0L)));
        assertEquals(List.of("id", "name", "description", "nodeCount", "createdAt"),
                fieldNames(summary));

        List<CognitivePipelineNode> nodes = List.of(
                new CognitivePipelineNode("n1", NodeType.REASON, "{}", List.of()));
        JsonNode detail = MAPPER.valueToTree(new CognitivePipelineDetailVO(
                "p-1", "demo", "desc", "DRAFT", "system", nodes, new Timestamp(0L)));
        assertEquals(List.of("id", "name", "description", "status", "createdBy", "nodes",
                "createdAt"), fieldNames(detail));
        assertEquals("n1", detail.get("nodes").get(0).get("nodeId").asText());
    }

    @Test
    @DisplayName("执行回执 4 键；执行状态 7 键（endTime=null 仍保留键）")
    void executionKeepsKeys() throws Exception {
        JsonNode exec = MAPPER.valueToTree(new CognitivePipelineExecutionVO(
                "e-1", "p-1", "RUNNING", new Timestamp(0L)));
        assertEquals(List.of("execId", "pipelineId", "status", "startTime"), fieldNames(exec));

        Map<String, String> nodeStatuses = new LinkedHashMap<>();
        nodeStatuses.put("n1", "SUCCEEDED");
        JsonNode status = MAPPER.valueToTree(new CognitivePipelineExecutionStatusVO(
                "e-1", "p-1", "RUNNING", nodeStatuses, new Timestamp(0L), null,
                List.of("[t] start")));
        assertEquals(List.of("execId", "pipelineId", "status", "nodeStatuses", "startTime",
                "endTime", "logs"), fieldNames(status));
        assertTrue(status.get("endTime").isNull());
        assertEquals("SUCCEEDED", status.get("nodeStatuses").get("n1").asText());
    }

    @Test
    @DisplayName("创建请求体：name/description/nodes[] 强类型解析（config 任意 JSON、dependsOn 列表）")
    void createRequestParses() throws Exception {
        String json = """
                {
                  "name": "demo",
                  "description": "d",
                  "nodes": [
                    {"nodeId": "n1", "nodeType": "PLAN", "config": {"k": 1}, "dependsOn": []},
                    {"nodeId": "n2", "nodeType": "EXECUTE"}
                  ]
                }
                """;
        CognitivePipelineCreateDTO dto = MAPPER.readValue(json, CognitivePipelineCreateDTO.class);
        assertEquals("demo", dto.getName());
        assertEquals("d", dto.getDescription());
        assertEquals(2, dto.getNodes().size());
        assertEquals("n1", dto.getNodes().get(0).getNodeId());
        assertEquals("{k=1}", dto.getNodes().get(0).getConfig().toString());
        assertEquals(List.of(), dto.getNodes().get(0).getDependsOn());
        assertEquals(null, dto.getNodes().get(1).getConfig());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
