package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyExportTaskVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 本体导出任务 wire 保真（DI-2 #147EL）。
 *
 * <p>存储层由内部 Map 改为 {@link OntologyExportTaskVO} 直存后，序列化键面/键序/条件键
 * 必须与旧「Map → convertValue → VO」链路逐字等价（成功腿含 payload/objectCount，
 * 失败腿含 error、payload 省略）。</p>
 */
class OntologyExportWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("成功腿：8 键（payload 动态结构原样、error 省略），与旧 Map 链路逐字等价")
    void completedRecordMatchesLegacyMapChain() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entities", List.of(Map.of("id", "e1", "code", "Customer")));
        payload.put("relationships", List.of());

        OntologyExportTaskVO vo = new OntologyExportTaskVO();
        vo.setId("exp_1234abcd");
        vo.setOntologyId("ont-1");
        vo.setFormat("JSON");
        vo.setScope("FULL");
        vo.setStatus("COMPLETED");
        vo.setCreatedAt("2026-11-05T10:00:00Z");
        vo.setObjectCount(1);
        vo.setPayload(payload);

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("id", "exp_1234abcd");
        legacy.put("ontologyId", "ont-1");
        legacy.put("format", "JSON");
        legacy.put("scope", "FULL");
        legacy.put("status", "COMPLETED");
        legacy.put("createdAt", "2026-11-05T10:00:00Z");
        legacy.put("payload", payload);
        legacy.put("objectCount", 1);

        JsonNode direct = MAPPER.valueToTree(vo);
        JsonNode converted = MAPPER.valueToTree(
                MAPPER.convertValue(legacy, OntologyExportTaskVO.class));

        assertEquals(List.of("id", "ontologyId", "format", "scope", "status", "createdAt",
                "objectCount", "payload"), fieldNames(direct));
        assertEquals(direct, converted);
        assertEquals("Customer", direct.get("payload").get("entities").get(0).get("code").asText());
    }

    @Test
    @DisplayName("失败腿：error 出现、payload 省略、objectCount=0 保留")
    void failedRecordKeepsErrorAndOmitsPayload() throws Exception {
        OntologyExportTaskVO vo = new OntologyExportTaskVO();
        vo.setId("exp_bad");
        vo.setOntologyId("ont-1");
        vo.setFormat("DDL");
        vo.setScope("ENTITIES");
        vo.setStatus("FAILED");
        vo.setCreatedAt("2026-11-05T10:00:00Z");
        vo.setObjectCount(0);
        vo.setError("boom");

        JsonNode node = MAPPER.valueToTree(vo);
        assertEquals(List.of("id", "ontologyId", "format", "scope", "status", "createdAt",
                "objectCount", "error"), fieldNames(node));
        assertEquals("boom", node.get("error").asText());
        assertEquals(0, node.get("objectCount").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
