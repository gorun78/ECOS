package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * C-2 数值接缝门禁（2026-11-07）：预测值只来自已落库确定性产物；取不到必须逐指标写明原因，
 * 禁 0 填充、禁抛断整次评估、禁指标名不符时拿别的值顶替。
 */
class WorkspaceScenarioRunValueReaderTest {

    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);

    private WorkspaceScenarioRunValueReader readerWith(RestTemplate template) {
        when(resolver.resolve(eq("workspace"), any())).thenReturn(Optional.of("http://localhost:18090"));
        return new WorkspaceScenarioRunValueReader(template, resolver);
    }

    private static RestTemplate returning(Object data) {
        return new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType,
                                                  Object... uriVariables) {
                return (ResponseEntity<T>) ResponseEntity.ok(Map.of("code", 0, "data", data));
            }
        };
    }

    private static OutcomeMetricValueReader.ResolveQuery query(String metricRef) {
        CalculationResultRef forecast = new CalculationResultRef();
        forecast.setCalculationId("run_540cbb12");
        forecast.setDeterministic(true);
        return new OutcomeMetricValueReader.ResolveQuery("sc_ent2_group_month", "run_1",
                "run_540cbb12", "cal_1", "2026-01-01T00:00:00Z", "2026-06-30T00:00:00Z",
                List.of(new OutcomeMetricValueReader.MetricRefPair(metricRef, forecast, null)));
    }

    @Test
    void resolvesPredictedFromStoredDeterministicRunAndLeavesActualExplicit() {
        Map<String, Object> run = Map.of(
                "id", "run_540cbb12",
                "metric", "毛利率",
                "forecast_result", Map.of("points", Map.of("points",
                        List.of(Map.of("t", "6", "value", "86.7606480305714")))));
        WorkspaceScenarioRunValueReader reader = readerWith(returning(List.of(run)));

        List<OutcomeMetricValueReader.ResolvedValue> out = reader.resolve(query("毛利率"));

        assertEquals(1, out.size());
        OutcomeMetricValueReader.ResolvedValue v = out.get(0);
        assertEquals(new BigDecimal("86.7606480305714"), v.predicted());
        assertNull(v.actual(), "实际值无权威通道时必须为 null，禁 0 填充");
        assertNotNull(v.unavailableReason());
        assertTrue(v.unavailableReason().contains("actual"), v.unavailableReason());
        assertEquals("run_540cbb12", v.calculationRef());
        assertTrue(!v.fullyResolved());
    }

    @Test
    void metricMismatchIsExplicitInsteadOfBorrowingAnotherMetric() {
        Map<String, Object> run = Map.of("id", "run_540cbb12", "metric", "毛利率",
                "forecast_result", Map.of("points", Map.of("points",
                        List.of(Map.of("value", "1.0")))));
        WorkspaceScenarioRunValueReader reader = readerWith(returning(List.of(run)));

        OutcomeMetricValueReader.ResolvedValue v = reader.resolve(query("M_PROFIT")).get(0);

        assertNull(v.predicted());
        assertTrue(v.unavailableReason().contains("metric mismatch"), v.unavailableReason());
    }

    @Test
    void runNotFoundIsExplicit() {
        WorkspaceScenarioRunValueReader reader = readerWith(returning(List.of()));

        OutcomeMetricValueReader.ResolvedValue v = reader.resolve(query("毛利率")).get(0);

        assertNull(v.predicted());
        assertTrue(v.unavailableReason().contains("not found"), v.unavailableReason());
    }

    @Test
    void channelFailureDoesNotThrowAndIsExplicit() {
        RestTemplate broken = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
                                                  HttpEntity<?> requestEntity, Class<T> responseType,
                                                  Object... uriVariables) {
                throw new ResourceAccessException("connect timed out");
            }
        };
        WorkspaceScenarioRunValueReader reader = readerWith(broken);

        OutcomeMetricValueReader.ResolvedValue v = reader.resolve(query("毛利率")).get(0);

        assertNull(v.predicted());
        assertTrue(v.unavailableReason().contains("unavailable"), v.unavailableReason());
    }

    @Test
    void pointValueParsesJsonStringAndFlatValue() {
        assertEquals(new BigDecimal("2.5"), WorkspaceScenarioRunValueReader.firstPointValue(
                "{\"points\":{\"points\":[{\"value\":\"2.5\"}]}}"));
        assertEquals(new BigDecimal("4"), WorkspaceScenarioRunValueReader.firstPointValue(
                Map.of("type", "jsonb", "value", "{\"points\":{\"points\":[{\"value\":4}]}}")),
                "PGobject 序列化形态 {type,value} 必须先解包");
        assertEquals(new BigDecimal("3"), WorkspaceScenarioRunValueReader.firstPointValue(
                Map.of("value", 3)));
        assertNull(WorkspaceScenarioRunValueReader.firstPointValue("not-json"));
        assertNull(WorkspaceScenarioRunValueReader.firstPointValue(Map.of()));
    }
}
