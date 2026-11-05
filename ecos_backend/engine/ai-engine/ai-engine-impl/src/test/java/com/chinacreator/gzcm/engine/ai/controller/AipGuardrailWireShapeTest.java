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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIP 护栏端点 wire 保真（DI-2 #147DX）。
 *
 * <p>护栏行 10 键（{@code rule/priority/enabled} 为请求体原值透传）；部分更新只覆盖出现的键；
 * 删除回执 2 键；审计日志 8 键（details 自由对象）。</p>
 */
class AipGuardrailWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> GUARDRAIL_KEYS = List.of("id", "name", "type", "rule",
            "action", "priority", "enabled", "description", "createdAt", "updatedAt");

    private AIPGuardrailController controller;

    @BeforeEach
    void setUp() {
        controller = new AIPGuardrailController();
    }

    @Test
    @DisplayName("创建：10 键；缺省 type/action/rule/priority/enabled 回填")
    void createKeepsKeysAndDefaults() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "敏感词过滤");
        body.put("rule", Map.of("keywords", List.of("secret")));

        JsonNode data = dataOf(controller.createGuardrail(body));

        assertEquals(GUARDRAIL_KEYS, fieldNames(data));
        assertEquals("content_filter", data.get("type").asText());
        assertEquals("block", data.get("action").asText());
        assertEquals(1, data.get("priority").asInt());
        assertTrue(data.get("enabled").asBoolean());
        assertTrue(data.get("rule").get("keywords").isArray(), "rule 原值透传（对象形态保留）");
        assertEquals("", data.get("description").asText());
    }

    @Test
    @DisplayName("更新：只覆盖出现的键；未出现字段保持原值；删除回执 2 键")
    void updateKeepsOtherFieldsAndDeleteShape() throws Exception {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("name", "限流");
        created.put("rule", "r1");
        String id = dataOf(controller.createGuardrail(created)).get("id").asText();

        JsonNode updated = dataOf(controller.updateGuardrail(id, Map.of("enabled", false)));
        assertEquals(GUARDRAIL_KEYS, fieldNames(updated));
        assertTrue(!updated.get("enabled").asBoolean());
        assertEquals("限流", updated.get("name").asText(), "未出现的 name 保持原值");
        assertEquals("r1", updated.get("rule").asText());

        JsonNode deleted = dataOf(controller.deleteGuardrail(id));
        assertEquals(List.of("deleted", "id"), fieldNames(deleted));
        assertEquals(id, deleted.get("id").asText());
        assertEquals(404, controller.deleteGuardrail(id).getCode(), "二次删除 404");
    }

    @Test
    @DisplayName("审计日志：8 键；details 自由对象保真；首次访问生成 3 条")
    void auditLogsKeepKeys() throws Exception {
        JsonNode list = MAPPER.valueToTree(controller.listAuditLogs()).get("data");
        assertEquals(3, list.size());
        assertEquals(List.of("id", "event", "agentId", "user", "action", "result", "timestamp",
                "details"), fieldNames(list.get(0)));
        assertEquals(150, list.get(0).get("details").get("tokensUsed").asInt());
    }

    // ── 辅助 ─────────────────────────────────────────────

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
