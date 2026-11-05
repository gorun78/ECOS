package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.engine.ai.service.DiagnosticAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 诊断 Agent 面 wire 键面门（DI-2 #147DF）。
 *
 * <p>工具条目 DB 7 键 / 硬编码 5 键（{@code id}/{@code schema} 条件出现）、Agent 配置 6 键、
 * 工具执行回执四态并集、对话 5 键、端到端诊断 5 键。gateway 同类无 Mockito 的限制不适用，
 * 本模块可用 Mockito；DAO 行用 {@link Proxy} 假 ResultSet 喂给真实 RowMapper。</p>
 */
class DiagnosticAgentWireShapeTest {

    private Map<String, Object> nextRow;
    private RestTemplate restTemplate;
    private DiagnosticAgentController controller;

    @BeforeEach
    void setUp() {
        DiagnosticAgentService service = new DiagnosticAgentService(null) {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
                if (nextRow == null) {
                    return List.of();
                }
                try {
                    return List.of(rowMapper.mapRow(fakeResultSet(nextRow), 0));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        restTemplate = mock(RestTemplate.class);
        controller = new DiagnosticAgentController(service, restTemplate);
    }

    private static ResultSet fakeResultSet(Map<String, Object> row) {
        return (ResultSet) Proxy.newProxyInstance(
                DiagnosticAgentWireShapeTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    Object v = row.get(args[0]);
                    return switch (name) {
                        case "getString" -> v == null ? null : String.valueOf(v);
                        case "getBigDecimal" -> v == null ? null : new BigDecimal(String.valueOf(v));
                        case "getDouble" -> v == null ? 0.0 : ((Number) v).doubleValue();
                        case "getLong" -> v == null ? 0L : ((Number) v).longValue();
                        case "getInt" -> v == null ? 0 : ((Number) v).intValue();
                        default -> null;
                    };
                });
    }

    private static List<String> keys(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode json(Object payload) {
        return new ObjectMapper().valueToTree(payload);
    }

    @Test
    @DisplayName("工具列表：DB 行 7 键；无数据时硬编码 5 键且不落 id/schema")
    void toolsShape() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", "t1");
        row.put("code", "query_worldmodel_deviation");
        row.put("name", "查询目标偏差");
        row.put("description", "desc");
        row.put("tool_type", "API");
        row.put("schema_json", "{}");
        row.put("status", "ACTIVE");
        nextRow = row;

        JsonNode db = json(controller.listTools("").getData());
        assertEquals(List.of("id", "code", "name", "description", "toolType", "schema", "status"),
                keys(db.get(0)));

        nextRow = null;
        JsonNode fallback = json(controller.listTools("").getData());
        assertEquals(3, fallback.size());
        assertEquals(List.of("code", "name", "description", "toolType", "status"),
                keys(fallback.get(0)));
        assertFalse(fallback.get(0).has("id"));
    }

    @Test
    @DisplayName("Agent 配置：DB 行 6 键；硬编码 diagnostic 6 键且 tools 为数组")
    void agentConfigShape() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", "a1");
        row.put("name", "诊断");
        row.put("model_provider", "deepseek");
        row.put("model_name", "deepseek-v4-flash");
        row.put("system_prompt", "sys");
        row.put("tools", "[\"t1\"]");
        nextRow = row;

        JsonNode db = json(controller.getAgentConfig("a1").getData());
        assertEquals(List.of("id", "name", "modelProvider", "modelName", "systemPrompt", "tools"),
                keys(db));
        assertEquals("[\"t1\"]", db.path("tools").asText());

        nextRow = null;
        JsonNode fallback = json(controller.getAgentConfig("diagnostic").getData());
        assertEquals(6, keys(fallback).size());
        assertEquals(3, fallback.path("tools").size());
    }

    @Test
    @DisplayName("工具执行：诊断工具 3 键 + 偏差行 5 键；知识图谱分支 7 键且无 tool")
    void executeShapes() {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("name", "交付准时率");
        row.put("target_value", 90);
        row.put("current_value", 67);
        row.put("deviation_pct", 25.6);
        row.put("status", "AT_RISK");
        nextRow = row;

        JsonNode dev = json(controller.executeTool(Map.of("tool", "query_worldmodel_deviation")).getData());
        assertEquals(List.of("tool", "deviations", "executedAt"), keys(dev));
        assertEquals(List.of("name", "targetValue", "currentValue", "deviationPct", "status"),
                keys(dev.path("deviations").get(0)));

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("data", Map.of("nodes", List.of(), "edges", List.of(),
                        "nodeCount", 0, "edgeCount", 0)));
        JsonNode kg = json(controller.executeTool(Map.of("tool", "query_knowledge_graph",
                "params", Map.of("entity", "订单"))).getData());
        assertEquals(List.of("nodes", "edges", "nodeCount", "edgeCount", "queryEntity",
                "dispatched", "executedAt"), keys(kg));
        assertEquals("订单", kg.path("queryEntity").asText());
    }

    @Test
    @DisplayName("对话 5 键 + 思维链 2 键；端到端诊断 5 键")
    void chatAndCallShapes() {
        nextRow = null;

        JsonNode chat = json(controller.chat(Map.of("agentId", "diagnostic", "message", "你好")).getData());
        assertEquals(List.of("success", "source", "logId", "responseText", "thoughtTrace"), keys(chat));
        assertEquals(List.of("type", "summary"), keys(chat.path("thoughtTrace").get(0)));

        JsonNode call = json(controller.callAgent(Map.of("agent", "diagnostic", "query", "偏差")).getData());
        assertEquals(List.of("agent", "query", "answer", "deviations", "executedAt"), keys(call));
    }
}
