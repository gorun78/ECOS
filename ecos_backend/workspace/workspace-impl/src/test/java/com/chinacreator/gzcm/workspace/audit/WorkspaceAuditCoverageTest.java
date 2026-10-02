package com.chinacreator.gzcm.workspace.audit;

import com.chinacreator.gzcm.common.context.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F07-18 / C164 审计闭包收口 — REQ-API（§8.1）点名
 * {@code WorkspaceAuditCoverageTest#sensitiveReadsAreAudited} ·
 * {@code #auditPayloadCarriesIdsNotResultBody} · {@code #knowledgeAndWorkbookControllersAreInsideAuditScope}。
 *
 * <p>覆盖 F07-18 要点 2（workspace 拥有、离线可交付、<b>不</b>依赖分册 01 契约）：</p>
 * <ul>
 *   <li>审计域由 {@code ...workspace.controller} 收窄面扩到 workspace <b>全子包</b>（knowledge/workbook/query 盲区，X-11）；</li>
 *   <li>读侧 GET 按 <b>C-6 敏感读判定表</b>审计（场景域 completeness/graph/runs/认知×4/列表/详情/决策），
 *       {@code /binding-catalog} 静态元数据与对象域（R-29）<b>不</b>审计（防全 GET 风暴）；</li>
 *   <li>E-5 载荷红线：读侧只带 ID + 分类 + subject + traceId + 状态，<b>禁带结果原文/行数/coverage</b>。</li>
 * </ul>
 *
 * <p>ABAC/RLS/列过滤读侧接线（F07-18 要点 4 的 policyId/obligations 口径由分册 01 SEC-02/03
 * 定义）仍记阻塞，本类<b>不</b>越界断言。servlet 接口用 Mockito stub（workspace-impl 测试类路径
 * 无 spring-test，与 {@code WorkspaceTraceIdFilterTest} 同口径）。</p>
 */
class WorkspaceAuditCoverageTest {

    // 供 HandlerMethod 反射的最小"控制器代理"（本包 = workspace 子包 → 命中审计域）。
    static class ProbeCtl {
        public void sample() {}
    }

    /** 捕获 emitAudit 的拦截器桩（跳过真实 EventBus/Kafka 通道）。 */
    static class CapturingInterceptor extends WorkspaceSecurityEngineAuditInterceptor {
        final List<Map<String, Object>> emitted = new ArrayList<>();
        CapturingInterceptor() { super(null); }
        @Override
        void emitAudit(Map<String, Object> e) { emitted.add(e); }
    }

    private HandlerMethod hm() throws Exception {
        Method m = ProbeCtl.class.getMethod("sample");
        return new HandlerMethod(new ProbeCtl(), m);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("审计域覆盖 knowledge/workbook/query 盲区子包（X-11：原仅 controller）")
    void knowledgeAndWorkbookControllersAreInsideAuditScope() {
        assertTrue(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspace"),
                "workspace 根包本身在域内");
        assertTrue(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspace.controller"),
                "controller（既有面）仍在域内");
        assertTrue(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspace.knowledge"),
                "knowledge 盲区子包必须入域（X-11）");
        assertTrue(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspace.workbook"),
                "workbook 盲区子包必须入域（X-11）");
        assertTrue(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspace.query"),
                "query 盲区子包必须入域（X-11）");
        assertFalse(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.datasource"),
                "非 workspace 族包不得误纳入审计");
        assertFalse(WorkspaceSecurityEngineAuditInterceptor.inAuditScope("com.chinacreator.gzcm.workspaces"),
                "前缀同形不同义（workspaces）不得误纳入（防裸 startsWith 前缀误判）");
        assertFalse(WorkspaceSecurityEngineAuditInterceptor.inAuditScope(null));
    }

    /** 用桩驱动一次 GET 请求，返回捕获到的审计事件（可能为空）。 */
    private List<Map<String, Object>> runGet(CapturingInterceptor it, String uri) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn(uri);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        it.afterCompletion(req, resp, hm(), null);
        return it.emitted;
    }

    @Test
    @DisplayName("C-6 敏感读 GET 逐端点触发审计（场景域 completeness/graph/runs/认知×4/列表/详情/绑定）")
    void sensitiveReadsAreAudited() throws Exception {
        String[][] table = {
            {"/api/v1/workspace/scenarios/sc-1/completeness", "SCENARIO_COMPLETENESS_READ"},
            {"/api/v1/workspace/scenarios/sc-1/graph", "SCENARIO_GRAPH_READ"},
            {"/api/v1/workspace/scenarios/sc-1/runs", "SCENARIO_RUN_READ"},
            {"/api/v1/business/scenarios/sc-1/cognition/detect", "SCENARIO_COGNITION_READ"},
            {"/api/v1/business/scenarios/sc-1/cognition/operation-eval", "SCENARIO_COGNITION_READ"},
            {"/api/v1/business/scenarios/sc-1/cognition/hypotheses", "SCENARIO_COGNITION_READ"},
            {"/api/v1/business/scenarios/sc-1/cognition/beliefs", "SCENARIO_COGNITION_READ"},
            {"/api/v1/workspace/scenarios", "SCENARIO_READ"},
            {"/api/v1/workspace/scenarios/sc-9", "SCENARIO_READ"},
            {"/api/v1/workspace/scenarios/sc-1/minds", "SCENARIO_BIND_READ"},
            {"/api/v1/workspace/scenarios/sc-1/bindings", "SCENARIO_BIND_READ"},
        };
        for (String[] row : table) {
            CapturingInterceptor it = new CapturingInterceptor();
            List<Map<String, Object>> ev = runGet(it, row[0]);
            assertEquals(1, ev.size(),
                    "敏感读必须恰审计一次: " + row[0] + " (实际 " + ev.size() + " 条)");
            assertEquals(row[1], ev.get(0).get("auditKind"), "分类应匹配 C-6 表: " + row[0]);
        }
    }

    @Test
    @DisplayName("C-6 明确不审计的读（/binding-catalog 静态元数据）与对象域读（R-29 边界）/布局/工作台读 不触发")
    void nonSensitiveAndNonScenarioReadsNotAudited() throws Exception {
        String[] nonSensitive = {
            "/api/v1/workspace/binding-catalog",                // C-6 ❌ 静态元数据
            "/api/v1/workspace/scenarios/sc-1/sandbox/layout",  // 布局，非 C-6 敏感读
            "/api/v1/workspace/objects/eq-1/transitions",       // R-29 对象域，禁卷入场景敏感读
            "/api/query/history",                               // /api/query 非场景前缀
            "/api/workbook/sessions",                           // workbook 读非场景域敏感读（写侧另计）
        };
        for (String uri : nonSensitive) {
            CapturingInterceptor it = new CapturingInterceptor();
            runGet(it, uri);
            assertTrue(it.emitted.isEmpty(), "非 C-6 敏感读不得审计（防全 GET 风暴）: " + uri);
        }
    }

    @Test
    @DisplayName("E-5 红线：读侧载荷只带 ID/分类/subject/traceId/状态，禁带结果原文/行数/coverage")
    void auditPayloadCarriesIdsNotResultBody() throws Exception {
        TraceContext.put("trc-0001");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("subject-42", "ignored", List.of()));

        CapturingInterceptor it = new CapturingInterceptor();
        String uri = "/api/v1/business/scenarios/sc-7/cognition/beliefs?mind=mind-9";
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn(uri);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        it.afterCompletion(req, resp, hm(), null);

        assertEquals(1, it.emitted.size());
        Map<String, Object> e = it.emitted.get(0);
        assertEquals("SCENARIO_COGNITION_READ", e.get("auditKind"));
        assertEquals("sc-7", e.get("scenarioId"), "载荷应携带 scenarioId");
        assertEquals("mind-9", e.get("mindId"), "认知读应携带 mindId");
        assertEquals("subject-42", e.get("subjectId"), "载荷应携带执行主体（取 token，非 request）");
        assertEquals("trc-0001", e.get("traceId"), "载荷应贯通 traceId（F07-21 同源 MDC）");
        assertNotNull(e.get("eventId"));
        assertNotNull(e.get("module"));
        // E-5：这些"结果富字段"绝不进入审计载荷（需读 body 才能得，且会突破摘要红线）
        List<String> forbidden = Arrays.asList(
                "coverage", "result", "resultBody", "body", "rows", "rowCount",
                "resultCount", "islands", "recommendations", "data");
        for (String k : forbidden) {
            assertFalse(e.containsKey(k), "审计载荷不得携带结果富字段: " + k);
        }
    }

    @Test
    @DisplayName("写侧仍走全量审计（POST 经 write 分支，auditKind=write，不强塞读侧 ID 字段）")
    void writeSideStillFullyAudited() throws Exception {
        CapturingInterceptor it = new CapturingInterceptor();
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/api/v1/workspace/scenarios/sc-1/runs");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(201);
        it.afterCompletion(req, resp, hm(), null);
        assertEquals(1, it.emitted.size());
        assertEquals("write", it.emitted.get(0).get("auditKind"));
        assertNull(it.emitted.get(0).get("scenarioId"), "写侧保持既有载荷形态，不强塞 read 侧 ID 字段");
    }
}
