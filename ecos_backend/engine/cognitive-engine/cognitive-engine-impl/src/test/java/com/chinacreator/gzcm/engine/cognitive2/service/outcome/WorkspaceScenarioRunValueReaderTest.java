package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/** 实际值接缝门禁（V367 批次 D）：datanet action-outcomes → actual。 */
class WorkspaceScenarioRunValueReaderTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static WorkspaceScenarioRunValueReader reader(RestTemplate rest, boolean withActual) {
        ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        when(resolver.resolve(eq("workspace"), any())).thenReturn(Optional.of("http://ws"));
        when(resolver.resolve(eq("datanet"), any())).thenReturn(Optional.of("http://dn"));
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("/runs")) {
                        return ResponseEntity.ok(Map.of("code", 0, "data", List.of(Map.of(
                                "id", "fr1", "metric", "m1",
                                "forecast_result", Map.of("points", List.of(Map.of("value", 10)))))));
                    }
                    if (url.contains("action-outcomes")) {
                        return ResponseEntity.ok(Map.of("code", 0, "data", withActual
                                ? List.of(Map.of("actual_impact", 12,
                                        "detail_json", "{\"metricRef\":\"m1\"}"))
                                : List.of()));
                    }
                    return ResponseEntity.ok(Map.of("code", 0, "data", List.of()));
                });
        return new WorkspaceScenarioRunValueReader(rest, resolver);
    }

    private static OutcomeMetricValueReader.ResolveQuery query() {
        return new OutcomeMetricValueReader.ResolveQuery("sc1", "r1", "fr1", "cal1", null, null,
                List.of(new OutcomeMetricValueReader.MetricRefPair("m1", null, null)));
    }

    @Test
    @DisplayName("有 action-outcome ⇒ predicted=10 / actual=12，fullyResolved")
    void resolvesPredictedAndActual() {
        List<OutcomeMetricValueReader.ResolvedValue> rows =
                reader(mock(RestTemplate.class), true).resolve(query());
        assertEquals(1, rows.size());
        assertEquals(0, rows.get(0).predicted().compareTo(new BigDecimal("10")));
        assertEquals(0, rows.get(0).actual().compareTo(new BigDecimal("12")));
        assertNull(rows.get(0).unavailableReason());
        assertTrue(rows.get(0).fullyResolved());
    }

    @Test
    @DisplayName("无 action-outcome ⇒ actual=null 且逐指标给 unavailableReason，禁填 0")
    void missingActualStaysUnavailable() {
        List<OutcomeMetricValueReader.ResolvedValue> rows =
                reader(mock(RestTemplate.class), false).resolve(query());
        assertEquals(1, rows.size());
        assertNotNull(rows.get(0).predicted());
        assertNull(rows.get(0).actual(), "无实际值必须为 null，不能写 0");
        assertNotNull(rows.get(0).unavailableReason());
        assertTrue(!rows.get(0).fullyResolved());
    }
}
