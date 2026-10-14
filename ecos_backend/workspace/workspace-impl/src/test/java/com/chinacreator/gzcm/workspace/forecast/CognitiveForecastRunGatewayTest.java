package com.chinacreator.gzcm.workspace.forecast;

import com.chinacreator.gzcm.workspace.scenario.ActionValidationException;
import com.chinacreator.gzcm.workspace.scenario.CognitiveEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.RunMode;
import com.chinacreator.gzcm.workspace.security.CallerCredentialRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认知域预测 run 适配器单测（差距分析 P0-4 收口：{@code ForecastRunGateway} 生产实现）。
 *
 * <p>本类断言的是"编排面 ↔ 认知面"的协议边界，逐条钉死：
 * ① <b>只搬不造</b> —— 请求体不含 forecastRunId（run 身份归生产方），幂等锚两列原样下传；
 *    响应里的三值/明细原样映射，缺位保持 null（R-62 ①，禁插值禁补 0）；
 * ② <b>语义可分</b> —— 404/501/400/409/不可达各自落到不同异常，禁把引擎拒绝折叠成 200
 *    或泛化 500（X-44/DG-9）；
 * ③ <b>未命中不是异常</b> —— 幂等键查不到回 null（首次运行正常分支）；
 * ④ <b>定位键空白即 400</b> —— 禁把空键交给引擎（全表扫面）；
 * ⑤ <b>诚实 501</b> —— copyScenario 未上线即抛 {@link ForecastRunNotLiveException}，禁伪回执；
 * ⑥ <b>凭证中继</b> —— 入口带 Authorization/信任头则逐字转发，无请求上下文则不带（fail-closed）。</p>
 */
class CognitiveForecastRunGatewayTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CognitiveForecastRunGateway gateway = new CognitiveForecastRunGateway(restTemplate);

    /** 路径模板 → 200 响应原文（未登记即失败，禁默默返回空）。 */
    private final Map<String, String> okBodies = new LinkedHashMap<>();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void stubOk() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(0);
                    String body = okBodies.get(path);
                    if (body == null) {
                        throw new AssertionError("未登记的路径模板: " + path);
                    }
                    return new ResponseEntity<>(body, HttpStatus.OK);
                });
    }

    private void stubError(HttpStatus status, String body) {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenThrow(HttpClientErrorException.create(status, status.getReasonPhrase(),
                        new HttpHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    private static ForecastRunCommand command() {
        return new ForecastRunCommand("scn-09", "FORECAST", "cal-2026", "v3", "2026-10-01T00:00:00",
                "snap-77", "f-12", "caller-key-1", "caller-hash-1", RunMode.FORMAL, null);
    }

    private static final String RUN_BODY = """
            {"code":0,"message":"ok","data":{
              "forecastRunId":"fc-0001","scenarioId":"scn-09","runType":"FORECAST","status":"SUCCEEDED",
              "caliberId":"cal-2026","caliberVersion":"v3","asOfTime":"2026-10-01T00:00:00",
              "snapshotId":"snap-77","formulaVersion":"f-12",
              "idempotencyKey":"caller-key-1","contentHash":"caller-hash-1",
              "baselineRunId":null,"reusedFromRunId":null,"taskId":null,"degraded":false,
              "evidenceRefs":["ev-1"],"quantile":{"p10":null,"p50":null,"p90":null},
              "baselineProfit":"1000000.0000","scenarioProfit":"1060000.0000"}}""";

    // ── ① 只搬不造 ───────────────────────────────────────────────

    @Test
    @DisplayName("submit：请求体带调用方幂等锚、不带 forecastRunId、带 traceId；视图字段原样映射")
    @SuppressWarnings("unchecked")
    void submitRelaysCallerAnchorsAndMapsView() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.COMPUTE_PATH, RUN_BODY);

        ForecastRunView view = gateway.submit(command());

        ArgumentCaptor<HttpEntity<String>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(CognitiveForecastRunGateway.COMPUTE_PATH), eq(HttpMethod.POST),
                entity.capture(), eq(String.class), ArgumentMatchers.<String, Object>anyMap());
        Map<String, Object> body = parseObject(entity.getValue().getBody());
        assertFalse(body.containsKey("forecastRunId"), "run 身份归生产方，编排面禁造: " + body.keySet());
        assertEquals("caller-key-1", body.get("idempotencyKey"));
        assertEquals("caller-hash-1", body.get("contentHash"));
        assertEquals("scn-09", body.get("scenarioId"));
        assertEquals("snap-77", body.get("snapshotId"));
        assertNotNull(body.get("traceId"), "写通道四必带项之一");

        assertEquals("fc-0001", view.getForecastRunId());
        assertEquals(ForecastRunStatus.SUCCEEDED, view.getStatus());
        assertEquals("caller-key-1", view.getIdempotencyKey());
        assertEquals(List.of("ev-1"), view.getEvidenceRefs());
        assertFalse(view.isDegraded());
        // 三值缺位保持 null（禁适配器补值）
        assertNull(view.getQuantile().getP10());
        assertNull(view.getQuantile().getP50());
        assertNull(view.getQuantile().getP90());
    }

    @Test
    @DisplayName("submit：引擎回执无 run 身份 ⇒ 判定落点未确认，禁按命令里的键造视图冒充成功")
    void submitWithoutRunIdentityFailsLoud() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.COMPUTE_PATH,
                "{\"code\":0,\"message\":\"ok\",\"data\":{\"status\":\"SUCCEEDED\"}}");

        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.submit(command()));
    }

    @Test
    @DisplayName("results：明细数值走 BigDecimal 原文（不经 double，金额 scale 不失真）")
    void resultsKeepDecimalPrecision() {
        stubOk();
        // 引擎侧 BigDecimal 经 Spring Jackson 序列化为 JSON number（非字符串），此处按真实形态构造
        okBodies.put(CognitiveForecastRunGateway.RESULTS_PATH, """
                {"code":0,"data":{"forecastRunId":"fc-0001",
                 "cells":[{"projectId":"P-1","period":"202601",
                           "baseline":100000.0000,"scenario":160000.0000}],
                 "baselineProfit":1000000.0000,"scenarioProfit":1060000.0000,
                 "p10":null,"p50":null,"p90":null,"quantileSource":"NOT_AVAILABLE"}}""");

        Map<String, Object> results = gateway.readResults("fc-0001");

        assertEquals("1060000.0000", String.valueOf(results.get("scenarioProfit")),
                "金额经 double 通道会丢 scale（B-2 禁适配器改动数值一个比特）");
        assertNull(results.get("p50"));
        assertEquals("NOT_AVAILABLE", results.get("quantileSource"));
        List<?> cells = (List<?>) results.get("cells");
        assertEquals(1, cells.size());
        Map<String, Object> cell = castMap(cells.get(0));
        assertEquals(new BigDecimal("160000.0000"), cell.get("scenario"));
        assertEquals("202601", cell.get("period"));
    }

    @Test
    @DisplayName("evidence：六要素锚 + 引用集 + 明细项映射；空载荷回 null 交编排面转未上线")
    void evidenceMapsSixFactorsAndRefs() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.EVIDENCE_PATH, """
                {"code":0,"data":{"forecastRunId":"fc-0001","caliberId":"cal-2026","caliberVersion":"v3",
                 "asOfTime":"2026-10-01T00:00:00","snapshotId":"snap-77","formulaVersion":"f-12",
                 "evidenceRefs":["ev-1","ev-2"],
                 "evidenceItems":[{"evidenceRef":"ev-1","chainId":"c-1","baseline":"1.00","scenario":"2.00"}]}}""");

        ForecastRunEvidence evidence = gateway.readEvidence("fc-0001");

        assertEquals("snap-77", evidence.getSnapshotId());
        assertEquals("f-12", evidence.getFormulaVersion());
        assertEquals(List.of("ev-1", "ev-2"), evidence.getEvidenceRefs());
        assertEquals(1, evidence.getEvidenceItems().size());
        assertEquals("c-1", evidence.getEvidenceItems().get(0).get("chainId"));

        okBodies.put(CognitiveForecastRunGateway.EVIDENCE_PATH, "{\"code\":0,\"data\":null}");
        assertNull(gateway.readEvidence("fc-0001"), "无证据载荷禁造空证据冒充有证据");
    }

    // ── ② 语义可分 ───────────────────────────────────────────────

    @Test
    @DisplayName("引擎 404 ⇒ ForecastRunNotFoundException（run 不存在，与未上线严格分开）")
    void engineNotFoundBecomesRunNotFound() {
        stubError(HttpStatus.NOT_FOUND, "{\"code\":404,\"message\":\"x\",\"data\":{\"missing\":\"run-x\"}}");
        ForecastRunNotFoundException e = assertThrows(ForecastRunNotFoundException.class,
                () -> gateway.findRun("run-x"));
        assertTrue(e.getMessage().contains("run-x"), e.getMessage());
    }

    @Test
    @DisplayName("引擎 501 / stub / ECOS-COG-503 ⇒ ForecastRunNotLiveException（未上线语义不被折叠）")
    void engineStubBecomesNotLive() {
        for (String body : new String[]{
                "{\"code\":501,\"message\":\"not implemented\",\"data\":{\"stub\":true}}",
                "{\"errorCode\":\"ECOS-COG-503\",\"data\":{\"stub\":true}}"}) {
            stubError(HttpStatus.NOT_IMPLEMENTED, body);
            assertThrows(ForecastRunNotLiveException.class, () -> gateway.findRun("run-x"));
        }
        stubError(HttpStatus.NOT_IMPLEMENTED, "{\"code\":501,\"message\":\"未上线\",\"data\":{}}");
        assertThrows(ForecastRunNotLiveException.class, () -> gateway.findRun("run-x"));
    }

    @Test
    @DisplayName("引擎 400/422 ⇒ ForecastRunRejectedException 且状态码原样保住（缺参不被泛化成 500）")
    void engineBadRequestKeepsStatusAndReason() {
        stubError(HttpStatus.BAD_REQUEST,
                "{\"code\":400,\"errorCode\":\"ECOS-COG-400P\",\"message\":\"无事实禁造产物\","
                        + "\"data\":{\"missing\":\"facts\"}}");
        ForecastRunRejectedException e = assertThrows(ForecastRunRejectedException.class,
                () -> gateway.submit(command()));
        assertEquals(400, e.getHttpStatus(), "BusinessException 会把状态折叠成 400/500，禁用它承载引擎语义");
        assertEquals(ForecastRunRejectedException.CODE_REJECTED, e.getCode());
        assertTrue(e.getMessage().contains("无事实禁造产物"), e.getMessage());
        assertEquals("ECOS-COG-400P", e.getDetails().get("engineErrorCode"));
        assertEquals("facts", e.getDetails().get("missing"));

        stubError(HttpStatus.UNPROCESSABLE_ENTITY, "{\"code\":422,\"message\":\"口径不匹配\"}");
        ForecastRunRejectedException unprocessable = assertThrows(ForecastRunRejectedException.class,
                () -> gateway.submit(command()));
        assertEquals(422, unprocessable.getHttpStatus());
        assertTrue(unprocessable.getMessage().contains("口径不匹配"), unprocessable.getMessage());
    }

    @Test
    @DisplayName("引擎 401/403 ⇒ 状态保真的拒绝/权限异常（凭证中继后被拒不谎报为不可用）")
    void engineDeniedKeepsDeniedStatus() {
        stubError(HttpStatus.FORBIDDEN, "{\"code\":403,\"message\":\"准入裁决拒绝\"}");
        ForbiddenOperationException denied = assertThrows(ForbiddenOperationException.class,
                () -> gateway.submit(command()));
        assertEquals(403, denied.getHttpStatus());
        assertEquals("PERMISSION_DENIED", denied.getCode());

        stubError(HttpStatus.UNAUTHORIZED, "{\"code\":401,\"message\":\"未认证\"}");
        ForecastRunRejectedException unauthenticated = assertThrows(ForecastRunRejectedException.class,
                () -> gateway.submit(command()));
        assertEquals(401, unauthenticated.getHttpStatus());
        assertEquals(ForecastRunRejectedException.CODE_UNAUTHENTICATED, unauthenticated.getCode());
        assertTrue(unauthenticated.getMessage().contains("未认证"), unauthenticated.getMessage());
    }

    @Test
    @DisplayName("引擎 409 ⇒ 409 领域异常并带引擎码（幂等冲突不被吞成 500）")
    void engineConflictBecomesConflict() {
        stubError(HttpStatus.CONFLICT,
                "{\"code\":409,\"message\":\"同键异指纹\",\"errorCode\":\"ECOS-COG-409R\"}");
        ForecastRunRejectedException e = assertThrows(ForecastRunRejectedException.class,
                () -> gateway.submit(command()));
        assertEquals(409, e.getHttpStatus());
        assertEquals(ForecastRunRejectedException.CODE_CONFLICT, e.getCode());
        assertEquals("ECOS-COG-409R", e.getDetails().get("engineErrorCode"));
        assertTrue(e.getMessage().contains("同键异指纹"), e.getMessage());
    }

    @Test
    @DisplayName("连不上 / 非 JSON / code!=0 / 空体 ⇒ 503 认知引擎不可用（禁降级为空数据）")
    void transportAndVerdictFailuresAreUnavailable() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenThrow(new ResourceAccessException("connect refused"));
        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.findRun("run-x"));

        stubOk();
        okBodies.put(CognitiveForecastRunGateway.RUN_PATH, "{\"code\":500,\"message\":\"内部错误\"}");
        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.findRun("run-x"));

        okBodies.put(CognitiveForecastRunGateway.RUN_PATH, "not-json");
        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.findRun("run-x"));

        okBodies.put(CognitiveForecastRunGateway.RUN_PATH, "");
        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.findRun("run-x"));

        when(restTemplate.exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap()))
                .thenThrow(new RestClientException("converter missing"));
        assertThrows(CognitiveEngineUnavailableException.class, () -> gateway.findRun("run-x"));
    }

    // ── ③ 幂等未命中 ─────────────────────────────────────────────

    @Test
    @DisplayName("幂等键查不到：data=null 与 404 都按未命中回 null（首次运行正常分支）")
    void idempotencyMissIsNotAnError() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.BY_IDEMPOTENCY_KEY_PATH, "{\"code\":0,\"data\":null}");
        assertNull(gateway.findByIdempotencyKey("idem-new"));

        okBodies.put(CognitiveForecastRunGateway.BY_IDEMPOTENCY_KEY_PATH, RUN_BODY);
        ForecastRunView hit = gateway.findByIdempotencyKey("caller-key-1");
        assertEquals("fc-0001", hit.getForecastRunId());
        assertEquals("caller-key-1", hit.getIdempotencyKey());

        stubError(HttpStatus.NOT_FOUND, "{\"code\":404,\"message\":\"路由不存在\"}");
        assertNull(gateway.findByIdempotencyKey("idem-new"));
    }

    @Test
    @DisplayName("findRun 命中 ⇒ 复用回链/任务键透出（缺位保持 null，禁伪造回填）")
    void findRunSurfacesLineageFields() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.RUN_PATH,
                RUN_BODY.replace("\"reusedFromRunId\":null", "\"reusedFromRunId\":\"run-base\"")
                        .replace("\"taskId\":null", "\"taskId\":\"task-9\""));

        ForecastRunView view = gateway.findRun("fc-0001");

        assertEquals("run-base", view.getReusedFromRunId());
        assertEquals("task-9", view.getTaskId());
        assertNull(view.getQuantile().getP10());
    }

    // ── ④ 定位键守卫 ─────────────────────────────────────────────

    @Test
    @DisplayName("空白定位键 ⇒ 400（ACTION_FIELDS_MISSING）且不出网（禁把空键交给引擎做全表扫）")
    void blankLookupKeysNeverLeaveTheProcess() {
        for (String blank : new String[]{null, "", "   "}) {
            assertThrows(ActionValidationException.class, () -> gateway.findRun(blank));
            assertThrows(ActionValidationException.class, () -> gateway.findByIdempotencyKey(blank));
            assertThrows(ActionValidationException.class, () -> gateway.readResults(blank));
            assertThrows(ActionValidationException.class, () -> gateway.readEvidence(blank));
        }
        verify(restTemplate, org.mockito.Mockito.never())
                .exchange(anyString(), any(HttpMethod.class), nullable(HttpEntity.class),
                        eq(String.class), ArgumentMatchers.<String, Object>anyMap());
    }

    // ── ⑤ 诚实 501 ───────────────────────────────────────────────

    @Test
    @DisplayName("copyScenario 未上线 ⇒ ForecastRunNotLiveException 且点名属主缺口（禁伪回执）")
    void scenarioCopyIsHonestStub() {
        ForecastRunNotLiveException e = assertThrows(ForecastRunNotLiveException.class,
                () -> gateway.copyScenario("run-src", Map.of()));
        assertTrue(e.getTechnicalReason().contains("FC-03"), e.getTechnicalReason());
        assertTrue(e.getTechnicalReason().contains("run-src"), e.getTechnicalReason());
    }

    // ── ⑥ 传输纪律与凭证 ─────────────────────────────────────────

    @Test
    @DisplayName("出站头：JSON Accept + 入口凭证逐字中继（无请求上下文则不带，fail-closed 不变）")
    void headersCarryJsonAcceptAndRelayedCredentials() {
        stubOk();
        okBodies.put(CognitiveForecastRunGateway.COMPUTE_PATH, RUN_BODY);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-from-entry");
        request.addHeader("X-ECOS-USER", "u-1");
        request.addHeader("X-ECOS-ROLE", "r-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        gateway.submit(command());

        ArgumentCaptor<HttpEntity<String>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entity.capture(),
                eq(String.class), ArgumentMatchers.<String, Object>anyMap());
        HttpHeaders headers = entity.getValue().getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        assertTrue(headers.getAccept().contains(MediaType.APPLICATION_JSON), headers.getAccept().toString());
        assertEquals("Bearer jwt-from-entry", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("u-1", headers.getFirst("X-ECOS-USER"));
        assertEquals("r-1", headers.getFirst("X-ECOS-ROLE"));
        assertNull(headers.getFirst("X-ECOS-TENANT"), "入口未带的信任头禁凭空补");

        RequestContextHolder.resetRequestAttributes();
        okBodies.put(CognitiveForecastRunGateway.COMPUTE_PATH, RUN_BODY);
        gateway.submit(command());
        ArgumentCaptor<HttpEntity<String>> anonymous = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(anyString(), eq(HttpMethod.POST),
                anonymous.capture(), eq(String.class), ArgumentMatchers.<String, Object>anyMap());
        assertNull(anonymous.getAllValues().get(1).getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                "无请求上下文禁伪造凭证");
    }

    @Test
    @DisplayName("CallerCredentialRelay：headers 为 null 即空操作，不抛（工具面自守）")
    void relayToleratesNullHeaders() {
        CallerCredentialRelay.applyTo(null);
        HttpHeaders headers = new HttpHeaders();
        CallerCredentialRelay.applyTo(headers);
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    // ── 工具 ─────────────────────────────────────────────────────

    private static Map<String, Object> parseObject(String raw) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AssertionError("出站请求体不可解析: " + raw, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
