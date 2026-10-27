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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两支<b>属主写腿</b>（{@link TriggerPipelineTool}／{@link RunQualityCheckTool}）与结论词表单源
 * {@link Outcome} 的行为不变式（#142/B4-3e-22，收口差距 <b>D-B4-49 余项</b>写腿子项）。
 *
 * <p><b>为什么写腿要单独一套断言</b>：读腿（{@code QueryPhysicalTableToolContractTest}）的契约是
 * 「取到了什么」，写腿的契约是「<b>属主到底受没受理</b>」。收口前两支写腿的共同缺陷是
 * <b>不等属主回答就自报成功</b>——在 agent 进程里往属主台账（或一张 DDL 单源里不存在的幽灵表）
 * 插一行，然后无条件回 {@code status="triggered"}。因此本类的核心断言不是「成功形态对不对」，
 * 而是<b>四种否定结局必须各归其态、且一律 available=false</b>：属主只读冲突 409 与已下线 410
 * 归 {@code OWNER_CHANNEL_CLOSED}，403 归 {@code DENIED}，503 归 {@code SECURITY_UNAVAILABLE}，
 * 传输断了归 {@code UNREACHABLE}。把它们中的任何一个报成成功，就是继续向 LLM 伪造「管道已跑」。</p>
 *
 * <p><b>另外钉死的三件事</b>：①<b>受理凭据不自造</b>——载荷里出现 {@code runId}/{@code checkId}
 * 只能来自属主 {@code data} 段（{@link #acceptanceEvidenceComesOnlyFromTheOwner()}）；
 * ②<b>前置判据在后端之前</b>——路径段形状不过即 {@code GUARD_REJECTED} 且<b>不发请求</b>
 * （id 要拼进 URL，放行等于让调用方决定打到哪个端点）；③<b>凭证只经单源中继</b>——
 * 有请求上下文时带给属主，没有时<b>不伪造</b>（D-B4-61 三纪律在写腿上的同形复现）。</p>
 *
 * <p>桩与 {@code QueryPhysicalTableToolContractTest} 同法（本模块 test 依赖仅 junit-jupiter + archunit，
 * 无 mockito）：手写 {@link RestTemplate} 子类记录出向请求、{@link Proxy} 造最小
 * {@link HttpServletRequest}、状态异常走 {@code HttpClient/ServerErrorException#create}。</p>
 */
class OwnerWriteLegChannelTest {

    private static final String RESOLVED_BASE = "http://svc-owner:18082";

    private static final String TASK_ID = "task-0001";

    private static final String RULE_ID = "rule-0007";

    // ── 桩 ─────────────────────────────────────────────

    /** 记录出向 URL／请求体／请求头，并按脚本返回应答或抛异常。 */
    private static final class RecordingRestTemplate extends RestTemplate {
        private final Object scriptedResponse;
        private final RuntimeException scriptedFailure;
        private String lastUrl;
        private HttpEntity<?> lastEntity;
        private int calls;

        RecordingRestTemplate(Object scriptedResponse, RuntimeException scriptedFailure) {
            this.scriptedResponse = scriptedResponse;
            this.scriptedFailure = scriptedFailure;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            this.calls++;
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
            super((ObjectProvider<ServiceEndpointResolver>) null, "", "", "", "");
            this.result = result;
        }

        @Override
        public Optional<String> dataBaseUrl() {
            return result;
        }
    }

    private static HttpServletRequest requestWithHeaders(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                OwnerWriteLegChannelTest.class.getClassLoader(),
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

    private static RestClientException statusError(HttpStatus status) {
        return status.is4xxClientError()
                ? HttpClientErrorException.create(status, status.getReasonPhrase(), null, null, null)
                : HttpServerErrorException.create(status, status.getReasonPhrase(), null, null, null);
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> taskParams() {
        return params("id", TASK_ID);
    }

    private static Map<String, Object> ruleParams() {
        return params("ruleId", RULE_ID, "datasourceId", "ds-1", "tableName", "DW_Sales.Order_Detail");
    }

    private static String state(Map<String, Object> payload) {
        return String.valueOf(payload.get(AgentToolResponses.KEY_STATE));
    }

    /** 被测写腿的出向调用（两支共用同一断言形状，故用 lambda 参数化）。 */
    private interface Leg {
        Object run(Map<String, Object> params);
    }

    private static Leg triggerPipeline(RecordingRestTemplate rest, Optional<String> base) {
        return p -> new TriggerPipelineTool(rest, new StubResolver(base)).execute("TriggerPipeline", p);
    }

    private static Leg runQualityCheck(RecordingRestTemplate rest, Optional<String> base) {
        return p -> new RunQualityCheckTool(rest, new StubResolver(base)).execute("RunQualityCheck", p);
    }

    /** 两腿都跑一遍同一断言（写腿的缺陷是同族缺陷，只测一支就等于另一半没关账）。 */
    private static Map<String, Object> runOnBoth(Leg leg, Map<String, Object> params) {
        Object out = leg.run(params);
        assertTrue(out instanceof Map, "写腿必须返回结构化载荷，不得把属主原始对象整串透传");
        return (Map<String, Object>) out;
    }

    private static Map<String, Object> ownerRunData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", "run-88");
        data.put("status", "QUEUED");
        return data;
    }

    // ── 成功腿：受理凭据只来自属主 ─────────────────────

    @Test
    @DisplayName("code=0 ⇒ ACCEPTED、available=true、属主 data 段原样透传")
    void acceptancePassesThroughOwnerEvidence() {
        RecordingRestTemplate rest = new RecordingRestTemplate(
                envelope(0, null, "已受理", ownerRunData()), null);
        Map<String, Object> payload = runOnBoth(triggerPipeline(rest, Optional.of(RESOLVED_BASE)), taskParams());

        assertEquals("ACCEPTED", state(payload));
        assertEquals(Boolean.TRUE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        assertEquals(TASK_ID, payload.get(TriggerPipelineTool.KEY_TASK_ID));
        assertEquals("run-88", payload.get("runId"), "受理凭据只能来自属主 data 段");
        assertEquals("QUEUED", payload.get("status"), "属主自报的状态透传，本腿不再写死一个 status");
    }

    @Test
    @DisplayName("两腿都不再自造台账主键：载荷里出现的 id 全部来自属主或入参")
    void acceptanceEvidenceComesOnlyFromTheOwner() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evaluationId", "eval-9");
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", data), null);
        Map<String, Object> payload = runOnBoth(runQualityCheck(rest, Optional.of(RESOLVED_BASE)), ruleParams());

        assertEquals("eval-9", payload.get("evaluationId"));
        assertFalse(payload.containsKey("checkId"),
                "checkId 是收口前本腿自造的 UUID（属主从未产生过它）；再出现即进程内台账复活");
        assertEquals(1, rest.calls);
    }

    @Test
    @DisplayName("URL＝解析基址+种子登记的属主端点；请求体键面与种子一致")
    void outboundRequestShapeMatchesTheSeededContract() {
        RecordingRestTemplate rest = new RecordingRestTemplate(
                envelope(0, null, "ok", ownerRunData()), null);
        new TriggerPipelineTool(rest, new StubResolver(Optional.of(RESOLVED_BASE)))
                .execute("TriggerPipeline", taskParams());
        assertEquals(RESOLVED_BASE + TriggerPipelineTool.PATH_TASK_RUN
                .replace(TriggerPipelineTool.SEGMENT_TASK_ID, TASK_ID)
                + "?triggeredBy=agent-tool", rest.lastUrl);

        RecordingRestTemplate q = new RecordingRestTemplate(envelope(0, null, "ok", Map.of()), null);
        Map<String, Object> payload = runOnBoth(runQualityCheck(q, Optional.of(RESOLVED_BASE)), ruleParams());
        assertEquals(RESOLVED_BASE + RunQualityCheckTool.PATH_RULE_EVALUATE
                .replace(RunQualityCheckTool.SEGMENT_RULE_ID, RULE_ID), q.lastUrl);
        Map<String, Object> body = q.body();
        assertEquals(RULE_ID, body.get(RunQualityCheckTool.KEY_RULE_ID));
        assertEquals("ds-1", body.get(RunQualityCheckTool.KEY_DATASOURCE_ID));
        assertEquals("DW_Sales.Order_Detail", body.get(RunQualityCheckTool.KEY_TABLE_NAME));
        assertEquals(RunQualityCheckTool.DEFAULT_SAMPLE_SIZE,
                ((Number) body.get(RunQualityCheckTool.KEY_SAMPLE_SIZE)).intValue(),
                "抽样规模缺省值与收口前同形（外形只增不改）");
        assertEquals("application/json", q.lastEntity.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals(Boolean.TRUE, payload.get(AgentToolResponses.KEY_AVAILABLE));
    }

    // ── 前置判据腿：不过即不发请求 ─────────────────────

    @Test
    @DisplayName("路径段入参形状不过 ⇒ GUARD_REJECTED 且一次请求都不发（id 能改写落点）")
    void pathSegmentGuardRejectsWithoutCalling() {
        for (String bad : List.of("task-1/../../admin", "task 1", "task?q=1", "", "x".repeat(65))) {
            RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
            Map<String, Object> payload = runOnBoth(triggerPipeline(rest, Optional.of(RESOLVED_BASE)),
                    params("id", bad));
            assertEquals("GUARD_REJECTED", state(payload), "非法路径段被放行: [" + bad + "]");
            assertEquals(0, rest.calls, "判据拒绝后不得发起调用 —— 否则等于让入参决定请求打到哪");
            assertEquals(Boolean.FALSE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        }

        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = runOnBoth(runQualityCheck(rest, Optional.of(RESOLVED_BASE)),
                params("ruleId", "rule' OR 1=1--"));
        assertEquals("GUARD_REJECTED", state(payload));
        assertEquals(0, rest.calls);
    }

    @Test
    @DisplayName("params 为 null ⇒ GUARD_REJECTED（上游契约违例 fail-closed，不静默触发）")
    void nullParamsAreRejected() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        assertEquals("GUARD_REJECTED", state(runOnBoth(triggerPipeline(rest,
                Optional.of(RESOLVED_BASE)), null)));
        assertEquals(0, rest.calls);
    }

    @Test
    @DisplayName("基址解析不到 ⇒ UNREACHABLE、不发请求、不回落硬编码地址")
    void unresolvedBaseYieldsUnreachableWithoutCall() {
        RecordingRestTemplate rest = new RecordingRestTemplate(null, null);
        Map<String, Object> payload = runOnBoth(triggerPipeline(rest, Optional.empty()), taskParams());

        assertEquals("UNREACHABLE", state(payload));
        assertEquals(Boolean.FALSE, payload.get(AgentToolResponses.KEY_AVAILABLE));
        assertEquals(0, rest.calls, "基址缺失时不得发起请求（禁回落 localhost:8080）");
    }

    // ── 分态腿：四种否定结局互不折叠 ───────────────────

    @Test
    @DisplayName("属主 409（只读桩）与 410（已下线）⇒ OWNER_CHANNEL_CLOSED，绝不报成受理")
    void ownerClosedChannelIsReportedAsClosed() {
        RecordingRestTemplate conflict = new RecordingRestTemplate(envelope(409, null, "管道模型只读，请走 definitions/execute", null), null);
        Map<String, Object> c = runOnBoth(triggerPipeline(conflict, Optional.of(RESOLVED_BASE)), taskParams());
        assertEquals("OWNER_CHANNEL_CLOSED", state(c));
        assertEquals(Boolean.FALSE, c.get(AgentToolResponses.KEY_AVAILABLE));
        assertEquals("管道模型只读，请走 definitions/execute", c.get(AgentToolResponses.KEY_MESSAGE),
                "属主的理由必须如实回传，调用方才知道该改走哪条通道");

        RecordingRestTemplate gone = new RecordingRestTemplate(envelope(410, "GONE", "DQ legacy 写通道已下线", null), null);
        Map<String, Object> g = runOnBoth(runQualityCheck(gone, Optional.of(RESOLVED_BASE)), ruleParams());
        assertEquals("OWNER_CHANNEL_CLOSED", state(g));
        assertEquals(Boolean.FALSE, g.get(AgentToolResponses.KEY_AVAILABLE));
    }

    @Test
    @DisplayName("真 409/410 状态行同样归 OWNER_CHANNEL_CLOSED（信封与状态行共用一套词表）")
    void realStatusLinesUseTheSameVocabulary() {
        RecordingRestTemplate r409 = new RecordingRestTemplate(null, statusError(HttpStatus.CONFLICT));
        RecordingRestTemplate r410 = new RecordingRestTemplate(null, statusError(HttpStatus.GONE));

        assertEquals("OWNER_CHANNEL_CLOSED", state(runOnBoth(triggerPipeline(r409,
                Optional.of(RESOLVED_BASE)), taskParams())));
        assertEquals("OWNER_CHANNEL_CLOSED", state(runOnBoth(triggerPipeline(r410,
                Optional.of(RESOLVED_BASE)), taskParams())));
    }

    @Test
    @DisplayName("403⇒DENIED、503⇒SECURITY_UNAVAILABLE、1001⇒ENGINE_FAILED、断链⇒UNREACHABLE 四态互异")
    void negativeOutcomesAreNeverCollapsedIntoEachOther() {
        Map<String, Map<String, Object>> payloads = new LinkedHashMap<>();
        payloads.put("denied", runOnBoth(triggerPipeline(new RecordingRestTemplate(
                envelope(403, null, "无权限", null), null), Optional.of(RESOLVED_BASE)), taskParams()));
        payloads.put("unavailable", runOnBoth(triggerPipeline(new RecordingRestTemplate(
                envelope(503, null, "护栏不可用", null), null), Optional.of(RESOLVED_BASE)), taskParams()));
        payloads.put("failed", runOnBoth(triggerPipeline(new RecordingRestTemplate(
                envelope(1001, "ECOS-DATA-1001", "数据源不存在", null), null), Optional.of(RESOLVED_BASE)), taskParams()));
        payloads.put("transport", runOnBoth(triggerPipeline(new RecordingRestTemplate(
                null, new ResourceAccessException("connect refused")), Optional.of(RESOLVED_BASE)), taskParams()));

        assertEquals(List.of("DENIED", "SECURITY_UNAVAILABLE", "ENGINE_FAILED", "UNREACHABLE"),
                List.of(state(payloads.get("denied")), state(payloads.get("unavailable")),
                        state(payloads.get("failed")), state(payloads.get("transport"))),
                "写腿的否定态必须与读腿同一套词表：折叠任何两件都会让调用方作出错误的下一步");
        assertEquals(4, new java.util.HashSet<>(payloads.values().stream().map(OwnerWriteLegChannelTest::state).toList()).size());
        for (Map<String, Object> payload : payloads.values()) {
            assertEquals(Boolean.FALSE, payload.get(AgentToolResponses.KEY_AVAILABLE),
                    "四个否定态的 available 必须同为 false —— 差异只允许存在于 state，"
                            + "否则调用方会拿 available 做出与 state 相反的下一步");
        }
    }

    @Test
    @DisplayName("errorCode=ECOS-SEC-501 ⇒ SECURITY_UNAVAILABLE，优先于同形 code=403")
    void secUnavailableErrorCodeWinsOverCodeBranch() {
        RecordingRestTemplate rest = new RecordingRestTemplate(
                envelope(403, RlsExecutionContract.SEC_UNAVAILABLE_CODE, "护栏不可用", null), null);
        assertEquals("SECURITY_UNAVAILABLE", state(runOnBoth(triggerPipeline(rest,
                Optional.of(RESOLVED_BASE)), taskParams())));
    }

    @Test
    @DisplayName("应答缺 code / 非 envelope 形态 ⇒ UNREACHABLE（没答不等于拒绝）")
    void malformedEnvelopeIsUnreachable() {
        RecordingRestTemplate noCode = new RecordingRestTemplate(envelope(null, null, "无码", null), null);
        RecordingRestTemplate notMap = new RecordingRestTemplate("text/html 502 Bad Gateway", null);

        assertEquals("UNREACHABLE", state(runOnBoth(runQualityCheck(noCode, Optional.of(RESOLVED_BASE)), ruleParams())));
        assertEquals("UNREACHABLE", state(runOnBoth(runQualityCheck(notMap, Optional.of(RESOLVED_BASE)), ruleParams())));
    }

    // ── 凭证中继腿（正/反两向）─────────────────────────

    @Test
    @DisplayName("请求线程上有 Authorization ⇒ 经单源带给属主（写腿的主语也不许丢）")
    void authorizationHeaderIsForwardedWhenRequestContextIsBound() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", ownerRunData()), null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                requestWithHeaders(Map.of(HttpHeaders.AUTHORIZATION, "Bearer t-9"))));
        try {
            runOnBoth(triggerPipeline(rest, Optional.of(RESOLVED_BASE)), taskParams());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        assertEquals("Bearer t-9", rest.lastEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                "写腿若不带主语，属主只能按缺省主体裁决 —— 与读腿同一条 D-B4-61 纪律");
    }

    @Test
    @DisplayName("无请求上下文 ⇒ 不伪造身份（出向头禁止出现 Authorization）")
    void noAuthorizationHeaderIsInventedWithoutRequestContext() {
        RecordingRestTemplate rest = new RecordingRestTemplate(envelope(0, null, "ok", ownerRunData()), null);
        RequestContextHolder.resetRequestAttributes();
        runOnBoth(triggerPipeline(rest, Optional.of(RESOLVED_BASE)), taskParams());

        assertFalse(rest.lastEntity.getHeaders().containsKey(HttpHeaders.AUTHORIZATION),
                "取不到载体时绝不伪造主语 —— 缺失交由属主侧默认 DENY 裁决");
    }

    // ── 进程内执行面已断开 ─────────────────────────────

    @Test
    @DisplayName("两支写腿不再持有 AgentToolDao；DAO 侧也只剩只读方法（无写签名）")
    void writeLegsHoldNoDaoAndDaoHasNoWriteMethods() {
        for (Class<?> tool : List.of(TriggerPipelineTool.class, RunQualityCheckTool.class)) {
            List<String> daoFields = Arrays.stream(tool.getDeclaredFields())
                    .filter(f -> AgentToolDao.class.isAssignableFrom(f.getType()))
                    .map(java.lang.reflect.Field::getName)
                    .toList();
            assertEquals(List.of(), daoFields, tool.getSimpleName() + " 又拿回了 DAO ⇒ 进程内 INSERT 那条腿复活");
        }
        List<String> writeMethods = Arrays.stream(AgentToolDao.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("insert") || m.getName().startsWith("update")
                        || m.getName().startsWith("delete"))
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertEquals(List.of(), writeMethods,
                "AgentToolDao 又出现写方法 ⇒ D-B4-49 余项写腿复发；agent 侧变更一律经属主端点");
    }

    @Test
    @DisplayName("supports 只认在册工具码（工具路由外形不变）")
    void supportsOnlyTheRegisteredToolCodes() {
        TriggerPipelineTool t = new TriggerPipelineTool(
                new RecordingRestTemplate(null, null), new StubResolver(Optional.empty()));
        assertTrue(t.supports("TriggerPipeline"));
        assertFalse(t.supports("TriggerPipelineV2"));

        RunQualityCheckTool q = new RunQualityCheckTool(
                new RecordingRestTemplate(null, null), new StubResolver(Optional.empty()));
        assertTrue(q.supports("RunQualityCheck"));
        assertFalse(q.supports("QualityCheck"));
    }

    // ── 词表单源自身的判据（状态行/信封两路都覆盖到八态）──

    @Test
    @DisplayName("Outcome：八态齐备，succeeded 只有 DATA_RETURNED/ACCEPTED，负态 available 恒 false")
    void vocabularyCoversEveryOwnerAnswerShape() {
        List<String> names = Arrays.stream(Outcome.values()).map(Outcome::name).toList();
        assertEquals(List.of("GUARD_REJECTED", "UNREACHABLE", "DENIED", "SECURITY_UNAVAILABLE",
                        "OWNER_CHANNEL_CLOSED", "ENGINE_FAILED", "DATA_RETURNED", "ACCEPTED"), names,
                "词表增减一个态都必须同批登记进台账与门禁基线");

        for (Outcome o : Outcome.values()) {
            Map<String, Object> payload = Outcome.payload(o, "x");
            assertEquals(o.succeeded(), payload.get(AgentToolResponses.KEY_AVAILABLE),
                    o.name() + " 的 available 与 succeeded() 不符");
            assertEquals(o.name(), payload.get(AgentToolResponses.KEY_STATE));
        }
        assertEquals(2, Arrays.stream(Outcome.values()).filter(Outcome::succeeded).count(),
                "成功出口只有『取到数据』与『属主受理』两件不同的事");

        assertEquals(Outcome.DENIED, Outcome.fromHttpStatus(401));
        assertEquals(Outcome.DENIED, Outcome.fromHttpStatus(403));
        assertEquals(Outcome.SECURITY_UNAVAILABLE, Outcome.fromHttpStatus(503));
        assertEquals(Outcome.OWNER_CHANNEL_CLOSED, Outcome.fromHttpStatus(409));
        assertEquals(Outcome.OWNER_CHANNEL_CLOSED, Outcome.fromHttpStatus(410));
        assertEquals(Outcome.ENGINE_FAILED, Outcome.fromHttpStatus(500));

        assertEquals(Outcome.DATA_RETURNED, Outcome.fromEnvelope(0, null), "读腿缺省终态不许被写腿词表带偏");
        assertEquals(Outcome.ACCEPTED, Outcome.fromEnvelope(0, null, Outcome.ACCEPTED));
        assertEquals(Outcome.UNREACHABLE, Outcome.fromEnvelope(null, null), "没答不是拒绝");
        assertEquals(Outcome.SECURITY_UNAVAILABLE,
                Outcome.fromEnvelope(403, RlsExecutionContract.SEC_UNAVAILABLE_CODE, Outcome.ACCEPTED),
                "errorCode 键面优先于同形 code，护栏不可用不许折成拒绝（§4.7.2）");
    }

    @Test
    @DisplayName("OwnerPostLeg 路径段判据：允许标识符形态，拒绝分隔符/空白/超长")
    void pathSegmentJudgementIsExact() {
        for (String ok : List.of("task-1", "a_b.c:d", "T0001", "x".repeat(64))) {
            assertTrue(OwnerPostLeg.safePathSegment(ok), "合法 id 被误挡: " + ok);
        }
        for (String bad : Arrays.asList(null, "", " ", "a/b", "a?b", "a%20b", "a b", "a\nb", "x".repeat(65))) {
            assertFalse(OwnerPostLeg.safePathSegment(bad), "非法路径段被放行: " + bad);
        }
    }
}
