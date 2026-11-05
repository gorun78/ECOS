package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认知规划端点 wire 保真（DI-2 #147DU）。
 *
 * <p>计划体 10 键；{@code chain[]} 双形——推理器节点按原 {@code convertValue} 展开（9 字段），
 * 兜底层为固定 4 键 {@code depth/node/confidence/source}；建议 2 键；优化建议 4 键。</p>
 */
class CognitivePlannerWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> PLAN_KEYS = List.of("planId", "goal", "context", "domain",
            "parameters", "chain", "rootCause", "suggestions", "status", "createdAt");

    private static CausalReasonerService reasoner(CausalChainResult result) {
        return new CausalReasonerService() {
            @Override
            public List<com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge> inferCausalGraph(
                    String domain) {
                return List.of();
            }

            @Override
            public double estimateCausalEffect(String source, String target) {
                return 0.0;
            }

            @Override
            public CausalChainResult diagnose(DiagnosisRequest request) {
                if (result == null) {
                    throw new IllegalStateException("reasoner down");
                }
                return result;
            }
        };
    }

    @Test
    @DisplayName("推理失败：计划 10 键 + 两层兜底链（各 4 键）；parameters 缺省为空对象")
    void fallbackChainKeepsShape() throws Exception {
        CognitivePlannerController controller = new CognitivePlannerController(reasoner(null));

        JsonNode data = dataOf(controller.createPlan(Map.of("goal", "提升收入")));

        assertEquals(PLAN_KEYS, fieldNames(data));
        assertEquals("提升收入", data.get("goal").asText());
        assertEquals("default", data.get("domain").asText());
        assertEquals(2, data.get("chain").size());
        assertEquals(List.of("depth", "node", "confidence", "source"),
                fieldNames(data.get("chain").get(0)));
        assertEquals(1, data.get("chain").get(0).get("depth").asInt());
        assertTrue(data.get("parameters").isObject() && data.get("parameters").size() == 0);
        assertTrue(data.has("rootCause") && data.get("rootCause").isNull());
        assertEquals(0, data.get("suggestions").size());
        assertEquals("planned", data.get("status").asText());
        assertTrue(data.get("planId").asText().startsWith("plan-"));
    }

    @Test
    @DisplayName("推理成功：节点按原展开字段出（9 键）带出参数/根因/建议 2 键；详情可读回")
    void reasonerChainKeepsExpandedFields() throws Exception {
        CausalChainResult result = new CausalChainResult();
        CausalChainNode n1 = new CausalChainNode(1, "收入下降", 1.0, "metric");
        CausalChainNode n2 = new CausalChainNode(2, "渠道流失", 0.7, "KG");
        result.setCausalChain(List.of(n1, n2));
        result.setRootCause("渠道流失");
        result.setSuggestions(List.of("补渠道", "查漏斗"));
        CognitivePlannerController controller = new CognitivePlannerController(reasoner(result));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goal", "提升收入");
        body.put("context", "Q4");
        body.put("parameters", Map.of("budget", 10));
        JsonNode data = dataOf(controller.createPlan(body));

        assertEquals(2, data.get("chain").size());
        assertEquals(9, fieldNames(data.get("chain").get(0)).size(), "CausalChainNode 原字段面");
        assertEquals("收入下降", data.get("chain").get(0).get("node").asText());
        assertEquals("渠道流失", data.get("rootCause").asText());
        assertEquals(List.of("index", "suggestion"), fieldNames(data.get("suggestions").get(0)));
        assertEquals("补渠道", data.get("suggestions").get(0).get("suggestion").asText());
        assertEquals(10, data.get("parameters").get("budget").asInt());

        String planId = data.get("planId").asText();
        JsonNode detail = dataOf(controller.getPlan(planId));
        assertEquals(PLAN_KEYS, fieldNames(detail));
    }

    @Test
    @DisplayName("优化：建议 4 键；数值 +10%（10→11.0）；空参数回落 global 一条")
    void optimizeKeepsSuggestionKeys() throws Exception {
        CognitivePlannerController controller = new CognitivePlannerController(reasoner(null));

        JsonNode withParams = dataOf(controller.optimize(Map.of("parameters", Map.of("budget", 10))));
        assertEquals(1, withParams.size());
        assertEquals(List.of("parameter", "currentValue", "suggestedValue", "reason"),
                fieldNames(withParams.get(0)));
        assertEquals("budget", withParams.get(0).get("parameter").asText());
        assertEquals(11.0, withParams.get(0).get("suggestedValue").asDouble(), 1e-9);

        JsonNode empty = dataOf(controller.optimize(Map.of("context", "保持")));
        assertEquals(1, empty.size());
        assertEquals("global", empty.get(0).get("parameter").asText());
        assertFalse(empty.get(0).get("reason").asText().isBlank());
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
