package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.security.AgentToolPolicyGate;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-01（REQ-AI-01，P0 红线）验收 —— 全工具类型裁决咽喉。
 *
 * <p>本类落实 doc C 章 4.1 的两条不变式（要求"写进测试而非注释"）：</p>
 * <ul>
 *   <li><b>I-1</b> 任何一次工具执行 ↔ 恰好一条裁决 —— 断言 {@code gate.review} 恰 1 次；</li>
 *   <li><b>I-2</b> {@code decision != ALLOW} ⇒ 无出向调用 —— 断言注册表未执行、DAO 未查询、
 *       HTTP/文件 IO 未发生。</li>
 * </ul>
 *
 * <p>为避免"DENY 断言恒真"，每个工具都跑 ALLOW 与 DENY 两遍：ALLOW 桩下必须真的落到
 * dispatch（正向对照），DENY 桩下必须停在裁决阶段（红线断言）。</p>
 *
 * <p><b>15 个工具名口径</b>（F06-01 陈述的"全部工具类型 × 全部来源"）：内置注册表 6
 * （{@code query_db}/{@code invoke_rest}/{@code read_file}/{@code write_file}/{@code patch}/
 * {@code delegate_to_agent}）+ BUILTIN 直供 2（{@code getCurrentTime}/{@code calculateRisk}）
 * + DB 注册表定义 3 形态（SQL/REST/BUILTIN）+ F06-11 场景工具 4（{@code forecast.query}/
 * {@code forecast.drilldown}/{@code forecast.explain}/{@code action.createDraft}）；
 * 第 5 个场景写工具 {@code scenario.copyCompare} 与非 SQL 面剩余的 {@code search_files}
 * 由 {@link #nonSqlToolsAreAlsoAdjudicated} 覆盖。</p>
 */
class ToolGuardrailChokepointTest {

    private static final String USER = "u-9";
    private static final String TENANT = "t-1";
    private static final String SQL_TEXT =
            "SELECT id, amount FROM ecos_dw.contract_amount LIMIT 10";

    private record ToolCase(String name, String source, String toolType, String schemaJson) {

        static ToolCase registry(String name) {
            return new ToolCase(name, "registry", "REGISTRY", null);
        }

        static ToolCase fallback(String name) {
            return new ToolCase(name, "fallback", "BUILTIN", null);
        }

        static ToolCase database(String name, String toolType, String schemaJson) {
            return new ToolCase(name, "database", toolType, schemaJson);
        }
    }

    private static final List<ToolCase> TOOLS = List.of(
            ToolCase.registry("query_db"),
            ToolCase.registry("invoke_rest"),
            ToolCase.registry("read_file"),
            ToolCase.registry("write_file"),
            ToolCase.registry("patch"),
            ToolCase.registry("delegate_to_agent"),
            ToolCase.fallback("getCurrentTime"),
            ToolCase.fallback("calculateRisk"),
            ToolCase.database("db_sql_report", "SQL", "{\"sql\":\"" + SQL_TEXT + "\"}"),
            ToolCase.database("db_rest_lookup", "REST", null),
            ToolCase.database("db_builtin_report", "BUILTIN", null),
            ToolCase.registry("forecast.query"),
            ToolCase.registry("forecast.drilldown"),
            ToolCase.registry("forecast.explain"),
            ToolCase.registry("action.createDraft"));

    // ── 测试装置：每个 case 一套独立 mock，保证"恰 1 次"计数不被上一轮污染 ──

    private static final class Fixture {
        final ToolDao toolDao = mock(ToolDao.class);
        final AgentToolPolicyGate gate = mock(AgentToolPolicyGate.class);
        final ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        final ToolRegistry registry = mock(ToolRegistry.class);
        final ToolExecutorService service = new ToolExecutorService();

        Fixture() {
            service.setToolDao(toolDao);
            service.setPolicyGate(gate);
            service.setSubjectResolver(subject);
            ReflectionTestUtils.setField(service, "toolRegistry", registry);
            when(subject.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                    USER, List.of("analyst"), "dept-a", TENANT));
        }

        void stub(ToolCase c) {
            if ("registry".equals(c.source())) {
                when(registry.has(c.name())).thenReturn(true);
                when(registry.execute(eq(c.name()), any()))
                        .thenReturn(ToolResult.ok("call_" + c.name(), c.name(),
                                "{\"rows\":[],\"count\":0}", 1L));
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", c.name());
                row.put("name", c.name());
                row.put("description", "test tool " + c.name());
                row.put("tool_type", c.toolType());
                row.put("endpoint_url", "REST".equals(c.toolType())
                        ? "http://127.0.0.1:1/unreachable" : null);
                row.put("http_method", "REST".equals(c.toolType()) ? "GET" : null);
                row.put("schema_json", c.schemaJson());
                when(toolDao.loadToolDefinition(c.name())).thenReturn(List.of(row));
                when(toolDao.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            }
        }

        void verdict(ToolDecision decision) {
            when(gate.review(any(), any(), any())).thenReturn(decision);
        }

        void assertExactlyOneAdjudication(ToolCase c) {
            verify(gate, times(1)).review(any(), any(), any());
        }

        /** I-2 观测面：DENY/FAIL_CLOSED 下不得有任何出向执行副作用 */
        void assertNoDispatchSideEffects(ToolCase c) {
            if ("registry".equals(c.source())) {
                verify(registry, never()).execute(anyString(), any());
            }
            verify(toolDao, never()).queryForList(anyString(), any());
        }

        /** 正向对照：ALLOW 下确实走到了 dispatch（注册表/SQL 形态可精确观测） */
        void assertDispatched(ToolCase c) {
            if ("registry".equals(c.source())) {
                verify(registry, times(1)).execute(eq(c.name()), any());
            } else if ("database".equals(c.source()) && "SQL".equals(c.toolType())) {
                verify(toolDao, times(1)).queryForList(anyString(), any());
            }
        }
    }

    // ── 验收 1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-01 15 个工具名各恰好 1 次裁决；ALLOW 才执行、拒绝态零执行（I-1/I-2）")
    void everyToolTypeReceivesExactlyOneEvaluate() {
        for (ToolCase c : TOOLS) {
            // 正向对照：ALLOW → 裁决 1 次 + 确实进入 dispatch
            Fixture allow = new Fixture();
            allow.stub(c);
            allow.verdict(ToolDecision.allow("p-allow", null, 1L));
            ToolResult allowed = allow.service.execute(c.name(), Map.of("scenarioId", "s-1"));
            allow.assertExactlyOneAdjudication(c);
            allow.assertDispatched(c);
            assertNotNull(allowed, c.name());

            // 红线：DENY → 仍裁决 1 次（无条件裁决，废止 toolType 前置条件），但零执行
            Fixture deny = new Fixture();
            deny.stub(c);
            deny.verdict(ToolDecision.deny("策略拒绝", "p-deny", 1L));
            ToolResult denied = deny.service.execute(c.name(), Map.of("scenarioId", "s-1"));
            deny.assertExactlyOneAdjudication(c);
            deny.assertNoDispatchSideEffects(c);
            assertFalse(denied.isSuccess(), "DENY 必须拒绝执行: " + c.name());
            assertEquals("GUARDRAIL_DENIED", denied.getErrorClass(),
                    "DENY 不得折叠成 FAIL_CLOSED: " + c.name());
            assertEquals("DENY", denied.getDecision(), c.name());

            // 红线：FAIL_CLOSED（不可用）与 DENY 分态，同样零执行
            Fixture failClosed = new Fixture();
            failClosed.stub(c);
            failClosed.verdict(ToolDecision.failClosed("security-engine 不可用", 1L));
            ToolResult blocked = failClosed.service.execute(c.name(), Map.of("scenarioId", "s-1"));
            failClosed.assertExactlyOneAdjudication(c);
            failClosed.assertNoDispatchSideEffects(c);
            assertFalse(blocked.isSuccess(), "FAIL_CLOSED 必须拒绝执行: " + c.name());
            assertEquals("GUARDRAIL_FAIL_CLOSED", blocked.getErrorClass(), c.name());
            assertEquals("FAIL_CLOSED", blocked.getDecision(), c.name());
        }
    }

    // ── 验收 2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F06-01 非 SQL 工具在全 DENY 桩下各 1 次裁决、0 次执行")
    void nonSqlToolsAreAlsoAdjudicated() {
        List<String> nonSql = List.of("invoke_rest", "read_file", "write_file", "patch",
                "delegate_to_agent", "search_files", "scenario.copyCompare");
        Fixture f = new Fixture();
        for (String name : nonSql) {
            when(f.registry.has(name)).thenReturn(true);
            when(f.registry.execute(eq(name), any()))
                    .thenReturn(ToolResult.ok("c_" + name, name, "{}", 1L));
        }
        f.verdict(ToolDecision.deny("无权限", "p-deny", 1L));

        for (String name : nonSql) {
            ToolResult r = f.service.execute(name,
                    Map.of("path", "workspace/a.txt", "content", "x", "url", "http://127.0.0.1:1/x"));
            assertFalse(r.isSuccess(), "非 SQL 工具在 DENY 下不得执行: " + name);
            assertEquals("GUARDRAIL_DENIED", r.getErrorClass(), name);
        }
        ArgumentCaptor<ToolAdjudicationRequest> captor =
                ArgumentCaptor.forClass(ToolAdjudicationRequest.class);
        verify(f.gate, times(nonSql.size())).review(captor.capture(), any(), any());
        List<String> adjudicated = captor.getAllValues().stream().map(ToolAdjudicationRequest::tool).toList();
        assertTrue(adjudicated.containsAll(nonSql), "非 SQL 工具必须全部进入裁决闸: " + adjudicated);
        verify(f.registry, never()).execute(anyString(), any());
        verify(f.toolDao, never()).queryForList(anyString(), any());
    }

    // ── 验收 3（X-14：原 catch → return null 放行） ──────────────────────

    @Test
    @DisplayName("F06-01/X-14 沙盒审查器异常 → FAIL_CLOSED 拒绝执行（不再放行）")
    void sandboxReviewerFailureYieldsDenyNotPass() {
        Fixture f = new Fixture();
        f.stub(ToolCase.registry("query_db"));
        f.verdict(ToolDecision.allow("p-allow", null, 1L));
        when(f.toolDao.querySandboxMandatory(USER)).thenThrow(new RuntimeException("db down"));

        ToolResult r = f.service.execute("query_db", Map.of("sql", "DROP TABLE ecos_dw.contract"));

        assertFalse(r.isSuccess(), "审查器故障必须拒绝执行");
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(),
                "审查器故障属不可用态而非策略 DENY: " + r.getError());
        assertEquals("FAIL_CLOSED", r.getDecision());
        f.assertExactlyOneAdjudication(ToolCase.registry("query_db"));
        verify(f.registry, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("R1.3/H8-T4 高危指令 + SecuritySandboxService 不可用 → FAIL_CLOSED 且留痕")
    void sandboxServiceUnavailableYieldsDenyNotPass() {
        Fixture f = new Fixture();
        f.stub(ToolCase.registry("query_db"));
        f.verdict(ToolDecision.allow("p-allow", null, 1L));
        when(f.toolDao.querySandboxMandatory(USER))
                .thenReturn(List.of(Map.of("sandbox_mandatory", Boolean.TRUE)));
        ApplicationContext appCtx = mock(ApplicationContext.class);
        when(appCtx.getBeansOfType(Object.class)).thenReturn(Map.of());
        ReflectionTestUtils.setField(f.service, "applicationContext", appCtx);

        ToolResult r = f.service.execute("query_db", Map.of("sql", "DELETE FROM ecos_dw.contract"));

        assertFalse(r.isSuccess(), "沙盒服务不可用时高危指令不得执行");
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass());
        verify(f.toolDao, times(1)).insertSandboxAudit(eq(USER), eq("query_db"), anyString());
        verify(f.registry, never()).execute(anyString(), any());
    }

    // ── 咽喉自身缺件：闸缺失/主体缺失都必须 FAIL_CLOSED（§2.4-6） ──────────

    @Test
    @DisplayName("F06-01 裁决闸未装配 → 任何工具 FAIL_CLOSED 且不执行（无'闸缺失即放行'路径）")
    void missingGateIsFailClosedNotPass() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.has("read_file")).thenReturn(true);
        ToolExecutorService noGate = new ToolExecutorService();
        ReflectionTestUtils.setField(noGate, "toolRegistry", registry);
        ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        when(subject.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                USER, List.of(), null, TENANT));
        noGate.setSubjectResolver(subject);

        ToolResult r = noGate.execute("read_file", Map.of("path", "a.txt"));

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass());
        verify(registry, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("F06-02 主体解析器未装配 → FAIL_CLOSED，且裁决闸根本不被调用")
    void missingSubjectResolverIsFailClosed() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.has("invoke_rest")).thenReturn(true);
        ToolExecutorService svc = new ToolExecutorService();
        ReflectionTestUtils.setField(svc, "toolRegistry", registry);
        AgentToolPolicyGate gate = mock(AgentToolPolicyGate.class);
        svc.setPolicyGate(gate);

        ToolResult r = svc.execute("invoke_rest", Map.of("url", "http://127.0.0.1:1/x"));

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass());
        verify(gate, never()).review(any(), any(), any());
        verify(registry, never()).execute(anyString(), any());
    }

    @Test
    @DisplayName("F06-02 token 主体为空 → 真裁决闸判 FAIL_CLOSED（不可解析 ≠ 策略 DENY）")
    void anonymousSubjectIsFailClosedNotDeny() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.has("forecast.query")).thenReturn(true);
        when(registry.execute(eq("forecast.query"), any()))
                .thenReturn(ToolResult.ok("c", "forecast.query", "{}", 1L));
        ToolSubjectResolver subject = mock(ToolSubjectResolver.class);
        when(subject.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                null, List.of(), null, null));

        FixtureProbe probe = new FixtureProbe();
        ToolExecutorService svc = new ToolExecutorService();
        ReflectionTestUtils.setField(svc, "toolRegistry", registry);
        svc.setSubjectResolver(subject);
        svc.setPolicyGate(probe.gate());

        ToolResult r = svc.execute("forecast.query", Map.of("forecastRunId", "run-1"));

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(),
                "无主体无法裁决，属不可用态： " + r.getError());
        assertEquals("FAIL_CLOSED", r.getDecision());
        verify(probe.publisher, times(1)).auditOrThrow(any(), any());
        verify(registry, never()).execute(anyString(), any());
    }

    // ── F06-14：拒绝文案面（X-49 禁原文上浮） ────────────────────────────

    @Test
    @DisplayName("F06-14 拒绝结果只带模板文案 + 诊断码，禁带原始拒绝原因")
    void denyMessageCarriesDiagnosticCodeNotRawReason() {
        Fixture f = new Fixture();
        f.stub(ToolCase.registry("forecast.query"));
        f.verdict(ToolDecision.failClosed("OPA 返回 500：表 ecos_dw.secret 未授权", 1L));

        ToolResult r = f.service.execute("forecast.query", Map.of("forecastRunId", "run-1"));

        assertNotNull(r.getErrorCode());
        assertNotNull(r.getTraceId(), "错误必须带诊断码");
        assertFalse(r.getError().contains("secret"),
                "原始拒绝原因禁随响应体上浮（X-49）: " + r.getError());
        assertFalse(r.getError().contains("500："), "同上: " + r.getError());
    }

    /** 真闸 + 真审计出口（mock 通道）的最小装配，用于验证闸内 fail-closed 语义。 */
    private static final class FixtureProbe {
        final com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient client =
                mock(com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient.class);
        final com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher publisher =
                mock(com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher.class);
        final com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist whitelist =
                mock(com.chinacreator.gzcm.engine.ai.security.AgentToolSqlWhitelist.class);
        @SuppressWarnings("unchecked")
        final org.springframework.beans.factory.ObjectProvider<
                com.chinacreator.gzcm.engine.ai.dao.ToolPermissionBindingDao> bindings =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        private final AgentToolPolicyGate gate =
                new AgentToolPolicyGate(whitelist, client, publisher, bindings);

        AgentToolPolicyGate gate() {
            return gate;
        }
    }
}
