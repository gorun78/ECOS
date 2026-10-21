package com.chinacreator.gzcm.engine.data.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * data-engine 查询通道的 RLS 四态分流门禁（#142/B4-3d-5，差距 D-B4-24 消费侧 ＋ §4.5.4 分态 D-B4-26；
 * HTTP 状态行保真腿由 #142/B4-3e-18 收口差距 <b>D-B4-59</b>）。
 *
 * <p><b>钉的是本批真正修掉的两个缺陷</b>：① 本通道此前读 {@code /rls/apply} 应答的<b>表达面</b>
 * {@code predicate.predicateTemplate}，恒真策略（{@code 1=1}＝明确「不限制行」）被它判成
 * 「无有效谓词」直接 403 —— 四态里的 ALLOW_ALL 在这一通道凭空消失；② 上下文未解析时表达面的
 * 参数表带 {@code null}，本通道把 {@code col = ?} 绑上 {@code null} 下推，结果「恰好零行」，
 * 语义却是「未知」而非「拒绝」——把 D-B4-23 伪装成「该用户没有数据」。
 * 现在读数经 {@code RlsExecutionContract.read} 单源，四态分流，不可用态走 503
 * {@code ECOS-SEC-501}（与「无权限」的 403 在对外形态上不可能同值）。</p>
 *
 * <p><b>#142/B4-3e-18 扩的判据（D-B4-59 的 HTTP 保真腿）</b>：收口前本方法返回裸
 * {@code ApiResponse}，Spring 侧状态行恒 <b>HTTP 200</b> ⇒ 分态只活在信封里，
 * 403／503 在传输层同形、且都被 {@code res.ok} 读成成功（与
 * {@code DataBridgeHttpStatusFidelityTest} 钉住的 gateway 域桥缺陷同根）。
 * 现在<b>每一态都同时钉两个面</b>：状态行（{@code getStatusCode()}）与信封（{@code code}／
 * {@code errorCode}），并做「态间必异」的对照断言。只钉信封会留缺口——把 {@code ResponseEntity}
 * 摘回裸 {@code ApiResponse} 时信封一字未改、本类若只看信封就全绿，正是原缺陷能长期存在的形态原因。</p>
 *
 * <p><b>驱动方式</b>：本类用<b>生产者真实应答的线上形态</b>
 * （{@code {code:"0", data:{whereClause:…, whereParams:…}}}）驱动 Controller，断言的是
 * 「{@code QueryExecutionService} 实际收到了什么 SQL 与什么参数表／根本没收到」，
 * 不是对源码正则的自证。夹具刻意写键名<b>字面量</b>而非 {@code RlsExecutionContract.KEY_*}：
 * 线上契约的键面漂移（D-B4-17 的根因）只有用真实键面才测得出来——若夹具与读侧共用同一常量，
 * 两侧一起改名时本类会恒绿。</p>
 *
 * <p><b>范围边界（如实登记，不冒充）</b>：本类只钉「请求带 {@code table_name}」时的四态分流；
 * 不带 {@code table_name} 的裸 SQL 面由 {@code QueryExecutorBindingTest}（值不入 SQL 文本）
 * 与 {@code RlsInjectionGuardArchTest}（禁裸拼谓词）守，不在本批改判据。
 * 「{@code table_name} 为空即整条 RLS 不跑」这一<b>触发条件覆盖面</b>不在本批修法内，
 * 已按实测另立差距 <b>D-B4-63</b> 在册。成功面与非安全类失败面（{@code badRequest}／
 * {@code internalError}）有意保持 HTTP 200（不扩面），余下裸回面由
 * {@code SecurityDenialHttpStatusRatchetTest} 量化为只减不增基线。
 * {@code restTemplate} 是 {@code private final} 且无测试接缝，用反射注入而非为此改生产签名
 * （API 只增不改），注入失败即抛——禁「注入不上就跳过」的假绿。</p>
 */
@DisplayName("B4-3d-5／B4-3e-18：data-engine 查询通道 —— RLS 四态分流、参数化注入与 HTTP 状态行保真")
class QueryControllerRlsFourStateTest {

    private static final String TABLE = "t_order";
    private static final String INTENT_SQL = "SELECT * FROM t_order";
    private static final String APPLY_BASE = "http://localhost:18081";
    private static final String APPLY_URL = APPLY_BASE + "/api/security/rls/apply";

    private final QueryExecutionService queryExecutionService = mock(QueryExecutionService.class);

    // ═══════════════ ALLOW_ALL ═══════════════

    @Test
    @DisplayName("恒真（1=1）＝放行且不追加行过滤：本批修掉的「恒真被判 403」")
    void allowAllRunsTheIntentSqlWithoutAnyRowFilter() {
        stubQueryReturnsOneRow();

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                run(data("whereClause", "1=1", "whereParams", List.of()));

        assertEquals(200, status(resp), "放行腿的状态行必须仍是 200（本批只把拒绝腿搬上状态行）");
        ApiResponse<Map<String, Object>> env = envelope(resp);
        assertEquals(ApiResponse.CODE_SUCCESS, env.getCode(),
                "恒真策略被当成「无谓词」拒绝＝ALLOW_ALL 态在该通道消失: " + env.getMessage());
        Map<String, Object> captured = capturedExecute();
        assertEquals(INTENT_SQL, captured.get("sql"), "恒真却拼了行过滤");
        assertFalse(String.valueOf(captured.get("sql")).contains("_rls"));
        assertTrue(asParams(captured.get("params")).isEmpty(),
                "恒真态不得凭空生出 RLS 绑定: " + captured.get("params"));
    }

    // ═══════════════ FILTERED ═══════════════

    @Test
    @DisplayName("非平凡谓词：? 改写为 :rlsPn 后注入，值只在参数表里（IR05）")
    void filteredInjectsNamedSegmentsAndKeepsValuesOutOfSql() {
        stubQueryReturnsOneRow();

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                run(data("whereClause", "(org_id = ?)", "whereParams", List.of("o-42")));

        assertEquals(200, status(resp), "过滤放行腿状态行 = 200");
        assertEquals(ApiResponse.CODE_SUCCESS, envelope(resp).getCode());
        Map<String, Object> captured = capturedExecute();
        assertEquals("SELECT * FROM (" + INTENT_SQL + ") AS _rls WHERE (org_id = :rlsP0)",
                captured.get("sql"));
        assertEquals(Map.of("rlsP0", "o-42"), asParams(captured.get("params")));
        assertFalse(String.valueOf(captured.get("sql")).contains("o-42"),
                "绑定值进了 SQL 文本＝参数化是假的");
    }

    @Test
    @DisplayName("参数值形如注入串时仍只落参数表，SQL 文本零改写")
    void injectionShapedValueNeverEntersTheSqlText() {
        String payload = "' OR 1=1 --";
        stubQueryReturnsOneRow();

        run(data("whereClause", "owner_id = ?", "whereParams", List.of(payload)));

        Map<String, Object> captured = capturedExecute();
        String sql = String.valueOf(captured.get("sql"));
        assertFalse(sql.contains(payload), "客户端值被拼进 SQL: " + sql);
        assertFalse(sql.contains("OR 1=1"), "谓词被注入串改写: " + sql);
        assertEquals(payload, asParams(captured.get("params")).get("rlsP0"));
    }

    @Test
    @DisplayName("调用方参数名占用 rlsP0 → 拒绝而非覆盖用户参数值（越权面）")
    void callerParamNameCollisionRefusesInsteadOfOverwriting() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = run(
                data("whereClause", "org_id = ?", "whereParams", List.of("o-42")),
                Map.of("rlsP0", "user-supplied"));

        assertDeniedShape(resp, "绑定名占用属拒绝态，状态行必须真 403");
        verifyNoInteractions(queryExecutionService);
    }

    // ═══════════════ DENIED ═══════════════

    @Test
    @DisplayName("生产者拒绝（1=0）→ 状态行 403 且请求根本不到达执行层")
    void producerDenyIsForbiddenAndNeverReachesTheEngine() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                run(data("whereClause", "1=0", "rlsDenyReason", "RLS_NO_POLICY_MATCHED"));

        assertDeniedShape(resp, "DENIED 腿此前 HTTP 恒 200＝D-B4-59");
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("? 数与参数数不符 → 拒绝，禁把未绑定的 ? 拼进 SQL")
    void arityMismatchFromProducerIsDeniedNotGuessed() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                run(data("whereClause", "(a = ? AND b = ?)", "whereParams", List.of("v-1")));

        assertDeniedShape(resp, "谓词不可绑定属拒绝态");
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("片段仍含 :name（生产者未渲染完）→ 拒绝，禁把占位符拼进 SQL")
    void residualNamedPlaceholderIsDenied() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = run(data("whereClause", "org_id = :orgId"));

        assertDeniedShape(resp, "残留占位符属拒绝态");
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("参数含 null（上下文未解析）→ 拒绝，禁下推成「该用户没有数据」（D-B4-23）")
    void nullWhereParamIsDeniedNotPushedDownAsNullComparison() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp = run(
                data("whereClause", "department_id = ?", "whereParams", Collections.singletonList(null)));

        assertDeniedShape(resp, "上下文未解析必须是「拒绝」，不是「空集」");
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("应答 code=0 却缺 data 段 → 可解释的拒绝，禁退回「无过滤」")
    void missingDataSegmentIsDeniedNotSilentPass() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                runWithTransport(transportReturning(Map.of("code", "0", "message", "ok")));

        assertDeniedShape(resp, "缺 data 段不得当「无过滤」放行");
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("应答缺 whereClause（D-B4-17 的漂移入口）→ 拒绝而非放行")
    void absentWhereClauseIsDeniedNotTreatedAsNoFilter() {
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                run(data("denyIfEmpty", Boolean.TRUE, "noPolicyMatched", Boolean.FALSE));

        assertDeniedShape(resp, "键面漂移入口必须落在拒绝侧");
        verifyNoInteractions(queryExecutionService);
    }

    // ═══════════════ UNAVAILABLE（§4.5.4：不可用 ≠ 无权限） ═══════════════

    @Test
    @DisplayName("传输异常 → 状态行 503 + ECOS-SEC-501，绝不冒充 403")
    void transportFailureIsUnavailableNotForbidden() {
        assert503(transportThrowing(new ResourceAccessException("connect refused")));
    }

    @Test
    @DisplayName("空响应 → 503（连上了但没内容，仍属不可用而非策略结论）")
    void emptyResponseIsUnavailableNotForbidden() {
        assert503(transportReturning(null));
    }

    @Test
    @DisplayName("security 侧业务失败（code 非 0）→ 503，禁把「服务报错」读成「用户无权限」")
    void producerBusinessFailureIsUnavailableNotForbidden() {
        assert503(transportReturning(Map.of("code", "-1", "message", "internal error")));
    }

    @Test
    @DisplayName("寻址失败（route-manifest 未登记 sysman）＝安全服务不可达，且不落到传输层")
    void unresolvedEndpointIsUnavailableAndNeverReachesTransport() {
        RestTemplate transport = mock(RestTemplate.class);
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                controllerWith(transport, resolverReturning(null)).execute(body(Map.of()), null);

        assert503Shape(resp);
        verifyNoInteractions(queryExecutionService);
        // 寻址失败必须在落到传输层之前就收口（否则「猜一个 host」的旧形态会悄悄回来）
        verify(transport, never()).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("resolver 缺失（可选注入未到位）→ 503，与本仓「安全不可用默认 DENY」同口径")
    void missingResolverBeanIsUnavailable() {
        QueryController controller = new QueryController(queryExecutionService, null);

        assert503Shape(controller.execute(body(Map.of()), null));
        verifyNoInteractions(queryExecutionService);
    }

    // ═══════════════ 状态行分形（D-B4-59 的本体判据） ═══════════════

    @Test
    @DisplayName("状态行分形：策略拒绝 403 与安全服务不可用 503 不得同形（收口 D-B4-59）")
    void denialAndUnavailableAreDistinguishableOnTheStatusLine() {
        int denied = status(run(data("whereClause", "1=0", "rlsDenyReason", "RLS_NO_POLICY_MATCHED")));
        int unavailable = status(runWithTransport(
                transportThrowing(new ResourceAccessException("connect refused"))));

        assertEquals(403, denied, "策略拒绝的状态行");
        assertEquals(503, unavailable, "安全服务不可用的状态行");
        assertNotEquals(denied, unavailable,
                "收口前两态在状态行同形（恒 200），调用方 res.ok 一律读成成功＝D-B4-59 的形态");
    }

    @Test
    @DisplayName("返回类型必须承载状态行：execute 声明 ResponseEntity（摘回裸 ApiResponse 即红）")
    void executeMethodMustReturnTypeThatCarriesAStatusLine() throws Exception {
        var m = QueryController.class.getMethod("execute", Map.class, String.class);
        assertEquals(ResponseEntity.class, m.getReturnType(),
                "execute 返回类型必须 = ResponseEntity；裸 ApiResponse 没有状态行能力，"
                        + "信封里的 403/503 会被 Spring 落成 HTTP 200（D-B4-59 复现形态）");
    }

    // ═══════════════ 寻址单源（W32） ═══════════════

    @Test
    @DisplayName("端点只经 ServiceEndpointResolver 两态寻址，禁引擎内硬编码 host:port")
    void applyEndpointIsAddressedOnlyThroughTheResolver() {
        stubQueryReturnsOneRow();
        RestTemplate transport = transportReturning(applyResponse(data("whereClause", "1=1")));
        ServiceEndpointResolver resolver = resolverReturning(APPLY_BASE);

        controllerWith(transport, resolver).execute(body(Map.of()), null);

        verify(resolver).resolve("sysman", ServiceEndpointResolver.RouteMode.MONOLITH);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(transport).postForObject(urlCaptor.capture(), any(HttpEntity.class), eq(Map.class));
        assertEquals(APPLY_URL, urlCaptor.getValue());
    }

    // ═══════════════ 夹具与桩 ═══════════════

    /** 用生产者的线上键面字面量造应答段（与读侧共用常量会让键面漂移测不出来，见类 javadoc）。 */
    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> segment = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            segment.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return segment;
    }

    private static Map<String, Object> applyResponse(Map<String, Object> dataSegment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "0");
        response.put("message", "ok");
        response.put("data", dataSegment);
        return response;
    }

    private static Map<String, Object> body(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasource_id", "ds-1");
        body.put("sql", INTENT_SQL);
        body.put("table_name", TABLE);
        body.put("params", params);
        return body;
    }

    private static RestTemplate transportReturning(Map<String, Object> response) {
        RestTemplate transport = mock(RestTemplate.class);
        when(transport.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);
        return transport;
    }

    private static RestTemplate transportThrowing(RuntimeException failure) {
        RestTemplate transport = mock(RestTemplate.class);
        when(transport.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(failure);
        return transport;
    }

    private void stubQueryReturnsOneRow() {
        Map<String, Object> rows = new LinkedHashMap<>();
        rows.put("rowCount", 1);
        when(queryExecutionService.execute(anyString(), anyString(), anyMap(), anyInt(), anyInt()))
                .thenReturn(rows);
    }

    /** 走完整通道：可用态默认 resolver 已登记 sysman（不可用态的用例各自构造缺失形态）。 */
    private ResponseEntity<ApiResponse<Map<String, Object>>> run(Map<String, Object> dataSegment) {
        return run(dataSegment, Map.of());
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> run(Map<String, Object> dataSegment,
                                                                 Map<String, Object> params) {
        return runWithTransport(transportReturning(applyResponse(dataSegment)), params);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> runWithTransport(RestTemplate transport) {
        return runWithTransport(transport, Map.of());
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> runWithTransport(
            RestTemplate transport, Map<String, Object> params) {
        return controllerWith(transport, resolverReturning(APPLY_BASE)).execute(body(params), null);
    }

    private QueryController controllerWith(RestTemplate transport, ServiceEndpointResolver resolver) {
        QueryController controller = new QueryController(queryExecutionService, resolver);
        injectRestTemplate(controller, transport);
        return controller;
    }

    private static ServiceEndpointResolver resolverReturning(String base) {
        ServiceEndpointResolver resolver = mock(ServiceEndpointResolver.class);
        when(resolver.resolve(anyString(), any(ServiceEndpointResolver.RouteMode.class)))
                .thenReturn(base == null ? Optional.empty() : Optional.of(base));
        return resolver;
    }

    /**
     * {@code restTemplate} 是 private final 且没有测试接缝：用反射注入而不是为此改生产构造签名。
     * 注入不上即抛（禁「拿不到字段就跳过用例」的假绿）。
     */
    private static void injectRestTemplate(QueryController controller, RestTemplate transport) {
        try {
            Field field = QueryController.class.getDeclaredField("restTemplate");
            field.setAccessible(true);
            field.set(controller, transport);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "无法注入 RestTemplate——Controller 的传输接缝已变更，门禁必须变红而不是跳过", e);
        }
    }

    /** 捕获真正送达执行层的 SQL 与参数表（本类的结论只由实参得出，不由源码正则得出）。 */
    private Map<String, Object> capturedExecute() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryExecutionService).execute(anyString(), sqlCaptor.capture(),
                paramsCaptor.capture(), anyInt(), anyInt());
        Map<String, Object> captured = new LinkedHashMap<>();
        captured.put("sql", sqlCaptor.getValue());
        captured.put("params", paramsCaptor.getValue());
        return captured;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asParams(Object raw) {
        return (Map<String, Object>) raw;
    }

    private static int status(ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        return resp.getStatusCode().value();
    }

    /** 信封体（缺失即抛——禁把「没有 body」当成「没有违例」）。 */
    private static ApiResponse<Map<String, Object>> envelope(
            ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        assertNotNull(resp, "Controller 不得返回 null 响应实体");
        ApiResponse<Map<String, Object>> body = resp.getBody();
        assertNotNull(body, "状态行之外还必须带可读信封（消费者按 code/errorCode 分派）");
        return body;
    }

    /** 拒绝态双面目判：状态行 403 ＋ 信封 code 403，缺一即折叠。 */
    private static void assertDeniedShape(ResponseEntity<ApiResponse<Map<String, Object>>> resp,
                                          String why) {
        assertEquals(403, status(resp), why + "（状态行）");
        assertEquals(ApiResponse.CODE_FORBIDDEN, envelope(resp).getCode(), why + "（信封 code）");
    }

    private void assert503(RestTemplate transport) {
        assert503Shape(runWithTransport(transport));
        verifyNoInteractions(queryExecutionService);
    }

    private static void assert503Shape(ResponseEntity<ApiResponse<Map<String, Object>>> resp) {
        assertEquals(503, status(resp),
                "不可用态的 503 必须落进 HTTP 状态行（收口前恒 200＝D-B4-59）");
        assertNotEquals(403, status(resp),
                "把「安全服务不可用」冒充成「用户无权限」＝§4.5.4 禁止的折叠（状态行面）");
        ApiResponse<Map<String, Object>> env = envelope(resp);
        assertEquals(503, env.getCode(), "不可用态的对外码漂移: " + env.getMessage());
        assertNotEquals(ApiResponse.CODE_FORBIDDEN, env.getCode(),
                "把「安全服务不可用」冒充成「用户无权限」＝§4.5.4 禁止的折叠（信封面）");
        assertEquals(QueryController.ERROR_SEC_UNAVAILABLE, env.getErrorCode(),
                "不可用态必须带机读码（与 DecisionController/gateway 同值）");
    }
}
