package com.chinacreator.gzcm.engine.data.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.QueryExecutionService;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;

/**
 * data-engine 查询通道的 RLS 四态分流门禁（#142/B4-3d-5，差距 D-B4-24 消费侧 ＋ §4.5.4 分态 D-B4-26）。
 *
 * <p><b>钉的是本批真正修掉的两个缺陷</b>：① 本通道此前读 {@code /rls/apply} 应答的<b>表达面</b>
 * {@code predicate.predicateTemplate}，恒真策略（{@code 1=1}＝明确「不限制行」）被它判成
 * 「无有效谓词」直接 403 —— 四态里的 ALLOW_ALL 在这一通道凭空消失；② 上下文未解析时表达面的
 * 参数表带 {@code null}，本通道把 {@code col = ?} 绑上 {@code null} 下推，结果「恰好零行」，
 * 语义却是「未知」而非「拒绝」——把 D-B4-23 伪装成「该用户没有数据」。
 * 现在读数经 {@code RlsExecutionContract.read} 单源，四态分流，不可用态走 503
 * {@code ECOS-SEC-501}（与「无权限」的 403 在对外形态上不可能同值）。</p>
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
 * {@code restTemplate} 是 {@code private final} 且无测试接缝，用反射注入而非为此改生产签名
 * （API 只增不改），注入失败即抛——禁「注入不上就跳过」的假绿。</p>
 */
@DisplayName("B4-3d-5：data-engine 查询通道 —— RLS 四态分流与参数化注入（D-B4-24／§4.5.4）")
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

        ApiResponse<Map<String, Object>> resp = run(data("whereClause", "1=1", "whereParams", List.of()));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(),
                "恒真策略被当成「无谓词」拒绝＝ALLOW_ALL 态在该通道消失: " + resp.getMessage());
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

        ApiResponse<Map<String, Object>> resp =
                run(data("whereClause", "(org_id = ?)", "whereParams", List.of("o-42")));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), resp.getMessage());
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
        ApiResponse<Map<String, Object>> resp = run(
                data("whereClause", "org_id = ?", "whereParams", List.of("o-42")),
                Map.of("rlsP0", "user-supplied"));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    // ═══════════════ DENIED ═══════════════

    @Test
    @DisplayName("生产者拒绝（1=0）→ 403 且请求根本不到达执行层")
    void producerDenyIsForbiddenAndNeverReachesTheEngine() {
        ApiResponse<Map<String, Object>> resp =
                run(data("whereClause", "1=0", "rlsDenyReason", "RLS_NO_POLICY_MATCHED"));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("? 数与参数数不符 → 拒绝，禁把未绑定的 ? 拼进 SQL")
    void arityMismatchFromProducerIsDeniedNotGuessed() {
        ApiResponse<Map<String, Object>> resp =
                run(data("whereClause", "(a = ? AND b = ?)", "whereParams", List.of("v-1")));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("片段仍含 :name（生产者未渲染完）→ 拒绝，禁把占位符拼进 SQL")
    void residualNamedPlaceholderIsDenied() {
        ApiResponse<Map<String, Object>> resp = run(data("whereClause", "org_id = :orgId"));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("参数含 null（上下文未解析）→ 拒绝，禁下推成「该用户没有数据」（D-B4-23）")
    void nullWhereParamIsDeniedNotPushedDownAsNullComparison() {
        ApiResponse<Map<String, Object>> resp = run(
                data("whereClause", "department_id = ?", "whereParams", Collections.singletonList(null)));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("应答 code=0 却缺 data 段 → 可解释的拒绝，禁退回「无过滤」")
    void missingDataSegmentIsDeniedNotSilentPass() {
        ApiResponse<Map<String, Object>> resp =
                runWithTransport(transportReturning(Map.of("code", "0", "message", "ok")));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    @Test
    @DisplayName("应答缺 whereClause（D-B4-17 的漂移入口）→ 拒绝而非放行")
    void absentWhereClauseIsDeniedNotTreatedAsNoFilter() {
        ApiResponse<Map<String, Object>> resp =
                run(data("denyIfEmpty", Boolean.TRUE, "noPolicyMatched", Boolean.FALSE));

        assertEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(), resp.getMessage());
        verifyNoInteractions(queryExecutionService);
    }

    // ═══════════════ UNAVAILABLE（§4.5.4：不可用 ≠ 无权限） ═══════════════

    @Test
    @DisplayName("传输异常 → 503 ECOS-SEC-501，绝不冒充 403")
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
        ApiResponse<Map<String, Object>> resp =
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
    private ApiResponse<Map<String, Object>> run(Map<String, Object> dataSegment) {
        return run(dataSegment, Map.of());
    }

    private ApiResponse<Map<String, Object>> run(Map<String, Object> dataSegment,
                                                 Map<String, Object> params) {
        return runWithTransport(transportReturning(applyResponse(dataSegment)), params);
    }

    private ApiResponse<Map<String, Object>> runWithTransport(RestTemplate transport) {
        return runWithTransport(transport, Map.of());
    }

    private ApiResponse<Map<String, Object>> runWithTransport(RestTemplate transport,
                                                              Map<String, Object> params) {
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

    private void assert503(RestTemplate transport) {
        assert503Shape(runWithTransport(transport));
        verifyNoInteractions(queryExecutionService);
    }

    private static void assert503Shape(ApiResponse<Map<String, Object>> resp) {
        assertEquals(503, resp.getCode(), "不可用态的对外码漂移: " + resp.getMessage());
        assertNotEquals(ApiResponse.CODE_FORBIDDEN, resp.getCode(),
                "把「安全服务不可用」冒充成「用户无权限」＝§4.5.4 禁止的折叠");
        assertEquals(QueryController.ERROR_SEC_UNAVAILABLE, resp.getErrorCode(),
                "不可用态必须带机读码（与 DecisionController/gateway 同值）");
    }
}
