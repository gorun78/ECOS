package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.security.sql.PhysicalQueryGuard;
import com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-01 / F06-02 / F06-03 的裁决闸验收（{@code AgentToolPolicyGate}）。
 *
 * <p>锚点：</p>
 * <ul>
 *   <li><b>X-19 / W145</b>：ABAC 裁决超时预算 5000ms → <b>2000ms</b>，超时落
 *       {@code GUARDRAIL_FAIL_CLOSED}（用真 HTTP 慢响应服务器实测，不是抄配置常量）；</li>
 *   <li><b>doc §4.2 决策树</b>逐分支：显式 ALLOW 才放行；明确 DENY → 403；
 *       {@code decision} 缺失/未知取值 → FAIL_CLOSED（禁"默认放行"兜底）；
 *       security 侧 {@code source=fallback}（OPA 自身兜底态）→ FAIL_CLOSED 而非 DENY，
 *       503 与 403 禁折叠；</li>
 *   <li><b>SQL 本地地板</b>：命中即 DENY 且不发起 HTTP（附加防御、省预算），
 *       但"通过地板"绝不构成放行依据（放行权只在 security，§2.4-7）；</li>
 *   <li><b>SQL 原文不入载荷</b>（F06-02 要点 1）：只送 {@code sqlDigest} 与结构派生值。</li>
 * </ul>
 */
class AgentToolPolicyGateTest {

    private static final String USER = "u-9";
    private static final String TENANT = "t-1";

    private HttpServer slowServer;
    private final AtomicInteger served = new AtomicInteger();

    private final AgentToolSqlWhitelist whitelist = mock(AgentToolSqlWhitelist.class);
    private final AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);
    private final GuardrailAuditPublisher publisher = mock(GuardrailAuditPublisher.class);

    @AfterEach
    void tearDown() {
        if (slowServer != null) {
            slowServer.stop(0);
            slowServer = null;
        }
    }

    // ── 装置 ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private static ToolAdjudicationRequest request() {
        return new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject(USER, List.of("analyst"), "dept-a", TENANT),
                new ToolAdjudicationRequest.Action(
                        ToolAdjudicationRequest.ACTION_TYPE, "query_db", "read"),
                ToolAdjudicationRequest.projectResource(Map.of("scenarioId", "s-1")),
                new ToolAdjudicationRequest.Environment(
                        "trace-fixed", ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-08T00:00:00Z"));
    }

    private AgentToolPolicyGate gate() {
        return new AgentToolPolicyGate(whitelist, client, publisher,
                emptyProvider() /* ObjectProvider<ToolPermissionBindingDao> */);
    }

    private void stubEvaluate(Map<String, Object> data) throws Exception {
        // doAnswer/doReturn 形态：重复桩配时不会触发上一轮 answer（when() 会以 null 实参调用桩）
        doAnswer(inv -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("policy", "rbac");
            body.put("input", inv.getArgument(0));
            return body;
        }).when(client).evaluateBodyForAdjudication(any());
        doReturn(data).when(client).evaluateData(any());
    }

    // ── 验收 1：2s 预算（X-19 / W145） ───────────────────────────────────

    @Test
    @DisplayName("F06-01/X-19 裁决预算 2000ms；真慢响应下按时中断并判 FAIL_CLOSED")
    void timeoutTwoSecondsFailClosed() throws Exception {
        assertEquals(2000, AiSecurityEngineClient.POLICY_TIMEOUT_MS,
                "PRD-06 §1.3 裁决超时预算必须是 2s（废止原 5000ms）");

        slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext("/", exchange -> {
            served.incrementAndGet();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] payload = "{\"code\":200,\"data\":{\"allow\":true}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        slowServer.start();

        @SuppressWarnings("unchecked")
        ObjectProvider<EventBusService> busProvider = mock(ObjectProvider.class);
        AiSecurityEngineClient realClient = new AiSecurityEngineClient(
                "http://127.0.0.1:" + slowServer.getAddress().getPort(),
                AiSecurityEngineClient.POLICY_TIMEOUT_MS, busProvider);
        assertEquals(2000, realClient.policyTimeoutMs(), "客户端必须携带 2s 预算");

        AgentToolPolicyGate tested = new AgentToolPolicyGate(
                whitelist, realClient, publisher, emptyProvider());

        long start = System.currentTimeMillis();
        ToolDecision decision = tested.review(request());
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(served.get() >= 1, "请求应确实到达服务端（证明是读超时而非连不上）");
        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, decision.verdict(),
                "security 超时必须落 FAIL_CLOSED，禁折叠成 DENY 更禁默认放行");
        assertEquals(AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                AgentToolPolicyGate.errorClassFor(decision));
        assertTrue(elapsed < 2900,
                "预算 2s：不得等满服务端 3s 慢响应（实测 " + elapsed + "ms）");
        assertTrue(elapsed >= 1500,
                "应真的等到读超时才判定，而非立即失败: " + elapsed + "ms");
        verify(publisher, times(1)).auditOrThrow(any(), any());
    }

    // ── 验收 2：§4.2 决策树逐分支 ────────────────────────────────────────

    @Test
    @DisplayName("§4.2 显式 ALLOW + obligations 可解析 → 放行并携带义务")
    void explicitAllowCarriesObligations() throws Exception {
        stubEvaluate(Map.of("allow", true, "policyId", "p-1", "source", "opa",
                "obligations", List.of(Map.of("column", "phone", "rule", "phone"))));

        ToolDecision d = gate().review(request());

        assertEquals(ToolDecision.Verdict.ALLOW, d.verdict());
        assertEquals("p-1", d.policyId());
        assertEquals(1, d.obligations().mask().size());
        assertEquals("phone", d.obligations().mask().get(0).column());
        assertEquals("phone", d.obligations().mask().get(0).rule());
    }

    @Test
    @DisplayName("§4.2 明确 DENY → GUARDRAIL_DENIED(403)，不折叠成 FAIL_CLOSED")
    void explicitDenyStaysDeny() throws Exception {
        stubEvaluate(Map.of("allow", false, "policyId", "p-2", "source", "opa"));

        ToolDecision d = gate().review(request());

        assertEquals(ToolDecision.Verdict.DENY, d.verdict());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, AgentToolPolicyGate.errorClassFor(d));
    }

    @Test
    @DisplayName("§4.2 缺 decision/allow 字段 → FAIL_CLOSED（禁默认放行兜底）")
    void missingVerdictFieldIsFailClosed() throws Exception {
        stubEvaluate(Map.of("policyId", "p-3", "source", "opa"));

        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, gate().review(request()).verdict());
    }

    @Test
    @DisplayName("§4.2 decision 未知取值 → FAIL_CLOSED")
    void unknownVerdictValueIsFailClosed() throws Exception {
        stubEvaluate(Map.of("decision", "MAYBE", "source", "opa"));

        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, gate().review(request()).verdict());
    }

    @Test
    @DisplayName("§4.2 security 侧 OPA 兜底态（source=fallback）→ FAIL_CLOSED 而非 DENY")
    void opaFallbackSourceIsFailClosedNotDeny() throws Exception {
        stubEvaluate(Map.of("allow", false, "source", "fallback",
                "obligations", List.of(Map.of("obligation", "DENIED",
                        "deniedReason", "OPA_UNAVAILABLE_FAIL_CLOSED"))));

        ToolDecision d = gate().review(request());

        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, d.verdict(),
                "security 自身不可用不是策略裁决，必须归 503 FAIL_CLOSED");
        assertNotNull(d.reason());
    }

    @Test
    @DisplayName("§2.4-6 无 token 主体 → FAIL_CLOSED 且不发 HTTP（无法裁决）")
    void anonymousSubjectFailsClosedBeforeHttp() throws Exception {
        ToolAdjudicationRequest anon = new ToolAdjudicationRequest(
                new ToolAdjudicationRequest.Subject(null, List.of(), null, null),
                new ToolAdjudicationRequest.Action(
                        ToolAdjudicationRequest.ACTION_TYPE, "query_db", "read"),
                ToolAdjudicationRequest.projectResource(Map.of()),
                new ToolAdjudicationRequest.Environment(
                        "trace-anon", ToolAdjudicationRequest.CHANNEL_AGENT, "2026-10-08T00:00:00Z"));

        ToolDecision d = gate().review(anon);

        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, d.verdict());
        verify(client, never()).evaluateData(any());
    }

    // ── 验收 3：SQL 本地地板 = 附加防御 ─────────────────────────────────

    @Test
    @DisplayName("F06-01 SQL 地板命中（DROP）→ DENY 且不发起 security 调用")
    void sqlLocalFloorDeniesWithoutReachingSecurity() throws Exception {
        ToolDecision d = gate().review(request(), "DROP TABLE ecos_dw.contract", List.of());

        assertEquals(ToolDecision.Verdict.DENY, d.verdict());
        assertEquals(AiErrorClass.GUARDRAIL_DENIED, AgentToolPolicyGate.errorClassFor(d));
        verify(client, never()).evaluateData(any());
        verify(publisher, times(1)).auditOrThrow(any(), any());
    }

    @Test
    @DisplayName("F06-01 多语句 / 注释 / 未列表白名单 → 本地地板 DENY")
    void multiStatementCommentAndUnlistedTableAreDeniedLocally() throws Exception {
        assertEquals(ToolDecision.Verdict.DENY,
                gate().review(request(), "SELECT id FROM ecos_dw.a; DROP TABLE ecos_dw.b", List.of())
                        .verdict());
        assertEquals(ToolDecision.Verdict.DENY,
                gate().review(request(), "SELECT id FROM ecos_dw.a -- tail", List.of()).verdict());
        // 表未列入白名单：生产契约是 allowedColumns 返回 null（Mockito 对 Set 返回值默认给空集合，
        // 那是装置假象而非线上语义，必须显式桩成 null，否则"未授权表"会被误当成"授权但无列"）
        when(whitelist.allowedColumns("ecos_dw.unlisted_table")).thenReturn(null);
        assertEquals(ToolDecision.Verdict.DENY,
                gate().review(request(), "SELECT id FROM ecos_dw.unlisted_table", List.of()).verdict());
        verify(client, never()).evaluateData(any());
    }

    @Test
    @DisplayName("D-B4-60 对账：同一串危险 SQL 在工具面与属主查表面拿到同一句前置理由（单源，不是两套）")
    void readOnlyFloorSharesOneSourceWithPhysicalQueryFace() throws Exception {
        // 合流前这两套判据各有一批对方没有的词：checkpoint 只有旧工具面知道，
        // 而 dblink_connect 在旧查表面靠裸前缀（连表名一起误拒）、在旧工具面靠具名清单（清单外即漏判）。
        // 本例钉的是「两面命中同一句理由」——日后任何一侧私自加词或换文案，这里立刻变红。
        for (String sql : new String[] {
                "SELECT id FROM DW_Sales.t WHERE checkpoint = 1",
                "SELECT dblink_connect('x') FROM DW_Sales.t",
                "SELECT id FROM DW_Sales.t; DELETE FROM DW_Sales.t",
                "SELECT id FROM DW_Sales.t /* cut */"}) {
            ToolDecision d = gate().review(request(), sql, List.of());
            PhysicalQueryGuard.Decision q = PhysicalQueryGuard.inspect(sql);

            assertEquals(ToolDecision.Verdict.DENY, d.verdict(), sql);
            assertFalse(q.allowed(), sql);
            String shared = q.reason().substring(PhysicalQueryGuard.PREFIX.length());
            assertTrue(d.reason().contains(shared),
                    "工具面理由应内嵌单源那句「" + shared + "」，实际: " + d.reason());
        }
        verify(client, never()).evaluateData(any());
    }

    @Test
    @DisplayName("D-B4-60 差异只剩一个参数：WITH 在工具面进白名单腿，在查表面被前置拒")
    void withHeadIsAllowedOnToolFaceButNotOnQueryFace() throws Exception {
        stubEvaluate(Map.of("allow", true));
        String cte = "WITH c AS (SELECT id FROM DW_Sales.t) SELECT id FROM c";

        assertEquals(ToolDecision.Verdict.ALLOW, gate().review(request(), cte, List.of()).verdict(),
                "工具面按既有语义允许 CTE（allowWith=true），仍须 ABAC 才放行");

        PhysicalQueryGuard.Decision q = PhysicalQueryGuard.inspect(cte);
        assertFalse(q.allowed());
        assertTrue(q.reason().contains("SELECT 起首"), q.reason());
    }

    @Test
    @DisplayName("F06-02 SQL 原文不入裁决载荷，只送 sqlDigest/readOnly/tables 派生值")
    void sqlTextNeverEntersAdjudicationPayload() throws Exception {
        stubEvaluate(Map.of("allow", true));
        String secretSql = "SELECT salary FROM ecos_dw.hr_payroll WHERE dept = 'FINANCE-SECRET'";

        ToolDecision d = gate().review(request(), secretSql, List.of());

        assertEquals(ToolDecision.Verdict.ALLOW, d.verdict(), "地板通过后仍须 security 才放行");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).evaluateBodyForAdjudication(captor.capture());
        Map<String, Object> input = captor.getValue();
        assertTrue(input.containsKey("sqlDigest"), "载荷应带摘要: " + input.keySet());
        assertEquals(Boolean.TRUE, input.get("readOnly"));
        String rendered = String.valueOf(input);
        assertFalse(rendered.contains("FINANCE-SECRET"), "字面量禁入载荷");
        assertFalse(rendered.contains("SELECT salary"), "SQL 原文禁入载荷（F06-02 要点 1）");
    }

    // ── 验收 4：审计红线 + obligations 委托 ─────────────────────────────

    @Test
    @DisplayName("F06-03 审计通道不可用 → ALLOW 也转 FAIL_CLOSED（不接受尽力而为）")
    void auditFailureTurnsAllowIntoFailClosed() throws Exception {
        stubEvaluate(Map.of("allow", true, "policyId", "p-9"));
        org.mockito.Mockito.doThrow(new GuardrailAuditUnavailableException(
                "query_db", "EventBusService 缺失", new IllegalStateException()))
                .when(publisher).auditOrThrow(any(), any());

        assertEquals(ToolDecision.Verdict.FAIL_CLOSED, gate().review(request()).verdict(),
                "红线审计失败必须让裁决整体失败（X-20 的 warn-continue 形态已废）");
    }

    @Test
    @DisplayName("F06-03 三态各发一次审计（不重不漏，§1.5-1 对账前提）")
    void eachVerdictAuditsExactlyOnce() throws Exception {
        stubEvaluate(Map.of("allow", true));
        gate().review(request());
        stubEvaluate(Map.of("allow", false));
        gate().review(request());
        when(client.evaluateData(any())).thenThrow(new IllegalStateException("security down"));
        gate().review(request());

        verify(publisher, times(3)).auditOrThrow(any(), any());
        verify(client, times(3)).evaluateData(any());
    }

    @Test
    @DisplayName("F06-02 脱敏义务交 security 执行；security 不可用则整条结果丢弃")
    void maskObligationDelegatesToSecurityAndFailsClosed() throws Exception {
        stubEvaluate(Map.of("allow", true,
                "obligations", List.of(Map.of("column", "phone", "rule", "phone"))));
        when(client.applyMasking(anyList(), anyList())).thenThrow(
                new AiSecurityEngineClient.SecurityResponseUnparsableException("masking/apply 503"));

        ToolDecision d = gate().review(request());
        assertEquals(ToolDecision.Verdict.ALLOW, d.verdict());

        ToolObligationApplier applier =
                new ToolObligationApplier(client, new ObjectMapper(), new AiToolColumnSurfaceGate(client));
        ToolObligationApplier.Outcome outcome = applier.apply("query_db",
                "{\"rows\":[{\"phone\":\"13800001111\"}],\"count\":1}",
                d.obligations(),
                new ToolAdjudicationRequest.Subject(USER, List.of("viewer"), null, TENANT),
                null);

        assertFalse(outcome.applied(), "security 脱敏不可用时整条结果丢弃（禁先返回再说明）");
        assertNotNull(outcome.failReason());
        assertNull(outcome.json());
        verify(client, times(1)).applyMasking(anyList(), anyList());
    }

    // ── 形态类断言（单一构造器 / 声明面 / 错误码） ───────────────────────

    @Test
    @DisplayName("F06-02 四段载荷只能用一个构造器（缺一即编译不过）")
    void singleConstructorRequiresFourSections() {
        var ctors = ToolAdjudicationRequest.class.getDeclaredConstructors();
        assertEquals(1, ctors.length, "四段载荷必须只有单一构造器");
        assertEquals(4, ctors[0].getParameterCount());
    }

    @Test
    @DisplayName("F06-14 错误分类映射：ALLOW 无错误码，DENY/FAIL_CLOSED 各自分态")
    void errorClassMappingIsThreeWay() {
        assertNull(AgentToolPolicyGate.errorClassFor(ToolDecision.allow("p", null, 1L)));
        assertEquals(AiErrorClass.GUARDRAIL_DENIED,
                AgentToolPolicyGate.errorClassFor(ToolDecision.deny("r", "p", 1L)));
        assertEquals(AiErrorClass.GUARDRAIL_FAIL_CLOSED,
                AgentToolPolicyGate.errorClassFor(ToolDecision.failClosed("r", 1L)));
        assertEquals("ECOS-AI-403", AiErrorClass.GUARDRAIL_DENIED.errorCode());
        assertEquals("ECOS-AI-503", AiErrorClass.GUARDRAIL_FAIL_CLOSED.errorCode());
    }

    @Test
    @DisplayName("R-18 ② 权限声明 DAO 缺失时声明集为空（不影响三态判定）")
    void declaredAttributesEmptyWhenDaoAbsent() {
        assertEquals(0, gate().declaredRequiredAttributes("query_db").size());
        assertNotNull(ToolPermissionBindingDao.class.getName());
    }
}
