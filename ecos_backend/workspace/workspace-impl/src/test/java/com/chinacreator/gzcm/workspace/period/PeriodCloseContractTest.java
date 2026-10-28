package com.chinacreator.gzcm.workspace.period;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.workspace.exception.PeriodCloseForwardException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 附录A 第 34 条契约面验收（#146y）—— {@code POST /api/v1/workspace/periods/{yyyMM}/close}。
 *
 * <p>本类钉死「薄承流」的四条可证伪判据，逐条对应真实缺陷形态：
 * ① <b>契约路径逐字</b> —— 类级前缀 + 方法级模板拼接等于详设 line 8268/附录A 第 34 条原文；
 *    属主侧出站 URI 由 MONOLITH 基址 + {@code /api/v1} 根 + 相对段 + 路径变量展开拼成，
 *    由 {@link #ownerLegUsesMonolithApiV1RootAndExpandsPathVariable} 的 {@code requestTo} 实证
 *    （前导斜杠拼接不靠注释成立）。
 * ② <b>属主语义不被折叠</b> —— 400/403/409/422/503 各保状态行 + 保属主机读码，禁被
 *    {@code WorkspaceExceptionHandler#handleDownstream} 洗成 500 {@code "INTERNAL"}
 *    （§4.7.2「不可用 ≠ 无权限」：403 DENY 与 503 UNEVALUATED 必须可分辨）。
 * ③ <b>拒绝不许冒充成功</b> —— HTTP 200 但信封 {@code code != 0}、缺 {@code data}、空/非 JSON
 *    应答一律 fail-loud；幂等回放只认属主 {@code alreadyClosed=true}，禁由「无回执」推定已关账。
 * ④ <b>凭证逐字中继、原文不外泄</b> —— 入口 {@code Authorization} + 四枚 {@code X-ECOS-*}
 *    信任头原样下传（{@code CallerCredentialRelay} 单源），无请求上下文即不带（不伪造主语）；
 *    下游原文只进日志面，details 只留三枚可机读键（X-41）。</p>
 *
 * <p>夹具字段面严格取属主 {@code PeriodCloseResultVO}（period/status/closedBy/closedAt/
 * alreadyClosed/reason）与 {@code PeriodCloseRequestVO}（reason/detailJson），不虚构第三套字段。</p>
 */
class PeriodCloseContractTest {

    /** MONOLITH 基址占位（真实值由 route-manifest 解析，本类只验拼接形状）。 */
    private static final String BASE = "http://monolith.invalid:8080/api/v1";
    private static final String OWNER_URL = "http://monolith.invalid:8080/api/v1/datanet/facts/periods/202501/close";
    private static final String PERIOD = "202501";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PeriodCloseGateway gateway;

    private final WorkspaceExceptionHandler advice = new WorkspaceExceptionHandler();

    @BeforeEach
    void bindServer() {
        restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(BASE));
        server = MockRestServiceServer.bindTo(restTemplate).build();
        gateway = new PeriodCloseGateway(restTemplate);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── ① 契约路径 ────────────────────────────────────

    @Test
    @DisplayName("第 34 条契约路径逐字：类级前缀 + 方法级模板 = 详设原文")
    void contractPathLiteralIsExact() {
        String classPrefix = PeriodCloseContractController.class.getAnnotation(RequestMapping.class).value()[0];
        String methodTemplate = java.util.Arrays
                .stream(PeriodCloseContractController.class.getDeclaredMethods())
                .filter(m -> "closePeriod".equals(m.getName()))
                .findFirst().orElseThrow()
                .getAnnotation(PostMapping.class).value()[0];

        assertEquals("/api/v1/workspace/periods", classPrefix);
        assertTrue(classPrefix.startsWith("/") && !classPrefix.endsWith("/"),
                "类级前缀斜杠起始、斜杠不结尾（拼接口径＝前缀 + 以斜杠起始的方法级模板）");
        assertEquals("/{yyyMM}/close", methodTemplate);
        assertTrue(methodTemplate.startsWith("/") && !methodTemplate.startsWith("//"),
                "方法级必须是斜杠起始的相对模板，绝对路径会拼成双前缀");
        String joined = classPrefix + methodTemplate;
        assertFalse(joined.contains("//"), "拼接出双斜杠＝运行时必然 404");
        assertEquals("/api/v1/workspace/periods/{yyyMM}/close", joined);
    }

    @Test
    @DisplayName("属主腿出站 URI：MONOLITH 基址 + /api/v1 根 + 相对段 + 路径变量展开")
    void ownerLegUsesMonolithApiV1RootAndExpandsPathVariable() {
        server.expect(requestTo(OWNER_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(ownerEnvelope(ownerRow(false)), MediaType.APPLICATION_JSON));

        gateway.close(PERIOD, Map.of("reason", "月度结账"));
        server.verify();
    }

    @Test
    @DisplayName("请求体逐字段搬运：属主 VO 键面原样下传，detailJson 保持字符串不被二次嵌套")
    void requestBodyIsForwardedVerbatim() {
        // 严格比对（strict=true）＝键面不加不减；detailJson 值是字符串，若本层把它解析成对象再序列化，
        // JSON 树比对立刻变红（"string" ≠ object）
        String expected = "{\"reason\":\"月度结账\",\"detailJson\":\"{\\\"operatorNote\\\":\\\"月结\\\"}\"}";
        server.expect(requestTo(OWNER_URL)).andExpect(content().json(expected, true))
                .andRespond(withSuccess(ownerEnvelope(ownerRow(false)), MediaType.APPLICATION_JSON));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "月度结账");
        body.put("detailJson", "{\"operatorNote\":\"月结\"}");
        gateway.close(PERIOD, body);
        server.verify();
    }

    @Test
    @DisplayName("凭证逐字中继：Authorization 与四枚 X-ECOS-* 信任头原样下传")
    void callerCredentialsAreRelayedVerbatim() {
        givenRequestContext();
        server.expect(requestTo(OWNER_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-period-close"))
                .andExpect(header(HeaderAuthInterceptor.H_USER, "u-close-1"))
                .andExpect(header(HeaderAuthInterceptor.H_ROLE, "finance_admin"))
                .andExpect(header(HeaderAuthInterceptor.H_TENANT, "t-ecos"))
                .andExpect(header(HeaderAuthInterceptor.H_DOMAIN, "d-finance"))
                .andRespond(withSuccess(ownerEnvelope(ownerRow(false)), MediaType.APPLICATION_JSON));

        gateway.close(PERIOD, Map.of("reason", "月度结账"));
        server.verify();
    }

    @Test
    @DisplayName("无请求上下文即不带凭证：不伪造主语，交由属主侧 fail-closed 裁决")
    void absentRequestContextRelaysNoIdentity() {
        RequestContextHolder.resetRequestAttributes();
        server.expect(requestTo(OWNER_URL)).andExpect(request -> {
            HttpHeaders sent = request.getHeaders();
            assertNull(sent.get(HttpHeaders.AUTHORIZATION), "无入口上下文时禁止伪造 Authorization");
            assertNull(sent.get(HeaderAuthInterceptor.H_USER), "无入口上下文时禁止伪造 X-ECOS-USER");
            assertNull(sent.get(HeaderAuthInterceptor.H_ROLE), "无入口上下文时禁止伪造 X-ECOS-ROLE");
        }).andRespond(withSuccess(ownerEnvelope(ownerRow(false)), MediaType.APPLICATION_JSON));

        gateway.close(PERIOD, null);
        server.verify();
    }

    // ── ② 属主语义保真 ────────────────────────────────

    @Test
    @DisplayName("幂等回放：属主 alreadyClosed=true 原样透出，禁由「无回执」推定成功")
    void idempotentReplayKeepsAlreadyClosedTrue() {
        server.expect(requestTo(OWNER_URL))
                .andRespond(withSuccess(ownerEnvelope(ownerRow(true)), MediaType.APPLICATION_JSON));

        Map<String, Object> row = gateway.close(PERIOD, Map.of("reason", "重复提交"));

        assertEquals(Boolean.TRUE, row.get("alreadyClosed"), "alreadyClosed 必须是布尔真值而非字符串");
        assertEquals("CLOSED", row.get("status"));
        server.verify();
    }

    @Test
    @DisplayName("属主 400 ECOS-DATA-023：状态行与机读码逐字透出")
    void malformedPeriodKeepsOwnerBadRequest() {
        // 畸形期间原样出站（本层不归一、不预校验，否则造出第二套口径），由属主判 400
        ownerRejection("2025-01", HttpStatus.BAD_REQUEST, "ECOS-DATA-023", "期间形态非法");

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close("2025-01", Map.of()));
        assertCarried(ex, 400, "ECOS-DATA-023");
        assertAdviceKeepsStatus(ex, 400, "ECOS-DATA-023");
    }

    @Test
    @DisplayName("属主 403 ECOS-DATA-031：保 403，禁被 handleDownstream 洗成 500 INTERNAL")
    void permissionDenialKeepsOwnerStatusAndMachineCode() {
        ownerRejection(HttpStatus.FORBIDDEN, "ECOS-DATA-031", "OPA DENY: role=viewer");

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of("reason", "月度结账")));
        assertCarried(ex, 403, "ECOS-DATA-031");

        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDomain(ex);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), "关账权限拒绝不得折叠成 500");
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body);
        assertEquals(403, body.getCode());
        assertEquals("ECOS-DATA-031", body.getErrorCode());
        assertFalse(body.getMessage().contains("viewer"), "下游裁决原文不得进入响应体 message（X-41）");
        assertEquals(Set.of("engineHttpStatus", "engineErrorCode", "engine"),
                ((Map<?, ?>) body.getData()).keySet(), "details 只许三枚可机读键，下游原文不进载荷");
    }

    @Test
    @DisplayName("属主 409 关账期封闭：冲突不被归一成 400/500")
    void conflictIsNotFoldedIntoGenericFailure() {
        ownerRejection(HttpStatus.CONFLICT, "PERIOD_ALREADY_CLOSED", "期间已进入关账封闭期");

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertCarried(ex, 409, "PERIOD_ALREADY_CLOSED");
        assertAdviceKeepsStatus(ex, 409, "PERIOD_ALREADY_CLOSED");
    }

    @Test
    @DisplayName("属主 503 ECOS-SEC-501（未评估）：与 403 无权限分态，禁互相冒充")
    void securityUnevaluatedStaysUnavailableNotForbidden() {
        ownerRejection(HttpStatus.SERVICE_UNAVAILABLE, "ECOS-SEC-501", "security-engine UNEVALUATED");

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertCarried(ex, 503, "ECOS-SEC-501");
        assertAdviceKeepsStatus(ex, 503, "ECOS-SEC-501");
    }

    @Test
    @DisplayName("属主应答无 errorCode：状态仍透出，机读码落本域中性词而非臆造")
    void ownerRejectionWithoutMachineCodeFallsBackToNeutralCode() {
        server.expect(requestTo(OWNER_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"code\":422,\"message\":\"不可处理\"}")
                .contentType(MediaType.APPLICATION_JSON));

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertCarried(ex, 422, PeriodCloseForwardException.CODE_FORWARD_FAILED, "");
    }

    // ── ③ 拒绝不许冒充成功 ────────────────────────────

    @Test
    @DisplayName("HTTP 200 但信封 code!=0：fail-loud，禁把业务失败当关账回执搬运")
    void ownerEnvelopeRejectionIsNotSilentSuccess() {
        server.expect(requestTo(OWNER_URL)).andRespond(withSuccess(
                "{\"code\":-1,\"errorCode\":\"ECOS-DATA-031\",\"message\":\"裸回信封拒绝\"}",
                MediaType.APPLICATION_JSON));

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertEquals(503, ex.getHttpStatus(), "属主拒绝且回执不可判定 ⇒ 不可确认态，禁报成功");
        assertEquals("ECOS-DATA-031", ex.getCode(), "属主机读码必须保留");
    }

    @Test
    @DisplayName("信封 code=0 但缺 data 段：回执不可判定，禁冒充已关账")
    void missingDataSegmentIsNotReplaySuccess() {
        server.expect(requestTo(OWNER_URL))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertCarried(ex, 503, PeriodCloseForwardException.CODE_UNAVAILABLE,
                PeriodCloseForwardException.CODE_UNAVAILABLE);
    }

    @Test
    @DisplayName("空响应体／非 JSON 响应体：一律不可确认，禁按空数据成功")
    void blankOrNonJsonBodyIsNotSuccess() {
        // 两枚期望先登记再发请求：MockRestServiceServer 在实际请求发出后不允许追加期望
        server.expect(requestTo(OWNER_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        server.expect(requestTo(OWNER_URL))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML));

        PeriodCloseForwardException blank = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertEquals(PeriodCloseForwardException.CODE_UNAVAILABLE, blank.getCode());

        PeriodCloseForwardException nonJson = assertThrows(PeriodCloseForwardException.class,
                () -> gateway.close(PERIOD, Map.of()));
        assertEquals(PeriodCloseForwardException.CODE_UNAVAILABLE, nonJson.getCode());
        server.verify();
    }

    @Test
    @DisplayName("传输层不可达（IOException）：503 DATANET_UNAVAILABLE，禁报 403 或假成功")
    void transportFailureReportsUnavailableNotForbidden() {
        RestTemplate unreachable = new RestTemplate(new SimpleClientHttpRequestFactory() {
            @Override
            public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
                throw new IOException("simulated connection refused");
            }
        });

        PeriodCloseForwardException ex = assertThrows(PeriodCloseForwardException.class,
                () -> new PeriodCloseGateway(unreachable).close(PERIOD, Map.of("reason", "月度结账")));
        assertCarried(ex, 503, PeriodCloseForwardException.CODE_UNAVAILABLE,
                PeriodCloseForwardException.CODE_UNAVAILABLE);
        assertAdviceKeepsStatus(ex, 503, PeriodCloseForwardException.CODE_UNAVAILABLE);
    }

    // ── ④ 搬运形状 ────────────────────────────────────

    @Test
    @DisplayName("缺位字段保持键位与 null 值：禁默认值冒充")
    void absentFieldsStayNull() {
        String envelope = "{\"code\":0,\"data\":{\"period\":\"202501\",\"status\":\"CLOSED\","
                + "\"closedBy\":null,\"closedAt\":null,\"alreadyClosed\":false,\"reason\":null}}";
        server.expect(requestTo(OWNER_URL)).andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        Map<String, Object> row = gateway.close(PERIOD, Map.of());

        assertTrue(row.containsKey("closedBy"), "属主字段面缺位也要保留键位");
        assertNull(row.get("closedBy"));
        assertNull(row.get("reason"));
        assertEquals(Boolean.FALSE, row.get("alreadyClosed"));
        assertEquals(List.of("period", "status", "closedBy", "closedAt", "alreadyClosed", "reason"),
                new ArrayList<>(row.keySet()), "搬运面保持属主 VO 顺序，不加不减");
        server.verify();
    }

    // ── 夹具与判据助手 ────────────────────────────────

    private void ownerRejection(HttpStatus status, String errorCode, String ownerMessage) {
        ownerRejection(PERIOD, status, errorCode, ownerMessage);
    }

    /**
     * 属主拒绝夹具，按 {@code period} 登记出站 URI。
     *
     * <p>{@code period} 参数化是有意的：本层不做期间归一也不预校验合法性，
     * 所以「2025-01」这种畸形值也必须原样出现在属主腿 URI 上；
     * 若哪天本层偷偷加了归一，这条 expectation 会因 URI 不等而立刻变红。</p>
     */
    private void ownerRejection(String period, HttpStatus status, String errorCode, String ownerMessage) {
        server.expect(requestTo(ownerUrl(period))).andRespond(withStatus(status)
                .body("{\"code\":" + status.value() + ",\"errorCode\":\"" + errorCode
                        + "\",\"message\":\"" + ownerMessage + "\"}")
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static String ownerUrl(String period) {
        return "http://monolith.invalid:8080/api/v1/datanet/facts/periods/" + period + "/close";
    }

    /** 字段面严格等于属主 {@code PeriodCloseResultVO}。 */
    private static Map<String, Object> ownerRow(boolean alreadyClosed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", PERIOD);
        row.put("status", "CLOSED");
        row.put("closedBy", "u-close-1");
        row.put("closedAt", "2026-10-29T09:00:00");
        row.put("alreadyClosed", alreadyClosed);
        row.put("reason", "月度结账");
        return row;
    }

    private static String ownerEnvelope(Map<String, Object> row) {
        List<String> parts = new ArrayList<>();
        row.forEach((k, v) -> parts.add("\"" + k + "\":" + jsonValue(v)));
        return "{\"code\":0,\"data\":{" + String.join(",", parts) + "}}";
    }

    private static String jsonValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean b) {
            return b.toString();
        }
        return "\"" + v + "\"";
    }

    private static void givenRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-period-close");
        request.addHeader(HeaderAuthInterceptor.H_USER, "u-close-1");
        request.addHeader(HeaderAuthInterceptor.H_ROLE, "finance_admin");
        request.addHeader(HeaderAuthInterceptor.H_TENANT, "t-ecos");
        request.addHeader(HeaderAuthInterceptor.H_DOMAIN, "d-finance");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void assertCarried(PeriodCloseForwardException ex, int status, String code) {
        assertCarried(ex, status, code, code);
    }

    /**
     * 载体保真判据：状态行、机读码、details 三键（{@code engine} 归因必须落 data-engine）。
     *
     * @param expectedEngineErrorCode 属主未给机读码时为中性空串，与 {@code code} 分列
     */
    private static void assertCarried(PeriodCloseForwardException ex, int status, String code,
                                      String expectedEngineErrorCode) {
        assertEquals(status, ex.getHttpStatus(), "携带的 HTTP 状态行必须等于属主状态");
        assertEquals(code, ex.getCode(), "携带的机读码必须等于属主码／本域中性码");
        Map<String, Object> details = ex.detailView();
        assertEquals(Set.of("engineHttpStatus", "engineErrorCode", "engine"), details.keySet());
        assertEquals(status, details.get("engineHttpStatus"));
        assertEquals(expectedEngineErrorCode, details.get("engineErrorCode"));
        assertEquals("datanet", details.get("engine"), "归因必须落在 data-engine 属主，不得误指认知/安全引擎");
        assertNotNull(ex.getTechnicalReason(), "下游原文须可取证（只进日志面）");
    }

    private void assertAdviceKeepsStatus(PeriodCloseForwardException ex, int status, String code) {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = advice.handleDomain(ex);
        assertEquals(HttpStatus.valueOf(status), resp.getStatusCode());
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body);
        assertEquals(status, body.getCode());
        assertEquals(code, body.getErrorCode());
        assertInstanceOf(Map.class, body.getData(), "details 必须落到 data 段供机器消费");
    }
}
