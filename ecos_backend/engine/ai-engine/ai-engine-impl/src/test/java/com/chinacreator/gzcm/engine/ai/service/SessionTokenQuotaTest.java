package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-07 要点 3 验收 —— 会话 token 配额超限必须终止 Loop 并回可读消息。
 *
 * <p>doc 冻结标识：{@code SessionTokenQuotaTest#overLimitStopsLoopWithReadableMessage}。
 * 关键断言是"禁继续静默消耗"：超限时 LLM 门面<b>一次都不被调用</b>（token 零消耗），
 * 且响应面只有 errorCode + 可读消息 + 诊断码，不含 reason/异常原文（X-49）。</p>
 *
 * <p>配额上限经 sysman 配置门面读 {@code ecos.ai.session.token_limit}（V264 种子键），
 * 用量真值经 {@link LLMGateway#sessionTokenUsage}（唯一计量点在网关，引擎不自记账）。</p>
 */
class SessionTokenQuotaTest {

    private static final String SESSION = "sess-quota-1";
    private static final String LIMIT_KEY = "ecos.ai.session.token_limit";

    private LLMGateway llmGateway;
    private AgentConfigResolver configResolver;
    private GuardrailsService guardrails;
    private SysConfigService sysConfig;
    private ObjectProvider<SysConfigService> quotaProviderStub;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        llmGateway = mock(LLMGateway.class);
        configResolver = mock(AgentConfigResolver.class);
        AgentLoopConfig config = new AgentLoopConfig();
        config.setModel("deepseek-chat");
        config.setMaxIterations(3);
        when(configResolver.resolve(any(), any())).thenReturn(config);

        // 输入护栏放行：本用例只考察配额分支，护栏链由 F06-04 的 GuardrailFailClosedTest 覆盖
        guardrails = mock(GuardrailsService.class);
        when(guardrails.validate(any())).thenReturn(Map.of("passed", true));

        sysConfig = mock(SysConfigService.class);
        ObjectProvider<SysConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sysConfig);
        quotaProviderStub = provider;
    }

    // ── 验收：超限即终止 + 可读消息 ────────────────────────────────────────

    @Test
    @DisplayName("F06-07 要点 3：会话用量达上限 → Loop 终止、LLM 零调用、消息可读带诊断码")
    void overLimitStopsLoopWithReadableMessage() {
        when(sysConfig.getInt(eq(LIMIT_KEY), anyInt())).thenReturn(2000);
        when(llmGateway.sessionTokenUsage(SESSION)).thenReturn(2500L);

        AgentLoopResult r = loop(2000, 2500L).run(new AgentLoopConfig(), "帮我查合同金额", session());

        assertFalse(r.isSuccess(), "超限不得返回未检查的模型输出");
        assertEquals("CONTEXT_OVERFLOW", r.getErrorClass(), String.valueOf(r.getErrorMsg()));
        assertEquals("ECOS-AI-413", r.getErrorCode());
        assertFalse(r.isRetryable(), "CONTEXT_OVERFLOW 定义 maxRetries=0 ⇒ 不可重试");
        assertNotNull(r.getTraceId(), "错误必须带诊断码（F06-14）");

        String msg = r.getErrorMsg();
        assertNotNull(msg);
        assertTrue(msg.contains("2000") && msg.contains("2500"),
                "可读消息必须给出已用/上限数值: " + msg);
        assertTrue(msg.contains(r.getTraceId()), "可读消息必须内嵌诊断码: " + msg);
        assertFalse(msg.contains("limit_reached"), "机器可读 reason 禁入用户消息: " + msg);

        // 禁继续静默消耗：网关一次都没被调用
        verify(llmGateway, never()).call(any());
    }

    @Test
    @DisplayName("F06-07 要点 3：未达上限 → 正常放行，且请求携带 sessionId 供网关计量归集")
    void withinLimitProceedsAndCarriesSessionScope() {
        ChatResponse ok = ChatResponse.ok("合同金额为 12 万元", 100, 20, "deepseek-chat");
        ok.setUsageReported(true);
        when(llmGateway.call(any())).thenReturn(ok);

        AgentLoopResult r = loop(200_000, 1_000L).run(new AgentLoopConfig(), "查合同金额", session());

        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));
        ArgumentCaptor<com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest> captor =
                ArgumentCaptor.captor();
        verify(llmGateway, times(1)).call(captor.capture());
        assertEquals(SESSION, captor.getValue().getSessionId(),
                "F06-07：会话作用域必须随请求下传到唯一计量点");
    }

    @Test
    @DisplayName("F06-07 要点 3：上限 0/未配置 = 不启用配额（不判定，禁据此推断已放行）")
    void limitUnsetDisablesQuotaJudgement() {
        SessionTokenQuotaService quota = quotaService(0, 999_999L);
        SessionTokenQuotaService.QuotaState state = quota.check(SESSION);

        assertFalse(state.enforced());
        assertFalse(state.exceeded());
        assertEquals(0L, state.limit());
        assertEquals("limit_disabled", state.reason());
        verify(llmGateway, never()).sessionTokenUsage(anyString());
    }

    @Test
    @DisplayName("F06-07 要点 3：无会话标识 / 用量读数失败 ⇒ 不判定（成本控制项，允许降级但不静默）")
    void noSessionOrUnreadableUsageDoesNotJudge() {
        when(sysConfig.getInt(eq(LIMIT_KEY), anyInt())).thenReturn(2000);

        SessionTokenQuotaService quota = new SessionTokenQuotaService(quotaProviderStub, llmGateway);
        SessionTokenQuotaService.QuotaState noSession = quota.check(null);
        assertFalse(noSession.enforced());
        assertEquals("no_session", noSession.reason());
        verify(llmGateway, never()).sessionTokenUsage(any());

        when(llmGateway.sessionTokenUsage(SESSION)).thenThrow(new IllegalStateException("mapper 未注册"));
        SessionTokenQuotaService.QuotaState unreadable = quota.check(SESSION);
        assertFalse(unreadable.enforced());
        assertEquals("usage_unavailable", unreadable.reason());
    }

    @Test
    @DisplayName("F06-07 要点 3：判定态数值可被上下文端点复用（used/limit/remaining 同源）")
    void enforcedStateExposesNumbersForContextEndpoint() {
        when(sysConfig.getInt(eq(LIMIT_KEY), anyInt())).thenReturn(2000);
        when(llmGateway.sessionTokenUsage(SESSION)).thenReturn(1200L);

        SessionTokenQuotaService.QuotaState state = quotaService(2000, 1200L).check(SESSION);
        assertTrue(state.enforced());
        assertFalse(state.exceeded());
        assertEquals(1200L, state.used());
        assertEquals(2000L, state.limit());
        assertEquals(800L, state.remaining());
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    /** 装配一个带真实配额服务的 Loop（上限/用量由参数决定，配置门面与网关读数均为 mock）。 */
    private AgentLoopService loop(int limit, long used) {
        AgentLoopService svc = new AgentLoopService();
        ReflectionTestUtils.setField(svc, "llmGateway", llmGateway);
        ReflectionTestUtils.setField(svc, "guardrailsService", guardrails);
        ReflectionTestUtils.setField(svc, "agentConfigResolver", configResolver);
        ReflectionTestUtils.setField(svc, "sessionTokenQuota", quotaService(limit, used));
        return svc;
    }

    private SessionTokenQuotaService quotaService(int limit, long used) {
        when(sysConfig.getInt(eq(LIMIT_KEY), anyInt())).thenReturn(limit);
        if (limit > 0) {
            when(llmGateway.sessionTokenUsage(SESSION)).thenReturn(used);
        }
        return new SessionTokenQuotaService(quotaProviderStub, llmGateway);
    }

    private static AgentSession session() {
        return new AgentSession(SESSION, "aiming", "forecast-analyst", "你是合同查询助手");
    }
}
