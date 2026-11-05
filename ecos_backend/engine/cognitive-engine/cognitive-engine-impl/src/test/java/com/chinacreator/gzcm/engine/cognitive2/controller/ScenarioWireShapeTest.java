package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioCompareItemVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioCompareRequest;
import com.chinacreator.gzcm.engine.cognitive2.dto.ScenarioTemplateVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 情景推演端点 wire 保真（DI-2 #147EK）。
 *
 * <p>模板条目 5 键（demo 恒 true）；对比条目 4 键（predictions 原样透传）；
 * 对比请求体 scenarios[] 强类型解析（variables 缺省为 null，由控制器补空映射）。</p>
 */
class ScenarioWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("模板条目 5 键（demo 恒 true）；对比条目 4 键")
    void templateAndCompareKeepKeys() throws Exception {
        JsonNode template = MAPPER.valueToTree(new ScenarioTemplateVO(
                "原材料涨价10%", "供应链", Map.of("raw_material_cost", "+10%"), "desc", true));
        assertEquals(List.of("name", "domain", "variables", "description", "demo"),
                fieldNames(template));
        assertTrue(template.get("demo").asBoolean());
        assertEquals("+10%", template.get("variables").get("raw_material_cost").asText());

        JsonNode item = MAPPER.valueToTree(new ScenarioCompareItemVO(
                "s1", 0.82, "summary", Map.of("profit", 100)));
        assertEquals(List.of("name", "confidence", "summary", "predictions"), fieldNames(item));
        assertEquals(0.82, item.get("confidence").asDouble());
        assertEquals(100, item.get("predictions").get("profit").asInt());
    }

    @Test
    @DisplayName("对比请求体：scenarios[] 解析（variables 缺省 null、非字符串值原样）")
    void compareRequestParses() throws Exception {
        String json = """
                {"scenarios": [
                  {"name": "a", "variables": {"rate": 0.05, "flag": true}},
                  {"name": "b"}
                ]}
                """;
        ScenarioCompareRequest dto = MAPPER.readValue(json, ScenarioCompareRequest.class);
        assertEquals(2, dto.getScenarios().size());
        assertEquals("a", dto.getScenarios().get(0).getName());
        assertEquals(0.05, dto.getScenarios().get(0).getVariables().get("rate"));
        assertNull(dto.getScenarios().get(1).getVariables());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
