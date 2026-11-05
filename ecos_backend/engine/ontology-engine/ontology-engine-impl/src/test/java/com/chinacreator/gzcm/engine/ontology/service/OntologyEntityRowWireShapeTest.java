package com.chinacreator.gzcm.engine.ontology.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code OntologyService.OntologyEntityRow} wire 键面门（DI-2 #147DD）。
 *
 * <p>{@code listAllObjects()} 由 {@code List<Map>} 改强类型行后，四个消费面
 * （{@code /ecos/entities}、{@code /ecos/lineages/entities}、{@code /ontology/data/objects}、
 * 导出 blob）共用同一行形态；本门按**键集相等 + 键序**钉住 11 键。</p>
 */
class OntologyEntityRowWireShapeTest {

    private static final List<String> ROW_KEYS = List.of(
            "id", "ontologyId", "code", "name", "description", "entityType", "domainId",
            "mapping", "sortOrder", "createdAt", "updatedAt");

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("实体行：11 键且键序与 entityToMap 一致，mapping 为文档对象")
    void rowShape() {
        OntologyService.OntologyEntityRow row = new OntologyService.OntologyEntityRow(
                "e1", "o1", "E1", "实体", "描述", "MASTER", "d1",
                Map.of("table", "t1"), 1, "2026-11-05T09:00", "2026-11-05T09:10");

        JsonNode data = new ObjectMapper().valueToTree(row);

        assertEquals(ROW_KEYS, keys(data));
        assertEquals("MASTER", data.path("entityType").asText());
        assertEquals("t1", data.path("mapping").path("table").asText());
    }
}
