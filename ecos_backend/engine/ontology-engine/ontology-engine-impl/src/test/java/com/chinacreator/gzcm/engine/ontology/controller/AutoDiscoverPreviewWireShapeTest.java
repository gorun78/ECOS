package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ontology.dto.AutoDiscoverEntityPreviewVO;
import com.chinacreator.gzcm.engine.ontology.dto.AutoDiscoverPreviewVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 自动发现预览 wire 保真（DI-2 #147EG）。
 *
 * <p>条目两腿同表异键：可发现腿 8 键（resourceName/entityCode/entityName/domainCode/
 * discovered/fieldCount/typeDistribution/alreadyExists）、跳过腿 4 键（.../discovered/reason）；
 * 回执 7 键。</p>
 */
class AutoDiscoverPreviewWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("可发现腿：8 键（reason 不出现）；跳过腿：4 键（明细键不出现）")
    void entityPreviewKeepsDualShape() throws Exception {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("STRING", 2);

        JsonNode discovered = MAPPER.valueToTree(
                AutoDiscoverEntityPreviewVO.discovered("orders", "orders", "dom", 2, dist, true));
        assertEquals(List.of("resourceName", "entityCode", "entityName", "domainCode", "discovered",
                "fieldCount", "typeDistribution", "alreadyExists"), fieldNames(discovered));
        assertFalse(discovered.has("reason"));
        assertEquals(2, discovered.get("typeDistribution").get("STRING").asInt());

        JsonNode skipped = MAPPER.valueToTree(
                AutoDiscoverEntityPreviewVO.skipped("empty_tbl", "empty_tbl"));
        assertEquals(List.of("resourceName", "entityCode", "discovered", "reason"),
                fieldNames(skipped));
        assertFalse(skipped.has("fieldCount"));
        assertEquals("无字段数据", skipped.get("reason").asText());
    }

    @Test
    @DisplayName("回执：7 键；entities 内混合两腿；dryRun 恒 true")
    void previewEnvelopeKeepsKeys() throws Exception {
        JsonNode data = MAPPER.valueToTree(new AutoDiscoverPreviewVO(
                "dom", "ds-1", true,
                List.of(AutoDiscoverEntityPreviewVO.discovered("orders", "orders", "dom", 1,
                                Map.of("STRING", 1), false),
                        AutoDiscoverEntityPreviewVO.skipped("empty_tbl", "empty_tbl")),
                2, 1, "预览模式（dryRun=true），未写入数据库"));

        assertEquals(List.of("domainCode", "datasourceId", "dryRun", "entities", "totalResources",
                "totalFields", "note"), fieldNames(data));
        assertEquals(2, data.get("entities").size());
        assertEquals(1, data.get("totalFields").asInt());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
