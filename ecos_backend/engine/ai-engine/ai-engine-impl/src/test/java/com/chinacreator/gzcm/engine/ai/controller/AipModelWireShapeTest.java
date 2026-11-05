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
 * AIP 模型目录端点 wire 保真（DI-2 #147DZ）。
 *
 * <p>内置模型 8 键（不带 {@code endpoint/apiKeyRef}）；手工注册 10 键（两键恒在，缺省空串）；
 * 部分更新只覆盖出现的键；健康检查 4 键、删除回执 2 键。</p>
 */
class AipModelWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> DEFAULT_KEYS = List.of("id", "name", "provider", "modelType",
            "description", "status", "createdAt", "updatedAt");
    private static final List<String> CREATED_KEYS = List.of("id", "name", "provider", "modelType",
            "description", "endpoint", "apiKeyRef", "status", "createdAt", "updatedAt");

    private AIPModelController controller;

    @BeforeEach
    void setUp() {
        controller = new AIPModelController();
    }

    @Test
    @DisplayName("内置模型：8 键（endpoint/apiKeyRef 不出键）")
    void defaultsOmitOptionalKeys() throws Exception {
        JsonNode list = MAPPER.valueToTree(controller.listModels()).get("data");
        assertEquals(5, list.size());
        assertEquals(DEFAULT_KEYS, fieldNames(list.get(0)));
        assertFalse(list.get(0).has("endpoint"));
        assertFalse(list.get(0).has("apiKeyRef"));
    }

    @Test
    @DisplayName("注册/更新：10 键、缺省空串；部分更新保值；删除 2 键")
    void createUpdateDeleteKeepKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "自建模型");
        JsonNode created = dataOf(controller.createModel(body));

        assertEquals(CREATED_KEYS, fieldNames(created));
        assertEquals("custom", created.get("provider").asText());
        assertEquals("", created.get("endpoint").asText());
        assertEquals("active", created.get("status").asText());

        String id = created.get("id").asText();
        JsonNode updated = dataOf(controller.updateModel(id, Map.of("status", "disabled")));
        assertEquals("disabled", updated.get("status").asText());
        assertEquals("自建模型", updated.get("name").asText(), "未出现的键保持原值");
        assertEquals(CREATED_KEYS, fieldNames(updated));

        JsonNode deleted = dataOf(controller.deleteModel(id));
        assertEquals(List.of("deleted", "id"), fieldNames(deleted));
    }

    @Test
    @DisplayName("健康检查：modelId/status/latencyMs/checkedAt 四键")
    void healthKeepsKeys() throws Exception {
        JsonNode data = dataOf(controller.healthCheck("gpt-4o"));
        assertEquals(List.of("modelId", "status", "latencyMs", "checkedAt"), fieldNames(data));
        assertEquals("healthy", data.get("status").asText());
        assertEquals(120, data.get("latencyMs").asInt());
        assertTrue(data.get("checkedAt").asText().contains("T"));
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
