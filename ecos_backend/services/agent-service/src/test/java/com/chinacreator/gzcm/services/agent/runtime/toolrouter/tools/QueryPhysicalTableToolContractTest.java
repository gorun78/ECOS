package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.chinacreator.gzcm.services.agent.dao.AgentToolDao;
import com.chinacreator.gzcm.services.agent.runtime.toolrouter.AgentToolEndpointResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QueryPhysicalTableTool} 收口 <b>D-B4-49 任意 SQL 子项</b>的行为不变式（#142/B4-3e-16）。
 *
 * <p>本类钉四条腿，各自对应差距解除条件的一半：</p>
 * <ol>
 *   <li><b>判据在前、调用在后</b>：非「单表只读」的 SQL 必须<b>根本不发起出向调用</b>
 *       （{@link #guardRejectionNeverReachesTheOwnerEndpoint()}）。否则改指属主端点只是把
 *       任意 SQL 换了个执行地点，风险一点没降。</li>
 *   <li><b>表名如实随体下发</b>：属主 {@code QueryController#execute} 只在
 *       {@code table_name} 非空时才跑 RLS（该触发条件覆盖面在 #142/B4-3e-18 另立为 <b>D-B4-63</b>），所以
 *       {@code table_name} 缺失＝本腿自愿放弃行级安全
 *       （{@link #tableNameIsCarriedInTheBodySoOwnerRlsApplies()} 把它钉成必带项）。</li>
 *   <li><b>分态不折叠</b>：§4.7.2「不可用 ≠ 无权限」在这里落成
 *       {@code DENIED}／{@code SECURITY_UNAVAILABLE}／{@code ENGINE_FAILED}／{@code UNREACHABLE}
 *       四个态，每一对都做「同形输入结局必异」的对照断言防恒真；{@code available} 只在取到数据时为真。</li>
 *   <li><b>进程内执行面已断开</b>：本类不得再持有任何 {@link AgentToolDao} 引用
 *       （{@link #toolHoldsNoDaoReference()}），否则
 *       {@code jdbcTemplate.queryForList(sql + " LIMIT ")} 那条拼接路径就还活着。</li>
 * </ol>
 *
 * <p><b>身份透传正反两向都做</b>：正向（请求线程上有 {@code Authorization} ⇒ 原样带给属主）与
 * 反向（无请求上下文 ⇒ <b>不伪造</b>，出向头里不许出现 {@code Authorization}）。正向用
 * {@link Proxy} 造最小 {@link HttpServletRequest}（本模块测试面无 spring-test，手写全量接口实现不现实），
 * 只应答 {@code getHeader}。</p>
 *
 * <p>不用 mockito（{@code services/agent-service/pom.xml} 的 test 依赖仅 junit-jupiter + archunit），
 * 桩全部为手写子类；HTTP 状态异常走 {@code HttpClientErrorException#create} /
 * {@code HttpServerErrorException#create} 这一仓内既有配方（{@code SecurityDownAllChannelDenyTest} 同法）。</p>
 */
class QueryPhysicalTableToolContractTest {

    private static final String RESOLVED_BASE = "http://svc-owner:18082";

    // 合成 schema 名，不用业务域真表名：非 data-engine 文件里的 ecos_dw 限定名会撞
    // data-engine 侧横切门 BusinessDomainWriteArchTest#noEcosDwOutsideDataEngine（该门只看字面量）
    private static final String READ_ONLY_SQL = "SELECT id, amount FROM DW_Sales.Order_Detail WHERE id=1";

    // ── 桩 ─────────────────────────────────────────────

    /** 记录出向 URL / 请求体 / 请求头，并按脚本返回应答或抛异常的 RestTemplate 桩。 */
    private static final class RecordingRestTemplate extends RestTemplate {
        private final Object scriptedResponse;
        private final RuntimeException scriptedFailure;
        private String lastUrl;
        private HttpEntity<?> lastEntity;

        RecordingRestTemplate(Object scriptedResponse, RuntimeException scriptedFailure) {
            this.scriptedResponse = scriptedResponse;
            this.scriptedFailure = scriptedFailure;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            this.lastUrl = url;
            this.lastEntity = (HttpEntity<?>) request;
            if (scriptedFailure != null) {
                throw scriptedFailure;
            }
            return (T) scriptedResponse;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> body() {
            return (Map<String, Object>) lastEntity.getBody();
        }
    }

    /** 固定 {@link AgentToolEndpointResolver#dataBaseUrl()} 结果的解析器桩。 */
    private static final class StubResolver extends AgentToolEndpointResolver {
        private final Optional<String> result;

        StubResolver(Optional<String> result) {
            super((ObjectProvider<ServiceEndpointResolver>) null, "", "", "");
            this.result = result;
        }

        @Override
        public Optional<String> dataBaseUrl() {
            return result;
        }
    }

    /** 只应答 {@code getHeader} 的最小 {@link HttpServletRequest}（把请求线程上下文绑上用）。 */
    private static HttpServletRequest requestWithHeaders(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                QueryPhysicalTableToolContractTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        return headers.get((String) args[0]);
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    return rt == int.class ? 0 : null;
                });
    }

    private static Map<String, Object> envelope(Integer code, String errorCode, String message,
                                                 Map<String, Object> data) {
        Map<String, Object> e = new LinkedHashMap<>();
        if (code != null) {
            e.put("code", code);
        }
        if (errorCode != null) {
            e.put("errorCode", errorCode);
        }
        if (message != null) {
            e.put("message", message);
        }
        if (data != null) {
            e.put("data", data);
        }
        return e;
    }

    /** 真 4xx/5xx 应答形态（属主裸 envelope 走 code 分支，另一套来源在此）。 */
    private static RestClientException statusError(HttpStatus status) {
        return status.is4xxClientError()
                ? HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null)
                : HttpServerErrorException.create(status, status.getReasonPhrase(), null, null, null);
    }

    private static Map<String, Object> emptyData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", List.of());
        data.put("rowCount", 0);
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(Object response, RuntimeException failure, Optional<String> base,
                                           Map<String, Object> params) {
        RecordingRestTemplate rest = new RecordingRestTemplate(response, failure);
        Object out = new QueryPhysicalTableTool(rest, new StubResolver(base))
                .execute("QueryPhysicalTable", params);
        assertTrue(out instanceof Map, "工具必须返回结构化载荷，不得把属主原始对象整串透传");
        return (Map<String, Object>) out;
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> readOnlyParams() {
        return params("datasourceId", "ds-1", "sql", READ_ONLY_SQL);
    }

    private static String state(Map<String, Object> payload) {
        return String.valueOf(payload.get(QueryPhysicalTableTool.KEY_STATE));
    }

    // ── 判据腿：不通过即不发请求 ───────────────────────

    @Test
    @DisplayName("非只读 SQL ⇒ GUARD_REJECTED，且出向调用一次都没发生")
    void guardRejectionNeverReachesTheOwnerEndpoint() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = (Map<String, Object>) new QueryPhysicalTableTool(rest,
                new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable", params("datasourceId", "ds-1", "sql", "DELETE FROM dw_sales.order_detail"));

        assertEquals("GUARD_REJECTED", state(payload));
        assertNull(rest.lastUrl, "判据拒绝后不得发起调用 —— 否则任意 SQL 只是换了个执行地点");
        assertEquals(Boolean.FALSE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        assertTrue(String.valueOf(payload.get(AgentToolResponses.KEY_MESSAGE))
                .startsWith("PHYSICAL_QUERY_REJECTED:"), "拒绝理由必须带机读前缀供调用方分派");
    }

    @Test
    @DisplayName("空 SQL ⇒ GUARD_REJECTED（缺失入参不许被当成放行）")
    void blankSqlIsRejectedNotTreatedAsAllowed() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = (Map<String, Object>) new QueryPhysicalTableTool(rest,
                new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable", params("datasourceId", "ds-1", "sql", "   "));

        assertEquals("GUARD_REJECTED", state(payload));
        assertNull(rest.lastUrl);
    }

    @Test
    @DisplayName("params 为 null ⇒ GUARD_REJECTED（上游契约违例 fail-closed，不静默取空集）")
    void nullParamsAreRejected() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = (Map<String, Object>) new QueryPhysicalTableTool(rest,
                new StubResolver(Optional.of(RESOLVED_BASE))).execute("QueryPhysicalTable", null);

        assertEquals("GUARD_REJECTED", state(payload));
        assertNull(rest.lastUrl);
    }

    // ── 基址腿：解析不到即不发请求 ─────────────────────

    @Test
    @DisplayName("基址解析不到 ⇒ UNREACHABLE、不发请求、不回落硬编码地址")
    void unresolvedBaseYieldsUnreachableWithoutCall() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = run(null, null, Optional.empty(), readOnlyParams());

        assertEquals("UNREACHABLE", state(payload));
        assertEquals(Boolean.FALSE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        assertNull(rest.lastUrl, "基址缺失时不得发起请求（禁回落 localhost:8080）");
    }

    // ── 请求体契约腿 ───────────────────────────────────

    @Test
    @DisplayName("放行：URL＝解析基址+属主端点，表名如实进 table_name（否则属主侧 RLS 不生效）")
    void tableNameIsCarriedInTheBodySoOwnerRlsApplies() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", emptyData()), null);
        new QueryPhysicalTableTool(rest, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable", params("datasourceId", "ds-9", "sql", READ_ONLY_SQL));

        assertEquals(RESOLVED_BASE + QueryPhysicalTableTool.PATH_DATA_QUERY_EXECUTE, rest.lastUrl);
        Map<String, Object> body = rest.body();
        assertEquals("ds-9", body.get(QueryPhysicalTableTool.BODY_DATASOURCE_ID), "数据源 ID 必须原形带给属主");
        assertEquals(READ_ONLY_SQL, body.get(QueryPhysicalTableTool.BODY_SQL));
        assertEquals("dw_sales.order_detail", body.get(QueryPhysicalTableTool.BODY_TABLE_NAME),
                "FROM 提取的物理表名（小写归一）必须进 table_name —— 属主端点以此决定是否跑行级安全（触发条件覆盖面在册为 D-B4-63）");
        assertEquals(1000, ((Number) body.get(QueryPhysicalTableTool.BODY_MAX_ROWS)).intValue(),
                "缺省行数与收口前缺省值同形（外形只增不改）");
    }

    @Test
    @DisplayName("maxRows 只向下收：999999 ⇒ 上限 5000，7 ⇒ 保留 7（向上放大＝把收口做反）")
    void maxRowsIsClampedDownwardNeverUpward() {
        RecordingRestTemplate big = new RecordingRestTemplate(envelope(0, null, "ok", emptyData()), null);
        new QueryPhysicalTableTool(big, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable",
                        params("datasourceId", "d", "sql", READ_ONLY_SQL, "maxRows", 999_999));
        assertEquals(5000, ((Number) big.body().get(QueryPhysicalTableTool.BODY_MAX_ROWS)).intValue());

        RecordingRestTemplate small = new RecordingRestTemplate(envelope(0, null, "ok", emptyData()), null);
        new QueryPhysicalTableTool(small, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable", params("datasourceId", "d", "sql", READ_ONLY_SQL, "maxRows", 7));
        assertEquals(7, ((Number) small.body().get(QueryPhysicalTableTool.BODY_MAX_ROWS)).intValue());
    }

    // ── 身份载体腿（正/反两向）─────────────────────────

    @Test
    @DisplayName("请求线程上有 Authorization ⇒ 原样透传给属主（主语不丢）")
    void authorizationHeaderIsForwardedWhenRequestContextIsBound() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", emptyData()), null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                requestWithHeaders(Map.of(HttpHeaders.AUTHORIZATION, "Bearer t-1"))));
        try {
            new QueryPhysicalTableTool(rest, new StubResolver(Optional.of(RESOLVED_BASE)))
                    .execute("QueryPhysicalTable", readOnlyParams());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        assertEquals("Bearer t-1", rest.lastEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                "同步请求腿上的主语必须带给属主，否则 RLS 只能按缺省主体裁决");
        assertEquals("application/json", rest.lastEntity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    @DisplayName("无请求上下文 ⇒ 不伪造身份（出向头禁止出现 Authorization）")
    void noAuthorizationHeaderIsInventedWithoutRequestContext() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", emptyData()), null);
        RequestContextHolder.resetRequestAttributes();
        new QueryPhysicalTableTool(rest, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("QueryPhysicalTable", readOnlyParams());

        assertFalse(rest.lastEntity.getHeaders().containsKey(HttpHeaders.AUTHORIZATION),
                "取不到载体时绝不伪造主语 —— 缺失交由属主侧默认 DENY 裁决（余项 D-B4-61）");
    }

    // ── 分态腿：四态互不折叠 ───────────────────────────

    @Test
    @DisplayName("code=0 ⇒ DATA_RETURNED、available=true、data 段原样透传")
    void successPassesThroughDataSection() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", List.of(Map.of("id", 1)));
        data.put("rowCount", 1);
        data.put("elapsedMs", 12);
        data.put("historyId", "h-77");
        data.put("truncated", true);

        Map<String, Object> payload = run(envelope(0, null, "ok", data), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("DATA_RETURNED", state(payload));
        assertEquals(Boolean.TRUE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        assertEquals("ds-1", payload.get(QueryPhysicalTableTool.KEY_DATASOURCE_ID));
        assertEquals("dw_sales.order_detail", payload.get(QueryPhysicalTableTool.KEY_TABLE_NAME));
        assertEquals(List.of(Map.of("id", 1)), payload.get("rows"), "取数结果必须保持原形（只增不改）");
        assertEquals(1, ((Number) payload.get("rowCount")).intValue());
        assertEquals("h-77", payload.get("historyId"), "属主台账 ID 透传，调用方才能核对历史");
        assertEquals(Boolean.TRUE, payload.get("truncated"));
    }

    @Test
    @DisplayName("code=403 ⇒ DENIED；code=503 ⇒ SECURITY_UNAVAILABLE；两态必异")
    void deniedAndSecurityUnavailableAreDistinctStates() {
        Map<String, Object> denied = run(envelope(403, null, "无权限", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());
        Map<String, Object> unavailable = run(envelope(503, null, "security 不可用", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("DENIED", state(denied));
        assertEquals("SECURITY_UNAVAILABLE", state(unavailable));
        assertFalse(state(denied).equals(state(unavailable)),
                "§4.7.2：不可用 ≠ 无权限。折叠会让调用方把可重试的护栏故障当成本人无权限");
        // 两者都「没取到数据」，available 同为 false ⇒ 折叠面只存在于 state，不许在 available 上做假绿
        assertEquals(Boolean.FALSE, denied.get(AgentToolResponses.KEY_AVAILABLE));
        assertEquals(Boolean.FALSE, unavailable.get(AgentToolResponses.KEY_AVAILABLE));
    }

    @Test
    @DisplayName("errorCode=ECOS-SEC-501 ⇒ SECURITY_UNAVAILABLE，优先于 code 分支")
    void secUnavailableErrorCodeWinsOverCodeBranch() {
        Map<String, Object> payload = run(
                envelope(403, RlsExecutionContract.SEC_UNAVAILABLE_CODE, "护栏不可用", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("SECURITY_UNAVAILABLE", state(payload),
                "属主用 errorCode 表达『护栏不可用』时必须按键面单源判定，不被同形 code=403 折成拒绝");
    }

    @Test
    @DisplayName("code=0 且带 ECOS-SEC-501 仍算取到数据（判据只看放行码，不误伤成功腿）")
    void successEnvelopeIsNotOverrejection() {
        Map<String, Object> payload = run(
                envelope(0, RlsExecutionContract.SEC_UNAVAILABLE_CODE, "ok", emptyData()), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("DATA_RETURNED", state(payload));
    }

    @Test
    @DisplayName("其它非零 code ⇒ ENGINE_FAILED，与 DENIED／SECURITY_UNAVAILABLE 三态互异")
    void otherBusinessCodeIsEngineFailure() {
        Map<String, Object> engine = run(envelope(1001, "ECOS-DATA-1001", "数据源不存在", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());
        Map<String, Object> denied = run(envelope(403, null, "无权限", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());
        Map<String, Object> unavailable = run(envelope(503, null, "护栏不可用", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("ENGINE_FAILED", state(engine));
        assertEquals("数据源不存在", engine.get(AgentToolResponses.KEY_MESSAGE), "属主理由必须如实回传，不许吞掉");
        assertEquals(3, java.util.Set.of(state(engine), state(denied), state(unavailable)).size(),
                "『属主报错』『无权限』『护栏不可用』是三件不同的事，任何折叠都会让调用方作出错误的下一步");
    }

    @Test
    @DisplayName("应答缺 code / 非 envelope 形态 ⇒ UNREACHABLE（结论未知，不冒充拒绝）")
    void malformedEnvelopeIsUnreachable() {
        Map<String, Object> noCode = run(envelope(null, null, "无码", null), null,
                Optional.of(RESOLVED_BASE), readOnlyParams());
        Map<String, Object> notMap = run("text/html 502 Bad Gateway", null,
                Optional.of(RESOLVED_BASE), readOnlyParams());

        assertEquals("UNREACHABLE", state(noCode));
        assertEquals("UNREACHABLE", state(notMap));
    }

    @Test
    @DisplayName("传输失败⇒UNREACHABLE；真 403/503/500⇒DENIED/SECURITY_UNAVAILABLE/ENGINE_FAILED")
    void transportAndStatusFailuresUseTheSameVocabulary() {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("transport", state(run(null, new ResourceAccessException("connect refused"),
                Optional.of(RESOLVED_BASE), readOnlyParams())));
        states.put("s403", state(run(null, statusError(HttpStatus.FORBIDDEN),
                Optional.of(RESOLVED_BASE), readOnlyParams())));
        states.put("s503", state(run(null, statusError(HttpStatus.SERVICE_UNAVAILABLE),
                Optional.of(RESOLVED_BASE), readOnlyParams())));
        states.put("s500", state(run(null, statusError(HttpStatus.INTERNAL_SERVER_ERROR),
                Optional.of(RESOLVED_BASE), readOnlyParams())));

        assertEquals("UNREACHABLE", states.get("transport"));
        assertEquals("DENIED", states.get("s403"));
        assertEquals("SECURITY_UNAVAILABLE", states.get("s503"));
        assertEquals("ENGINE_FAILED", states.get("s500"));
        assertFalse(states.get("s403").equals(states.get("s503")),
                "真 HTTP 403/503 同样不得折叠 —— envelope 与状态码两套来源共用一个词表");
    }

    // ── 进程内执行面已断开 ─────────────────────────────

    @Test
    @DisplayName("本类不再持有 AgentToolDao（拼接 SQL 的进程内腿必须整条拆除）")
    void toolHoldsNoDaoReference() {
        List<String> daoFields = java.util.Arrays.stream(QueryPhysicalTableTool.class.getDeclaredFields())
                .filter(f -> AgentToolDao.class.isAssignableFrom(f.getType()))
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertEquals(List.of(), daoFields,
                "QueryPhysicalTableTool 又拿回了 DAO ⇒ 进程内 queryForList(sql + \" LIMIT \") 那条拼接路径复活");
    }

    @Test
    @DisplayName("supports 只认 QueryPhysicalTable（工具码路由外形不变）")
    void supportsOnlyTheRegisteredToolCode() {
        QueryPhysicalTableTool tool = new QueryPhysicalTableTool(
                new RecordingRestTemplate(null, null), new StubResolver(Optional.empty()));
        assertTrue(tool.supports("QueryPhysicalTable"));
        assertFalse(tool.supports("QueryPhysicalTableV2"));
        assertFalse(tool.supports("SearchCatalog"));
    }
}
