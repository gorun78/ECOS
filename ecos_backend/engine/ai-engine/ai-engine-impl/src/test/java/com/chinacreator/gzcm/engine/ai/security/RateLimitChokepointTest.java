package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolDecision;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流腿在咽喉 {@code ToolExecutorService.execute} 的<b>行为</b>验收
 * （#142/B4-3d-4，差距 D-B4-21 的收口证明）。
 *
 * <p><b>本类证明的是「消费方真在调用链上」而非「消费方存在」</b>：D-B4-21 的原形态就是
 * 策略签发义务、生产侧零消费方 —— 只写一个 {@code @Component} 再单测它自己，
 * 等于把缺口从"没人消费"改写成"有个没人调用的类"。因此每个用例都从
 * {@code execute()} 入口驱动，并对 {@code registry.execute}（dispatch）的调用次数下断言：</p>
 * <ul>
 *   <li>配额用尽 ⇒ 前 20 次确有 dispatch，第 21 次被拒且 <b>dispatch 次数不增</b>
 *       （限流决定"这次调用能否发起"，必须在 dispatch 之前；放阶段 4 等于先花掉后端预算再拒绝）；</li>
 *   <li>形态不合契约 ⇒ 一次 dispatch 都没有；</li>
 *   <li>消费方未装配 ⇒ 一次 dispatch 都没有，且归 503 而非 403。</li>
 * </ul>
 *
 * <p><b>分态不折叠</b>：DENIED→{@code GUARDRAIL_DENIED}（403），FAIL_CLOSED→
 * {@code GUARDRAIL_FAIL_CLOSED}（503）；两用例各自断"不是另一态"。</p>
 *
 * <p>装置与 {@code ObligationsApplyTest} 同形（真执行器 + 假闸 + 假注册表），
 * 不启 Spring 上下文：咽喉的编排顺序是纯代码行为，起容器只会让它变慢且更难定位。</p>
 */
class RateLimitChokepointTest {

    private static final ToolAdjudicationRequest.Subject SUBJECT =
            new ToolAdjudicationRequest.Subject("u-pj", List.of("project_owner"), "d01", "t-demo");

    private static final String ROW_RESULT_JSON =
            "{\"rows\":[{\"id\":1,\"phone\":\"13800001111\"}],\"count\":1}";

    /** 非行结构结果 —— 用来钉住"仅限流义务时 applier 必须透传"（禁误入行过滤分支）。 */
    private static final String PLAIN_TEXT_RESULT = "查询完成，共 2 条记录（非结构化文本）";

    private static final String RATE_KEY = GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX + "u-pj";

    private static ToolObligations rateOnlyObligations(String key) {
        return ToolObligations.fromEvaluateData(Map.of(
                GuardrailDecisionContract.KEY_OBLIGATIONS,
                List.of(Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
                        GuardrailDecisionContract.KEY_RATE_LIMIT_KEY, key))));
    }

    // ── 验收 1：配额用尽时 dispatch 不再发生 ────────────────────────────────

    @Test
    @DisplayName("咽喉 — 配额用尽的第 21 次被拒且 dispatch 次数不增（限流在 dispatch 之前）")
    void quotaExhaustionBlocksBeforeDispatch() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate gate = new AiToolRateLimitGate(now::get);
        Fixture f = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(RATE_KEY), 1L),
                ROW_RESULT_JSON, gate);

        for (int i = 0; i < AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            ToolExecutorService.ToolResult r = f.call();
            assertTrue(r.isSuccess(), "第 " + (i + 1) + " 次应在额度内: " + r.getError());
        }
        verify(f.registry, times(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW)).execute(eq("query_db"), any());

        ToolExecutorService.ToolResult exceeded = f.call();

        assertFalse(exceeded.isSuccess());
        assertEquals("GUARDRAIL_DENIED", exceeded.getErrorClass(), String.valueOf(exceeded.getError()));
        assertNotEquals("GUARDRAIL_FAIL_CLOSED", exceeded.getErrorClass(),
                "配额用尽是策略义务未满足（403 面），禁写成护栏不可用（503 面）");
        assertEquals("ALLOW", exceeded.getDecision(),
                "裁决本身是 ALLOW —— 拒绝来自义务执行，不是策略改口");
        // dispatch 次数不增：这一条才是"在 dispatch 之前消费"的证据
        verify(f.registry, times(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW)).execute(eq("query_db"), any());
        assertEquals(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW + 1, gate.usedCalls(RATE_KEY));
        // 拒绝态必须喊留痕出口（D-B4-33：形态由 RateLimitDenialTraceTest 验，本条只验"在链上"）
        verify(f.audit, times(1)).auditExecutionLegDenial(any());
    }

    @Test
    @DisplayName("咽喉 — 窗口翻转后同一链路重新放行（固定窗口语义贯通到 dispatch）")
    void quotaRecoversAfterWindowRollover() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate gate = new AiToolRateLimitGate(now::get);
        Fixture f = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(RATE_KEY), 1L),
                ROW_RESULT_JSON, gate);

        for (int i = 0; i < AiToolRateLimitGate.MAX_CALLS_PER_WINDOW + 1; i++) {
            f.call();
        }
        assertFalse(f.call().isSuccess());

        now.set(now.get() + AiToolRateLimitGate.WINDOW_MILLIS);

        assertTrue(f.call().isSuccess(), "超窗后必须重开窗口");
        verify(f.registry, times(AiToolRateLimitGate.MAX_CALLS_PER_WINDOW + 1)).execute(eq("query_db"), any());
    }

    // ── 验收 2：无义务恒放行且不计额 ───────────────────────────────────────

    @Test
    @DisplayName("咽喉 — 未签发限流义务时 25 次全放行且窗口表恒空（页面/导出通道不受本腿影响）")
    void absentObligationNeverConsumesQuota() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate gate = new AiToolRateLimitGate(now::get);
        Fixture f = new Fixture(ToolDecision.allow("p-none", ToolObligations.none(), 1L),
                ROW_RESULT_JSON, gate);

        for (int i = 0; i < 25; i++) {
            ToolExecutorService.ToolResult r = f.call();
            assertTrue(r.isSuccess(), "第 " + (i + 1) + " 次应放行: " + r.getError());
            assertEquals(ROW_RESULT_JSON, r.getContent(), "无义务时必须原样透传结果");
        }
        assertTrue(gate.windowSnapshot().isEmpty(),
                "无义务却计额＝在执行面自造计额键（键面唯一来源应是策略义务）");
        verify(f.registry, times(25)).execute(eq("query_db"), any());
        verify(f.audit, never()).auditExecutionLegDenial(any());
    }

    @Test
    @DisplayName("咽喉 — 仅限流义务不得误入行过滤分支（applier 透传，非结构化结果照样放行）")
    void loneRateObligationDoesNotTripRowFilter() {
        Fixture f = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(RATE_KEY), 1L),
                PLAIN_TEXT_RESULT, new AiToolRateLimitGate());

        ToolExecutorService.ToolResult r = f.call();

        assertTrue(r.isSuccess(),
                "只签发限流义务时结果段必须透传；若这里 fail-closed 说明 isEmpty() 已把限流算进义务"
                        + "而 applier 的透传守卫被移除: " + r.getError());
        assertEquals(PLAIN_TEXT_RESULT, r.getContent());
    }

    // ── 验收 3：形态不合契约 ⇒ 503 且零 dispatch ───────────────────────────

    @Test
    @DisplayName("咽喉 — 键面漂移的义务 → GUARDRAIL_FAIL_CLOSED 且一次 dispatch 都没有")
    void offContractKeyFailsClosedWithoutAnyDispatch() {
        String drifted = "tool.u-pj";
        Fixture f = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(drifted), 1L),
                ROW_RESULT_JSON, new AiToolRateLimitGate());

        ToolExecutorService.ToolResult r = f.call();

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getError()));
        assertNotEquals("GUARDRAIL_DENIED", r.getErrorClass(),
                "形态不合契约属「护栏给的形态无法消费」，禁写成无权限（403）");
        verify(f.registry, never()).execute(eq("query_db"), any());
        assertFalse(String.valueOf(r.getError()).contains(drifted),
                "自造/漂移键原文不得上浮到响应面（X-49）: " + r.getError());
        verify(f.audit, times(1)).auditExecutionLegDenial(any());
    }

    @Test
    @DisplayName("咽喉 — 主体段为空（ai.tool.）同样 503 且零 dispatch")
    void emptySubjectSegmentFailsClosed() {
        Fixture f = new Fixture(ToolDecision.allow("p-rate",
                rateOnlyObligations(GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX), 1L),
                ROW_RESULT_JSON, new AiToolRateLimitGate());

        ToolExecutorService.ToolResult r = f.call();

        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getError()));
        verify(f.registry, never()).execute(eq("query_db"), any());
        verify(f.audit, times(1)).auditExecutionLegDenial(any());
    }

    @Test
    @DisplayName("咽喉 — 消费方未装配 ⇒ 503（禁退回「义务存在但没人消费」的 D-B4-21 原形态）")
    void unwiredConsumerFailsClosed() {
        Fixture f = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(RATE_KEY), 1L),
                ROW_RESULT_JSON, null);

        ToolExecutorService.ToolResult r = f.call();

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getError()));
        verify(f.registry, never()).execute(eq("query_db"), any());
        verify(f.audit, times(1)).auditExecutionLegDenial(any());
    }

    // ── 验收 4：DENIED 与 FAIL_CLOSED 的对外分类互斥 ───────────────────────

    @Test
    @DisplayName("咽喉 — 同一义务下「配额用尽」与「形态不合契约」分别落在 403/503，禁互相折叠")
    void deniedAndFailClosedStayDistinct() {
        AtomicLong now = new AtomicLong(1_700_000_000_000L);
        AiToolRateLimitGate gate = new AiToolRateLimitGate(now::get);
        Fixture allowed = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations(RATE_KEY), 1L),
                ROW_RESULT_JSON, gate);
        for (int i = 0; i <= AiToolRateLimitGate.MAX_CALLS_PER_WINDOW; i++) {
            allowed.call();
        }
        String deniedClass = allowed.call().getErrorClass();

        Fixture broken = new Fixture(ToolDecision.allow("p-rate", rateOnlyObligations("tool.bad"), 1L),
                ROW_RESULT_JSON, gate);
        String failClosedClass = broken.call().getErrorClass();

        assertEquals("GUARDRAIL_DENIED", deniedClass);
        assertEquals("GUARDRAIL_FAIL_CLOSED", failClosedClass);
        assertNotEquals(deniedClass, failClosedClass,
                "两态折叠＝§4.7.2「不可用 ≠ 无权限」在执行面失效");
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    /** 真咽喉 + 假闸（恒返回给定裁决）+ 假注册表（恒返回给定结果）。 */
    private static final class Fixture {
        final ToolExecutorService service = new ToolExecutorService();
        final ToolRegistry registry = mock(ToolRegistry.class);
        /**
         * 留痕出口的装配位（#142/B4-3e-1，D-B4-33）。本类用 mock 只验"拒绝分支确实喊了留痕"，
         * 事件与台账行的<b>形态</b>由 {@code RateLimitDenialTraceTest} 用真发布器验收。
         */
        final GuardrailAuditPublisher audit = mock(GuardrailAuditPublisher.class);

        Fixture(ToolDecision decision, String resultJson, AiToolRateLimitGate gate) {
            ToolDao toolDao = mock(ToolDao.class);
            AgentToolPolicyGate policyGate = mock(AgentToolPolicyGate.class);
            ToolSubjectResolver subjectResolver = mock(ToolSubjectResolver.class);
            when(subjectResolver.resolve()).thenReturn(SUBJECT);
            when(policyGate.review(any(), any(), any())).thenReturn(decision);
            when(registry.has("query_db")).thenReturn(true);
            when(registry.execute(eq("query_db"), any())).thenReturn(
                    ToolExecutorService.ToolResult.ok("call-1", "query_db", resultJson, 1L));

            service.setToolDao(toolDao);
            service.setPolicyGate(policyGate);
            service.setSubjectResolver(subjectResolver);
            AiSecurityEngineClient securityStub = mock(AiSecurityEngineClient.class);
            service.setObligationApplier(new ToolObligationApplier(
                    securityStub, new ObjectMapper(), new AiToolColumnSurfaceGate(securityStub)));
            service.setRateLimitGate(gate);
            service.setGuardrailAuditPublisher(audit);
            ReflectionTestUtils.setField(service, "toolRegistry", registry);
        }

        ToolExecutorService.ToolResult call() {
            return service.execute("query_db", Map.of("scenarioId", "s-1"));
        }
    }
}
