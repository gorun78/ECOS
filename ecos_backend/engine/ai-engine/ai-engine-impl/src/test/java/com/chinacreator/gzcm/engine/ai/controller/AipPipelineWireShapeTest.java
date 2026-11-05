package com.chinacreator.gzcm.engine.ai.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIP Pipeline 端点 wire 保真（DI-2 #147EC）。
 *
 * <p>Pipeline 行 7 键（执行后补 {@code lastExecutedAt} 尾键）；列表摘要 6 键（无 steps）；
 * 执行回执 4 键；删除 2 键。</p>
 */
class AipPipelineWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> PIPELINE_KEYS = List.of("id", "name", "description", "steps",
            "status", "createdAt", "updatedAt");

    private AIPPipelineController controller;

    @BeforeEach
    void setUp() {
        controller = new AIPPipelineController();
    }

    @Test
    @DisplayName("创建/更新：7 键；steps 自由结构透传；部分更新保值")
    void createAndUpdateKeepKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "清洗管道");
        body.put("steps", List.of(Map.of("type", "cleansing")));

        JsonNode created = dataOf(controller.createPipeline(body));
        assertEquals(PIPELINE_KEYS, fieldNames(created));
        assertEquals("draft", created.get("status").asText());
        assertEquals("cleansing", created.get("steps").get(0).get("type").asText());

        String id = created.get("id").asText();
        JsonNode updated = dataOf(controller.updatePipeline(id, Map.of("status", "ready")));
        assertEquals("ready", updated.get("status").asText());
        assertEquals("清洗管道", updated.get("name").asText(), "未出现的键保持原值");
        assertEquals(PIPELINE_KEYS, fieldNames(updated));
    }

    @Test
    @DisplayName("执行：回执 4 键；执行后行补 lastExecutedAt 尾键且状态 completed")
    void executeKeepsReceiptAndTailKey() throws Exception {
        JsonNode created = dataOf(controller.createPipeline(Map.of("name", "管道")));
        String id = created.get("id").asText();

        JsonNode result = dataOf(controller.executePipeline(id));
        assertEquals(List.of("pipelineId", "status", "message", "executedAt"), fieldNames(result));
        assertEquals("executed", result.get("status").asText());

        JsonNode after = dataOf(controller.getPipeline(id));
        assertEquals(PIPELINE_KEYS.size() + 1, fieldNames(after).size());
        assertEquals("lastExecutedAt", fieldNames(after).get(PIPELINE_KEYS.size()));
        assertEquals("completed", after.get("status").asText());
        assertTrue(after.get("lastExecutedAt").asText().contains("T"));

        JsonNode list = MAPPER.valueToTree(controller.listPipelines()).get("data");
        assertEquals(List.of("id", "name", "description", "status", "createdAt", "updatedAt"),
                fieldNames(list.get(0)));
        assertFalse(list.get(0).has("steps"), "列表摘要不含 steps");
    }

    @Test
    @DisplayName("删除：2 键回执；二次删除 404")
    void deleteKeepsKeys() throws Exception {
        JsonNode created = dataOf(controller.createPipeline(Map.of("name", "管道")));
        String id = created.get("id").asText();

        JsonNode deleted = dataOf(controller.deletePipeline(id));
        assertEquals(List.of("deleted", "id"), fieldNames(deleted));
        assertEquals(404, controller.deletePipeline(id).getCode());
    }

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
