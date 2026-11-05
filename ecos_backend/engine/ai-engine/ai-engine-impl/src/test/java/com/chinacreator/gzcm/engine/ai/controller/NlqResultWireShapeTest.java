package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ai.dto.NlqResultVO;
import com.chinacreator.gzcm.engine.ai.dto.ObjectQlText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * NLQ 回执与 ObjectQL 文本化保真（DI-2 #147EA）。
 *
 * <p>回执两腿：正常腿 2 键（translated/results）、Agent 回退腿补 agent_answer/message；
 * 结果列名统一小写。ObjectQL 规范化（复合 filter→filters、复合查询取首子查询）与
 * JSON 文本化（转义 / 数字 / 布尔 / 列表原样）保持旧实现形态。</p>
 */
class NlqResultWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("正常腿：translated/results 两键；列名转小写")
    void normalKeepsTwoKeysAndLowercasesColumns() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", "c1");
        row.put("NAME", "某某公司");

        JsonNode data = MAPPER.valueToTree(
                NlqResultVO.of("{\"entity\":\"Customer\"}", List.of(row)));

        assertEquals(List.of("translated", "results"), fieldNames(data));
        assertEquals("c1", data.get("results").get(0).get("id").asText());
        assertEquals("某某公司", data.get("results").get(0).get("name").asText());
        assertFalse(data.get("results").get(0).has("ID"));
    }

    @Test
    @DisplayName("Agent 回退腿：四键（agent_answer/message 条件键）")
    void fallbackKeepsConditionalKeys() throws Exception {
        JsonNode data = MAPPER.valueToTree(new NlqResultVO(
                null, List.of(), "解析答案", "词典无匹配，已通过 Agent Runtime 解析。"));

        assertEquals(List.of("translated", "results", "agent_answer", "message"), fieldNames(data));
        assertEquals("解析答案", data.get("agent_answer").asText());

        JsonNode failed = MAPPER.valueToTree(new NlqResultVO(
                null, List.of(), null, "词典无匹配，Agent Runtime 解析失败"));
        assertEquals(List.of("translated", "results", "message"), fieldNames(failed));
    }

    @Test
    @DisplayName("ObjectQL：复合 filter→filters；复合查询取首子查询；JSON 文本化逐字")
    void objectQlNormalizeAndJsonKept() {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("operator", "AND");
        filter.put("conditions", List.of(Map.of("field", "region", "op", "=", "value", "华东")));
        Map<String, Object> ql = new LinkedHashMap<>();
        ql.put("entity", "Customer");
        ql.put("filter", filter);

        Map<String, Object> normalized = ObjectQlText.normalize(ql);
        assertEquals(List.of("entity", "filters"), new ArrayList<>(normalized.keySet()));
        assertFalse(normalized.containsKey("filter"));

        Map<String, Object> composite = new LinkedHashMap<>();
        composite.put("composite", true);
        composite.put("queries", List.of(normalized));
        assertEquals("Customer", ObjectQlText.normalize(composite).get("entity"));

        Map<String, Object> literal = new LinkedHashMap<>();
        literal.put("name", "a\"b\n");
        literal.put("n", 3);
        literal.put("ok", true);
        assertEquals("{\"name\": \"a\\\"b\\n\", \"n\": 3, \"ok\": true}",
                ObjectQlText.toJson(literal));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
