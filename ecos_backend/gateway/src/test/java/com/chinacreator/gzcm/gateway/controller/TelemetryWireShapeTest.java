package com.chinacreator.gzcm.gateway.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.TelemetryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 遥测端点 wire 保真（DI-2 #147EF）。
 *
 * <p>健康检查成功腿 6 键（含库计数）、异常腿 4 键（两计数不出现、{@code db} 落 error 文案）；
 * Token 汇总成功腿 4 键（totals/by_model/by_operation 原样结构）、异常腿 2 键（range/error）。</p>
 */
class TelemetryWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 只覆盖健康与汇总读数，DAO 不参与。 */
    private static final class FakeTelemetryService extends TelemetryService {
        private final boolean throwOnCounts;
        private final boolean throwOnSummary;

        private FakeTelemetryService(boolean throwOnCounts) {
            this(throwOnCounts, false);
        }

        private FakeTelemetryService(boolean throwOnCounts, boolean throwOnSummary) {
            super(null);
            this.throwOnCounts = throwOnCounts;
            this.throwOnSummary = throwOnSummary;
        }

        @Override
        public Map<String, Object> queryHealthCounts() {
            if (throwOnCounts) {
                throw new IllegalStateException("db down");
            }
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("spans_stored", 12L);
            counts.put("token_records", 34L);
            return counts;
        }

        @Override
        public Map<String, Object> queryTokenSummaryTotals(int days) {
            if (throwOnSummary) {
                throw new IllegalStateException("aggregate failed");
            }
            Map<String, Object> totals = new LinkedHashMap<>();
            totals.put("prompt_tokens", 100L);
            return totals;
        }

        @Override
        public List<Map<String, Object>> queryTokenSummaryByModel(int days) {
            return List.of(Map.of("model", "deepseek-chat", "tokens", 60));
        }

        @Override
        public List<Map<String, Object>> queryTokenSummaryByOperation(int days) {
            return List.of(Map.of("operation", "chat", "tokens", 40));
        }
    }

    @Test
    @DisplayName("健康检查：成功 6 键；异常 4 键（db=error: …）")
    void healthKeepsConditionalKeys() throws Exception {
        JsonNode ok = dataOf(new TelemetryController(new FakeTelemetryService(false)).health());
        assertEquals(List.of("service", "status", "timestamp", "spans_stored", "token_records", "db"),
                fieldNames(ok));
        assertEquals(12, ok.get("spans_stored").asInt());
        assertEquals("connected", ok.get("db").asText());

        JsonNode down = dataOf(new TelemetryController(new FakeTelemetryService(true)).health());
        assertEquals(List.of("service", "status", "timestamp", "db"), fieldNames(down));
        assertFalse(down.has("spans_stored"));
        assertTrue(down.get("db").asText().startsWith("error: "));
    }

    @Test
    @DisplayName("Token 汇总：成功 4 键（三段原样）；range 非法 → 2 键（range/error）")
    void tokenSummaryKeepsConditionalKeys() throws Exception {
        JsonNode ok = dataOf(new TelemetryController(new FakeTelemetryService(false))
                .getTokenSummary("7d"));
        assertEquals(List.of("range", "totals", "by_model", "by_operation"), fieldNames(ok));
        assertEquals(100, ok.get("totals").get("prompt_tokens").asInt());
        assertEquals("deepseek-chat", ok.get("by_model").get(0).get("model").asText());

        JsonNode bad = dataOf(new TelemetryController(new FakeTelemetryService(false, true))
                .getTokenSummary("7d"));
        assertEquals("7d", bad.get("range").asText());
        assertEquals(List.of("range", "error"), fieldNames(bad));
        assertEquals("aggregate failed", bad.get("error").asText());
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
