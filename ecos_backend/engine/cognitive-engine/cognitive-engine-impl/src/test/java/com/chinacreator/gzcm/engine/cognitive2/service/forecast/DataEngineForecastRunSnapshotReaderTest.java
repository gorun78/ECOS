package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Cell;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.ContributionEdgeSource;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.RunSnapshot;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.SnapshotQuery;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.DataEngineForecastRunSnapshotReader.SnapshotWithLineage;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 预测运行快照读 SPI 实现单测（详设-09 v1.2 §十一 H-2b / H-5）。
 *
 * <p>三件事逐条验：① 命中快照能读到六要素 + cell/edge 明细（金额走文本原文入 BigDecimal）；
 * ② 幂等复用路径 {@code reusedFromRunId} 只在源侧真透出该列时才为真，缺列历史行禁伪造回填；
 * ③ data-engine 端点未上线 / 运行不存在 / 依赖不可达三种语义严格可分（501 vs 404 vs 503），
 * 禁 200 + 假数据（X-44 / X-42）。</p>
 */
class DataEngineForecastRunSnapshotReaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "http://datanet-test-base";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
    private final DataEngineForecastRunSnapshotReader reader =
            new DataEngineForecastRunSnapshotReader(restTemplate, resolver);

    private static final String SNAPSHOT_JSON = """
            {"code":0,"message":"ok","data":{
              "forecastRunId":"run-001","scenarioId":"scn-09","runType":"DIAGNOSE","status":"SUCCEEDED",
              "caliberId":"cal-2026","caliberVersion":"v3","asOfTime":"2026-10-01T00:00:00Z",
              "snapshotId":"snap-77","formulaVersion":"f-12","baselineRunId":"run-000",
              "baselineProfit":"1000000.0000","scenarioProfit":"1060000.0000",
              "cells":[
                {"projectId":"P-1","period":202601,"baseline":"100000.0000","scenario":"160000.0000"},
                {"project_id":"P-2","period":202602,"baseline":"50000.0000","scenario":"50000.0000"}
              ],
              "edges":[{"chainId":"c-1","path":[{"node":"n1","type":"ACTION"}],
                        "baseline":"10.00","scenario":"12.00",
                        "evidence_refs":["ev-1"],"stat_support":{"method":"did","p_value":0.01}}]
            }}""";

    private void routeRegistered() {
        when(resolver.resolve(DataEngineForecastRunSnapshotReader.DATA_OWNER_SERVICE,
                ServiceEndpointResolver.RouteMode.MONOLITH)).thenReturn(Optional.of(BASE));
    }

    private void respondWith(String json) {
        routeRegistered();
        JsonNode node = parse(json);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(node, HttpStatus.OK));
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("测试夹具 JSON 不合法: " + e.getMessage(), e);
        }
    }

    private static SnapshotQuery query(String runId) {
        return new SnapshotQuery(runId, null, null, null, null, null);
    }

    // ── 命中读取 ────────────────────────────────────────────────

    @Test
    @DisplayName("命中快照：六要素 + cell/edge 明细全部读到，金额保持源侧 scale（不经 double）")
    void readsSixElementSnapshotFromDataEngine() {
        respondWith(SNAPSHOT_JSON);

        RunSnapshot snapshot = reader.getRun(query("run-001"));

        assertEquals("run-001", snapshot.forecastRunId());
        assertEquals("cal-2026", snapshot.caliberId());
        assertEquals("v3", snapshot.caliberVersion());
        assertEquals("2026-10-01T00:00:00Z", snapshot.asOfTime());
        assertEquals(new BigDecimal("1000000.0000"), snapshot.baselineProfit());
        assertEquals(new BigDecimal("1060000.0000"), snapshot.scenarioProfit());
        assertEquals(2, snapshot.cells().size());
        Cell first = snapshot.cells().get(0);
        assertEquals("P-1", first.projectId());
        assertEquals(202601, first.period());
        assertEquals(new BigDecimal("160000.0000"), first.scenario());
        // snake_case 形态的列名同样可读（data-engine 侧列名单源是 snake，VO 是 camel）
        assertEquals("P-2", snapshot.cells().get(1).projectId());

        List<ContributionEdgeSource> edges = snapshot.edges();
        assertEquals(1, edges.size());
        assertEquals("c-1", edges.get(0).chainId());
        assertEquals(List.of("ev-1"), edges.get(0).evidenceRefs());
        assertNotNull(edges.get(0).statSupport());
    }

    @Test
    @DisplayName("reusedFromRunId 复用路径：源侧透出该列 ⇒ reused=true 并带出被复用 run 键")
    void surfacesIdempotentReuseWhenColumnPresent() {
        respondWith(SNAPSHOT_JSON.replace("\"baselineRunId\":\"run-000\"",
                "\"baselineRunId\":\"run-000\",\"reusedFromRunId\":\"run-000\""));

        SnapshotWithLineage withLineage = reader.getSnapshotWithLineage(query("run-001"));

        assertTrue(withLineage.lineage().reused());
        assertEquals("run-000", withLineage.lineage().reusedFromRunIdOrEmpty());
        assertEquals("snap-77", withLineage.lineage().snapshotId());
        assertEquals("f-12", withLineage.lineage().formulaVersion());
        assertEquals(reader.getLineage(query("run-001")).forecastRunId(), "run-001");
    }

    @Test
    @DisplayName("缺 reused_from_run_id 列的历史行 ⇒ reused=false（禁伪造回填复用来源）")
    void missingReuseColumnStaysNotReused() {
        respondWith(SNAPSHOT_JSON);
        DataEngineForecastRunSnapshotReader.SnapshotLineage lineage = reader.getLineage(query("run-001"));
        assertFalse(lineage.reused());
        assertNull(lineage.reusedFromRunIdOrEmpty());
        assertNull(lineage.reusedFromRunId());
    }

    @Test
    @DisplayName("projectId 限定命中范围（读侧过滤，禁把全量当命中）")
    void filtersCellsByProjectId() {
        respondWith(SNAPSHOT_JSON);
        RunSnapshot snapshot = reader.getRun(new SnapshotQuery(
                "run-001", null, null, null, "P-1", null));
        assertEquals(1, snapshot.cells().size());
        assertEquals("P-1", snapshot.cells().get(0).projectId());
    }

    @Test
    @DisplayName("载荷无 cells 数组（canonicalBytes 文本形态）⇒ 明细为空，由调用方 fail-loud，本层不合成")
    void absentCellArrayYieldsNoSynthesisedRows() {
        respondWith("""
                {"code":0,"data":{"forecastRunId":"run-001","caliberId":"cal-2026","caliberVersion":"v3",
                 "asOfTime":"2026-10-01T00:00:00Z","baselineProfit":"1","scenarioProfit":"2",
                 "forecast_result_json":"{\\"rows\\":[]}"}}""");
        RunSnapshot snapshot = reader.getRun(query("run-001"));
        assertTrue(snapshot.cells().isEmpty());
        assertTrue(snapshot.edges().isEmpty());
    }

    // ── 语义可分：404 / 501 / 503 ───────────────────────────────

    @Test
    @DisplayName("HTTP 404 且响应体带业务原文 ⇒ 真实 404（运行不存在），而非未上线语义")
    void runNotFoundIsRealNotFound() {
        routeRegistered();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY,
                        "{\"code\":404,\"message\":\"预测运行不存在: run-404\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-404")));
        assertEquals(CognitiveErrorCode.E404_FORECAST_RUN, e.errorCode);
        assertEquals(404, e.httpStatus);
    }

    @Test
    @DisplayName("HTTP 404 无业务原文 ⇒ 该路由未上线 ⇒ 真实 501（ECOS-COG-503），禁回 200 空快照")
    void endpointAbsentIs501NotEmptySnapshot() {
        routeRegistered();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        HttpHeaders.EMPTY,
                        "{\"timestamp\":\"2026-10-01\"}".getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-001")));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
        assertEquals(501, e.httpStatus);
        assertTrue(e.missing.contains("FC-02"), e.missing);
    }

    @Test
    @DisplayName("HTTP 200 + body.code=404（网关把异常转成响应体）⇒ 仍判 404 语义")
    void bodyLevelNotFoundMapsToNotFound() {
        respondWith("""
                {"code":404,"message":"预测运行不存在: run-9","data":null}""");
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-9")));
        assertEquals(CognitiveErrorCode.E404_FORECAST_RUN, e.errorCode);
        assertEquals(404, e.httpStatus);
    }

    @Test
    @DisplayName("通道不可达 / 裁决失败 / 空响应体 ⇒ 真实 503（ECOS-COG-502D），禁降级为空数据")
    void dependencyFailuresAre503() {
        routeRegistered();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("connect refused"));
        assertEquals(503, assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-001"))).httpStatus);

        respondWith("""
                {"code":500,"message":"内部错误","data":null}""");
        CognitiveGuardException verdictError = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-001")));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, verdictError.errorCode);

        respondWith("null");
        assertEquals(503, assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-001"))).httpStatus);
    }

    @Test
    @DisplayName("route-manifest 未登记 datanet ⇒ 500 fail-loud（禁硬编码基址兜底，X-35）")
    void unresolvedBaseFailsLoud() {
        when(resolver.resolve(anyString(), any(ServiceEndpointResolver.RouteMode.class)))
                .thenReturn(Optional.empty());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("run-001")));
        assertEquals(CognitiveErrorCode.E504_INTERNAL, e.errorCode);
        assertTrue(e.getMessage().contains("route-manifest"), e.getMessage());
    }

    // ── 定位与口径锁定 ──────────────────────────────────────────

    @Test
    @DisplayName("缺 forecastRunId ⇒ 400（禁全库扫描快照）")
    void blankRunIdIsRejected() {
        routeRegistered();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(query("  ")));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertTrue(e.getMessage().contains("禁全库扫描"), e.getMessage());
    }

    @Test
    @DisplayName("带 departmentId 限定 ⇒ 真实 501（禁静默放宽范围后回全量）")
    void departmentScopedQueryIsNotSilentlyWidened() {
        routeRegistered();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(new SnapshotQuery(
                        "run-001", null, null, null, null, "DEPT-1")));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
        assertEquals(501, e.httpStatus);
    }

    @Test
    @DisplayName("请求口径与返回快照口径不一致 ⇒ 500；版本不一致 ⇒ 409（禁拿别的口径冒充）")
    void caliberMismatchFailsLoud() {
        respondWith(SNAPSHOT_JSON);
        CognitiveGuardException idMismatch = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(new SnapshotQuery(
                        "run-001", "cal-other", null, null, null, null)));
        assertEquals(CognitiveErrorCode.E504_INTERNAL, idMismatch.errorCode);

        CognitiveGuardException versionMismatch = assertThrows(CognitiveGuardException.class,
                () -> reader.getRun(new SnapshotQuery(
                        "run-001", "cal-2026", "v9", null, null, null)));
        assertEquals(CognitiveErrorCode.E409_VERSION_STALE, versionMismatch.errorCode);
        assertEquals(409, versionMismatch.httpStatus);
    }

    // ── FC-03 情景复制：能力未就绪 ⇒ 501 ────────────────────────

    @Test
    @DisplayName("copyScenario 未上线 ⇒ 真实 501 + missing 指向属主缺口（认知域不自算情景金额）")
    void scenarioCopyHonestStubCapability() {
        routeRegistered();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.copyScenario(new ForecastRunSnapshotReader.ScenarioCopyRequest("run-000", List.of())));
        assertEquals(CognitiveErrorCode.E503_NOT_IMPLEMENTED, e.errorCode);
        assertEquals(CognitiveErrorCode.MSG_NOT_IMPLEMENTED, e.getMessage());
        assertEquals(501, e.httpStatus);
        assertTrue(e.missing.contains("scenario-copy"), e.missing);
        assertTrue(e.missing.contains("H-5"), e.missing);
    }

    @Test
    @DisplayName("copyScenario 缺 baselineRunId ⇒ 400（定位键缺失，不进入未上线分支）")
    void scenarioCopyRejectsBlankBaseline() {
        routeRegistered();
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> reader.copyScenario(new ForecastRunSnapshotReader.ScenarioCopyRequest(null, List.of())));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
    }

    @Test
    @DisplayName("读通道 URI 由 route-manifest 基址 + 登记路径拼出（禁硬编码服务地址）")
    void requestUriComesFromResolverBaseAndRegisteredPath() {
        respondWith(SNAPSHOT_JSON);
        reader.getRun(query("run-001"));
        org.mockito.ArgumentCaptor<String> url = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(url.capture(), eq(HttpMethod.GET), nullable(HttpEntity.class), eq(JsonNode.class));
        assertTrue(url.getValue().startsWith(BASE), url.getValue());
        assertTrue(url.getValue().endsWith("/api/v1/datanet/forecast-runs/run-001/snapshot"), url.getValue());
    }
}
