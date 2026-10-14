package com.chinacreator.gzcm.engine.cognitive2.service.forecast;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.Artifact;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.DeterministicForecastCalculator.SixFactors;
import com.chinacreator.gzcm.engine.cognitive2.service.calc.ScenarioBalanceChecker.Cell;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunComputeService.ComputeRequest;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.ContributionEdgeSource;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.RunSnapshot;
import com.chinacreator.gzcm.engine.cognitive2.service.forecast.ForecastRunSnapshotReader.SnapshotQuery;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预测 run 计算服务单测（差距分析 P0-4 主链接通 / 详设-09 §十一 H-5 认知侧半区）。
 *
 * <p>断言面即本批的四条红线：
 * ① <b>无事实即无计算</b>（零行 / DQ FAILED 行 ⇒ 400，且写通道零调用，X-44）；
 * ② <b>幂等锚属主 = 调用方</b>（裁决 D-B2②-1：落库与回读都用编排面给定键，
 *    引擎自产的含 runId 派生键只作 canonicalContentHash 另栏，绝不可顶替）；
 * ③ <b>run 身份由生产方生成</b>（请求缺 forecastRunId ⇒ 认知域造 UUID，编排面不造 id）；
 * ④ <b>三值物理无列 ⇒ 原样 null</b>（R-62 ①，禁插值/取均值/兜底）。</p>
 *
 * <p>失败语义三态可分：读通道不可达 502、route-manifest 未登记 500、
 * 写回执成功但读回未确认 ⇒ {@code degraded=true}（禁把已提交当已核验）。</p>
 */
class ForecastRunComputeServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATANET_BASE = "http://datanet-test-base";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver resolver =
            mock(com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver.class);
    private final ForecastArtifactWriteChannel writeChannel = mock(ForecastArtifactWriteChannel.class);
    private final DataEngineForecastRunSnapshotReader snapshotReader =
            mock(DataEngineForecastRunSnapshotReader.class);

    private final ForecastRunComputeService service =
            new ForecastRunComputeService(restTemplate, resolver, writeChannel, snapshotReader);

    /** 每页事实 items 的 JSON 原文（null = 该页起无更多数据）。 */
    private String[] factPages = {};

    /** 幂等键读通道响应原文（null = 未登记，命中 URL 即失败）。 */
    private String byKeyResponse;

    /** 事实通道整包响应覆盖（用于裁决失败 / 空响应体等异常形态）。 */
    private String factsBodyOverride;

    // ── 夹具 ────────────────────────────────────────────────────

    private void route() {
        when(resolver.resolve(anyString(),
                any(com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver.RouteMode.class)))
                .thenReturn(Optional.of(DATANET_BASE));
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET),
                nullable(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenAnswer(invocation -> respond(invocation.getArgument(0)));
    }

    private ResponseEntity<com.fasterxml.jackson.databind.JsonNode> respond(String url) throws Exception {
        if (url.contains("/facts/")) {
            if (factsBodyOverride != null) {
                return new ResponseEntity<>(MAPPER.readTree(factsBodyOverride), HttpStatus.OK);
            }
            int page = Integer.parseInt(url.replaceAll(".*[?&]page=(\\d+).*", "$1"));
            String items = page <= factPages.length ? factPages[page - 1] : "";
            String body = "{\"code\":0,\"message\":\"ok\",\"data\":{\"total\":1,\"page\":" + page
                    + ",\"pageSize\":200,\"items\":[" + items + "]}}";
            return new ResponseEntity<>(MAPPER.readTree(body), HttpStatus.OK);
        }
        if (url.contains("/by-idempotency-key/")) {
            return new ResponseEntity<>(MAPPER.readTree(byKeyResponse), HttpStatus.OK);
        }
        throw new AssertionError("未登记的读通道 URL: " + url);
    }

    private static String fact(String projectId, String period, String amount, String evidenceRef,
                               String dqStatus) {
        return "{\"id\":\"f-1\",\"project_id\":\"" + projectId + "\",\"department_id\":\"D-1\","
                + "\"period\":\"" + period + "\",\"fact_type\":\"STAGE\",\"amount\":" + amount
                + ",\"source_type\":\"IMPORT\",\"evidence_ref\":\"" + evidenceRef + "\""
                + (dqStatus == null ? "" : ",\"dq_status\":\"" + dqStatus + "\"") + "}";
    }

    private void serveFacts(String... items) {
        factPages = new String[]{String.join(",", items)};
    }

    private static ComputeRequest request(String forecastRunId, String idempotencyKey, String contentHash) {
        return new ComputeRequest(forecastRunId, "scn-09", "FORECAST", "cal-2026", "v3",
                "2026-10-01T00:00:00", "snap-77", "f-12", idempotencyKey, contentHash,
                null, "trace-1", null, null, null);
    }

    private static ComputeRequest request() {
        return request(null, "caller-key-1", "caller-hash-1");
    }

    private void ackWritten() {
        when(writeChannel.submit(any(Artifact.class), any(SixFactors.class), anyString(),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                anyString(), anyString()))
                .thenReturn(new ForecastArtifactWriteChannel.WriteAck("write-1", true, false));
    }

    private void readbackOk() {
        RunSnapshot snapshot = new RunSnapshot("run-x", "cal-2026", "v3", "2026-10-01T00:00:00",
                new BigDecimal("1000000.0000"), new BigDecimal("1060000.0000"),
                List.of(new Cell("P-1", 202601, new BigDecimal("1"), new BigDecimal("2"))),
                List.of(new ContributionEdgeSource("c-1", List.of(), new BigDecimal("10.00"),
                        new BigDecimal("12.00"), List.of("ev-1"), null)));
        when(snapshotReader.getSnapshotWithLineage(any(SnapshotQuery.class))).thenReturn(
                new DataEngineForecastRunSnapshotReader.SnapshotWithLineage(snapshot,
                        new DataEngineForecastRunSnapshotReader.SnapshotLineage("run-x", "snap-77",
                                "f-12", null, "scn-09", "FORECAST", "SUCCEEDED", null)));
    }

    // ── ① 无事实即无计算 ─────────────────────────────────────────

    @Test
    @DisplayName("零事实 ⇒ 400 且写通道零调用（禁空产物冒充成功）")
    void noFactsMeansNoArtifact() {
        route();
        factPages = new String[]{"", };

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertTrue(e.getMessage().contains("无事实禁造产物"), e.getMessage());
        verify(writeChannel, never()).submit(any(Artifact.class), any(SixFactors.class), anyString(),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                anyString(), anyString());
    }

    @Test
    @DisplayName("DQ FAILED 的事实行不参与装配（判定不通过即剔除，剔除到零 ⇒ 400）")
    void dqFailedFactsAreExcluded() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "FAILED"));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
    }

    @Test
    @DisplayName("事实行缺 source_type/source_ref ⇒ 400（数值来源不可追溯，禁参与装配）")
    void unsourcedFactIsRejected() {
        route();
        serveFacts("{\"id\":\"f-9\",\"project_id\":\"P-1\",\"period\":\"202601\",\"amount\":100,"
                + "\"fact_type\":\"STAGE\",\"evidence_ref\":\"ev-1\"}");

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertTrue(e.getMessage().contains("source_type"), e.getMessage());
    }

    // ── ② 幂等锚属主 = 调用方（裁决 D-B2②-1）────────────────────

    @Test
    @DisplayName("落库与回执一律用调用方幂等锚；引擎派生指纹另存 canonicalContentHash（顶替即判据失效）")
    void callerAnchorIsPersistedAndEchoed() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        ackWritten();
        readbackOk();

        Map<String, Object> view = service.compute(request("run-fixed", "caller-key-1", "caller-hash-1"));

        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(writeChannel).submit(artifact.capture(), any(SixFactors.class), eq("scn-09"), eq("FORECAST"),
                eq("trace-1"), eq("snap-77"), isNull(), key.capture(), hash.capture());
        assertEquals("caller-key-1", key.getValue());
        assertEquals("caller-hash-1", hash.getValue());
        // 引擎派生键确实不同于调用方键（含 runId 的派生键随重试变化 ⇒ 不能当幂等锚）
        assertFalse(artifact.getValue().idempotencyKey().endsWith("caller-key-1"));
        assertEquals(view.get("canonicalContentHash"), artifact.getValue().contentHash());
        assertNotEqualsAsString(view.get("canonicalContentHash"), view.get("contentHash"));
        assertEquals("caller-key-1", view.get("idempotencyKey"));
        assertEquals("caller-hash-1", view.get("contentHash"));
    }

    private static void assertNotEqualsAsString(Object left, Object right) {
        assertFalse(String.valueOf(left).equals(String.valueOf(right)),
                "canonical 指纹与调用方指纹不应相同: " + left);
    }

    // ── ③ run 身份属主 = 生产方 ──────────────────────────────────

    @Test
    @DisplayName("请求缺 forecastRunId ⇒ 认知域生成 run 键并用于落库与回读（编排面只编排不生产）")
    void runIdIsGeneratedByProducerSide() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        ackWritten();
        readbackOk();

        Map<String, Object> view = service.compute(request());

        String runId = String.valueOf(view.get("forecastRunId"));
        assertTrue(runId.startsWith("fc-"), runId);
        assertEquals(36 + 3, runId.length(), "run 键应为 fc- 前缀 + UUID: " + runId);

        ArgumentCaptor<SixFactors> factors = ArgumentCaptor.forClass(SixFactors.class);
        verify(writeChannel).submit(any(Artifact.class), factors.capture(), anyString(), nullable(String.class),
                anyString(), nullable(String.class), nullable(String.class), anyString(), anyString());
        assertEquals(runId, factors.getValue().forecastRunId());
        assertEquals("cal-2026", factors.getValue().caliberId());
        assertEquals("v3", factors.getValue().caliberVersion());
        assertEquals("f-12", factors.getValue().formulaVersion());
        assertEquals(List.of("ev-1"), factors.getValue().evidenceRefs());
    }

    @Test
    @DisplayName("给定 forecastRunId ⇒ 原样使用（同键重放路径不得另造 run）")
    void providedRunIdIsKeptVerbatim() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        ackWritten();
        readbackOk();

        Map<String, Object> view = service.compute(request("run-given-001", "k", "h"));
        assertEquals("run-given-001", view.get("forecastRunId"));
    }

    // ── 装配与视图 ───────────────────────────────────────────────

    @Test
    @DisplayName("同输入同产物：两次计算 canonicalBytes 一致（确定性可复算，禁掺时间戳/随机量）")
    void computeIsDeterministicForSameInput() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"),
                   fact("P-2", "202601", "50.00", "ev-2", "PASSED"));
        ackWritten();
        readbackOk();

        ArgumentCaptor<Artifact> first = ArgumentCaptor.forClass(Artifact.class);
        service.compute(request("run-det", "k", "h"));
        service.compute(request("run-det", "k", "h"));
        verify(writeChannel, org.mockito.Mockito.times(2)).submit(first.capture(), any(SixFactors.class),
                anyString(), nullable(String.class), anyString(), nullable(String.class),
                nullable(String.class), anyString(), anyString());
        List<Artifact> both = first.getAllValues();
        assertEquals(new String(both.get(0).canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8),
                new String(both.get(1).canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, both.get(0).rows().size());
        // 明细排序稳定（项目 → 部门 → 期间 → 度量），与输入顺序无关
        assertEquals("P-1", both.get(0).rows().get(0).get("projectId"));
        assertEquals(0, new BigDecimal(String.valueOf(both.get(0).rows().get(0).get("value")))
                .compareTo(new BigDecimal("100.00")), "数值原样搬运，等值不失真");
    }

    @Test
    @DisplayName("projectIds 限定命中范围（读侧过滤，禁把全量当命中）")
    void projectScopeIsApplied() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"),
                   fact("P-2", "202601", "50.00", "ev-2", "PASSED"));
        ackWritten();
        readbackOk();

        ComputeRequest scoped = new ComputeRequest(null, "scn-09", "FORECAST", "cal", "v1",
                "2026-10-01T00:00:00", null, "f-1", "k", "h", null, "trace-1",
                List.of("P-2"), List.of("202601"), "M_FC_PROFIT");
        service.compute(scoped);

        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        verify(writeChannel).submit(artifact.capture(), any(SixFactors.class), anyString(),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                anyString(), anyString());
        assertEquals(1, artifact.getValue().rows().size());
        Map<String, Object> row = artifact.getValue().rows().get(0);
        assertEquals("P-2", row.get("projectId"));
        assertEquals("M_FC_PROFIT", row.get("metric"), "请求指定 metric 时以请求为准");
    }

    @Test
    @DisplayName("metric 缺省取事实行 fact_type 原值（禁自造度量名）")
    void metricDefaultsToFactType() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", null));
        ackWritten();
        readbackOk();

        service.compute(request());
        ArgumentCaptor<Artifact> artifact = ArgumentCaptor.forClass(Artifact.class);
        verify(writeChannel).submit(artifact.capture(), any(SixFactors.class), anyString(),
                nullable(String.class), anyString(), nullable(String.class), nullable(String.class),
                anyString(), anyString());
        assertEquals("STAGE", artifact.getValue().rows().get(0).get("metric"));
    }

    // ── 必填要素矩阵 ─────────────────────────────────────────────

    @Test
    @DisplayName("幂等锚/六要素缺项 ⇒ 400 并点名缺哪项（禁半集计算）")
    void requiredElementsAreEnforced() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        assertFieldRejected(withAnchors(null, "h"), "idempotencyKey");
        assertFieldRejected(withAnchors("k", "  "), "contentHash");
        assertFieldRejected(withSix("caliberId", null), "caliberId");
        assertFieldRejected(withSix("formulaVersion", null), "formulaVersion");
        assertFieldRejected(withSix("traceId", null), "traceId");
    }

    @Test
    @DisplayName("请求体为 null ⇒ 400（无输入即无计算）")
    void nullRequestIsRejected() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(null));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
    }

    /** 只替换幂等锚两列，其余要素保持齐备（定位"本批新增必填项"的拒绝面）。 */
    private static ComputeRequest withAnchors(String idempotencyKey, String contentHash) {
        return new ComputeRequest(null, "scn-09", "FORECAST", "cal-2026", "v3",
                "2026-10-01T00:00:00", "snap-77", "f-12", idempotencyKey, contentHash,
                null, "trace-1", null, null, null);
    }

    /** 六要素/身份逐项抽空，验证缺项即拒（switch 单点，禁复制六份构造器）。 */
    private static ComputeRequest withSix(String field, String replacement) {
        String runId = null, caliberId = "cal-2026", caliberVersion = "v3", asOfTime = "2026-10-01T00:00:00",
                formulaVersion = "f-12", traceId = "trace-1";
        switch (field) {
            case "caliberId" -> caliberId = replacement;
            case "caliberVersion" -> caliberVersion = replacement;
            case "asOfTime" -> asOfTime = replacement;
            case "formulaVersion" -> formulaVersion = replacement;
            case "traceId" -> traceId = replacement;
            default -> throw new IllegalArgumentException("测试未覆盖的要素: " + field);
        }
        return new ComputeRequest(runId, "scn-09", "FORECAST", caliberId, caliberVersion, asOfTime,
                "snap-77", formulaVersion, "k", "h", null, traceId, null, null, null);
    }

    private void assertFieldRejected(ComputeRequest request, String field) {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertTrue(e.getMessage().contains(field), e.getMessage() + " 应点名 " + field);
    }

    // ── ④ 三值物理无列 ⇒ null ────────────────────────────────────

    @Test
    @DisplayName("results：区间三值全 null + quantileSource=NOT_AVAILABLE（R-62 ①，禁插值合成）")
    void resultsSurfaceQuantilesAsNull() {
        readbackOk();
        Map<String, Object> results = service.readResults("run-x");
        assertNull(results.get("p10"));
        assertNull(results.get("p50"));
        assertNull(results.get("p90"));
        assertEquals("NOT_AVAILABLE", results.get("quantileSource"));
        assertEquals(1, ((List<?>) results.get("cells")).size());
        assertEquals(new BigDecimal("1060000.0000"), results.get("scenarioProfit"));
    }

    @Test
    @DisplayName("run 视图：quantile 三值 null（compute 与读回两条路径同形）")
    @SuppressWarnings("unchecked")
    void computeViewCarriesUnavailableQuantile() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        ackWritten();
        readbackOk();

        Map<String, Object> view = service.compute(request());
        Map<String, Object> quantile = (Map<String, Object>) view.get("quantile");
        assertNull(quantile.get("p10"));
        assertNull(quantile.get("p50"));
        assertNull(quantile.get("p90"));
        assertEquals(Boolean.FALSE, view.get("degraded"));
        assertEquals(List.of("ev-1"), view.get("evidenceRefs"), "回读快照的因果边证据应带入视图");
        assertEquals(new BigDecimal("1060000.0000"), view.get("scenarioProfit"));
    }

    // ── 读回未确认：degraded ─────────────────────────────────────

    @Test
    @DisplayName("写回执成功但回读失败 ⇒ degraded=true 如实标注（禁把已提交当已核验）")
    void unreadableRunIsFlaggedDegraded() {
        route();
        serveFacts(fact("P-1", "202601", "100.00", "ev-1", "PASSED"));
        ackWritten();
        when(snapshotReader.getSnapshotWithLineage(any(SnapshotQuery.class)))
                .thenThrow(new CognitiveGuardException(CognitiveErrorCode.E503_NOT_IMPLEMENTED,
                        CognitiveErrorCode.MSG_NOT_IMPLEMENTED, "FC-02 快照端点未上线"));

        Map<String, Object> view = service.compute(request());
        assertEquals(Boolean.TRUE, view.get("degraded"));
        assertEquals("SUCCEEDED", view.get("status"), "写回执已达成，状态不谎报为失败");
    }

    // ── 读通道语义 ───────────────────────────────────────────────

    @Test
    @DisplayName("事实读通道不可达 ⇒ 502 ECOS-COG-502（禁降级为空事实后 400 掩盖真因）")
    void unreachableFactsChannelIs502() {
        route();
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET),
                nullable(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new ResourceAccessException("connect refused"));

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, e.errorCode);
        assertEquals(503, e.httpStatus);
    }

    @Test
    @DisplayName("读通道裁决失败（body.code!=0）⇒ 502，不吞")
    void verdictFailureIs502() {
        route();
        factsBodyOverride = "{\"code\":500,\"message\":\"内部错误\",\"data\":null}";

        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, e.errorCode);
        assertTrue(e.missing.contains("裁决失败"), e.missing);
    }

    @Test
    @DisplayName("route-manifest 未登记 datanet ⇒ 500 fail-loud（禁硬编码基址兜底，X-35）")
    void unresolvedBaseFailsLoud() {
        when(resolver.resolve(anyString(),
                any(com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver.RouteMode.class)))
                .thenReturn(Optional.empty());
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.compute(request()));
        assertEquals(CognitiveErrorCode.E504_INTERNAL, e.errorCode);
        assertTrue(e.getMessage().contains("route-manifest"), e.getMessage());
    }

    @Test
    @DisplayName("按幂等键读：未命中 data=null ⇒ 返回 null（首次运行正常分支，非 404）")
    void idempotencyKeyMissIsNull() {
        route();
        byKeyResponse = "{\"code\":0,\"message\":\"ok\",\"data\":null}";
        assertNull(service.findByIdempotencyKey("idem-new"));
    }

    @Test
    @DisplayName("按幂等键读：命中 ⇒ 带出调用方锚与复用回链（编排面据此判复用/409）")
    void idempotencyKeyHitSurfacesAnchors() {
        route();
        byKeyResponse = "{\"code\":0,\"data\":{\"forecastRunId\":\"run-old\",\"scenarioId\":\"scn-09\","
                + "\"runType\":\"FORECAST\",\"status\":\"SUCCEEDED\",\"caliberId\":\"cal-2026\","
                + "\"caliberVersion\":\"v3\",\"asOfTime\":\"2026-10-01T00:00:00\","
                + "\"snapshotId\":\"snap-77\",\"formulaVersion\":\"f-12\","
                + "\"idempotencyKey\":\"caller-key-1\",\"contentHash\":\"caller-hash-1\","
                + "\"reusedFromRunId\":\"run-base\",\"taskId\":\"task-9\","
                + "\"baselineProfit\":\"1.0000\",\"scenarioProfit\":\"2.0000\"}}";

        Map<String, Object> view = service.findByIdempotencyKey("caller-key-1");

        assertEquals("run-old", view.get("forecastRunId"));
        assertEquals("caller-key-1", view.get("idempotencyKey"));
        assertEquals("caller-hash-1", view.get("contentHash"));
        assertEquals("run-base", view.get("reusedFromRunId"));
        assertEquals("task-9", view.get("taskId"));
        assertEquals(new BigDecimal("2.0000"), view.get("scenarioProfit"));
    }

    @Test
    @DisplayName("定位键空白 ⇒ 400（三条读路径同守卫，禁把空白键交给读通道）")
    void blankLookupKeysAreRejected() {
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class,
                () -> service.findByIdempotencyKey("  "));
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM, e.errorCode);
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM,
                assertThrows(CognitiveGuardException.class, () -> service.findRun("")).errorCode);
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM,
                assertThrows(CognitiveGuardException.class, () -> service.readResults(" ")).errorCode);
        assertEquals(CognitiveErrorCode.E400_INVALID_PARAM,
                assertThrows(CognitiveGuardException.class, () -> service.readEvidence(null)).errorCode);
        verify(snapshotReader, never()).getSnapshotWithLineage(any(SnapshotQuery.class));
    }

    @Test
    @DisplayName("读通道空载荷 ⇒ 502 定位原因（禁 NPE 冒成无定位 500，也禁按空快照造正常视图）")
    void nullPayloadIsDependencyError() {
        when(snapshotReader.getSnapshotWithLineage(any(SnapshotQuery.class))).thenReturn(null);
        CognitiveGuardException e = assertThrows(CognitiveGuardException.class, () -> service.findRun("run-x"));
        assertEquals(CognitiveErrorCode.E502_DEPENDENCY_DOWN, e.errorCode);
        assertTrue(e.missing.contains("空载荷"), e.missing);
    }

    @Test
    @DisplayName("证据集：因果边 evidenceRefs 去重合并 + 六要素锚（后端组装，编排面不拼公式）")
    void evidenceSetIsDeduplicated() {
        RunSnapshot snapshot = new RunSnapshot("run-x", "cal-2026", "v3", "2026-10-01T00:00:00",
                null, null, List.of(),
                List.of(new ContributionEdgeSource("c-1", List.of(), null, null,
                                List.of("ev-1", "ev-2"), null),
                        new ContributionEdgeSource("c-2", List.of(), null, null,
                                List.of("ev-2", " "), null)));
        when(snapshotReader.getSnapshotWithLineage(any(SnapshotQuery.class))).thenReturn(
                new DataEngineForecastRunSnapshotReader.SnapshotWithLineage(snapshot,
                        new DataEngineForecastRunSnapshotReader.SnapshotLineage("run-x", "snap-77",
                                "f-12", null, "scn-09", "FORECAST", "SUCCEEDED", null)));

        Map<String, Object> evidence = service.readEvidence("run-x");

        assertEquals(List.of("ev-1", "ev-2"), evidence.get("evidenceRefs"));
        assertEquals("snap-77", evidence.get("snapshotId"));
        assertEquals("f-12", evidence.get("formulaVersion"));
        assertEquals(2, ((List<?>) evidence.get("evidenceItems")).size());
    }
}
