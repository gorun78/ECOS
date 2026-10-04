package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * F06-04（详细设计-06 §303-312，X-15/X-16）— 输入/输出护栏改经 security 且 fail-closed。
 *
 * <p>验收三点（设计 §312 指名）：
 * <ol>
 *   <li><b>absentSecurityEngineBlocksInputBeforeLlmCall</b>：security 不可用/未明确放行 →
 *       输入护栏在调用 LLM 之前即拒绝整次请求（GUARDRAIL_FAIL_CLOSED），LLM 出口零交互。</li>
 *   <li><b>guardrailBeanMissingIsNotSilentlySkipped</b>：护栏依赖 {@code @Autowired} 必填
 *       （非 required=false）——源级断言消除 X-15 判空跳过面。</li>
 *   <li><b>outputFilterEmitsAuditEvent</b>：输出护栏拦截 → 整段替换为过滤占位 <b>且</b>发一条
 *       GUARDRAIL_EVAL 审计（§1.5-1 对账，要点 3）。</li>
 * </ol>
 *
 * <p>不触 DB/网络；全 Mockito 桩。分册01 text-screen 端点尚未入网（J-6 接缝），
 * 本测试锁定"无该端点时 fail-closed 而非本地正则放行"的正向护栏。</p>
 */
class GuardrailFailClosedTest {

    private AgentLoopService svc;
    private GuardrailsService guardrails;
    private LLMGatewayService llmGatewayService;
    private LLMGateway llmGateway;
    private AiSecurityEngineClient security;

    @BeforeEach
    void setUp() throws Exception {
        guardrails = mock(GuardrailsService.class);
        llmGatewayService = mock(LLMGatewayService.class);
        llmGateway = mock(LLMGateway.class);
        security = mock(AiSecurityEngineClient.class);
        AgentConfigResolver cfg = mock(AgentConfigResolver.class);
        when(cfg.resolve(anyString(), any())).thenReturn(new AgentLoopConfig());

        svc = new AgentLoopService();
        inject(svc, "guardrailsService", guardrails);
        inject(svc, "llmGatewayService", llmGatewayService);
        inject(svc, "llmGateway", llmGateway);
        inject(svc, "agentConfigResolver", cfg);
        inject(svc, "securityEngineClient", security);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = AgentLoopService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 构造一个"未过护栏"载体：passed=false + errorClass=GUARDRAIL_FAIL_CLOSED（即 security 不可用/未放行语义）。 */
    private Map<String, Object> failClosed() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("passed", false);
        m.put("failed", true);
        m.put("errorClass", "GUARDRAIL_FAIL_CLOSED");
        m.put("message", "security 文本审核 fail-closed");
        return m;
    }

    // ── 验收 1：absentSecurityEngineBlocksInputBeforeLlmCall ─────────────

    @Test
    @DisplayName("security 不可用/未明确放行 → 输入护栏在调用 LLM 之前拒绝整次请求（0 LLM 交互）")
    void absentSecurityEngineBlocksInputBeforeLlmCall() {
        when(guardrails.validate(any())).thenReturn(failClosed());

        AgentLoopResult r = svc.run(new AgentLoopConfig(), "请帮我做年度报告", null);

        assertFalse(r.isSuccess(), "护栏未放行必须终止");
        assertNotNull(r.getErrorMsg());
        assertTrue(r.getErrorMsg().contains("GUARDRAIL_FAIL_CLOSED"),
                "错误须带 FAIL_CLOSED 指纹（§1.5-2 可读消息），got=" + r.getErrorMsg());
        // 关键：LLM 出口零交互——拒绝必须发生在调 LLM 之前
        verifyNoInteractions(llmGatewayService, llmGateway);
    }

    // ── 验收 2：guardrailBeanMissingIsNotSilentlySkipped ─────────────────

    @Test
    @DisplayName("护栏依赖是 @Autowired 必填（required!=false）——消除 X-15 判空跳过面")
    void guardrailBeanMissingIsNotSilentlySkipped() throws Exception {
        Field f = AgentLoopService.class.getDeclaredField("guardrailsService");
        Autowired ann = f.getAnnotation(Autowired.class);
        assertNotNull(ann, "guardrailsService 必须 @Autowired（禁 required=false 判空跳过，X-15）");
        assertTrue(ann.required(),
                "required=false 即 X-15 判空跳过面，护栏必须 required=true（必填），缺失即启动失败");
    }

    // ── 验收 3：outputFilterEmitsAuditEvent ──────────────────────────────

    @Test
    @DisplayName("输出护栏拦截 → 整段替换过滤占位 且 发一条 GUARDRAIL_EVAL 审计")
    void outputFilterEmitsAuditEvent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-42", "pw", java.util.Collections.emptyList()));
        when(guardrails.validate(any())).thenReturn(failClosed());

        String out = svc.filterOutputGuardrail("raw sensitive llm output", "sess-1", "trace-1", "agentA");

        assertEquals("[内容已根据安全策略过滤]", out,
                "拦截态保留现行为：整段替换过滤占位（要点 3）");
        // 且必须发审计（否则 §1.5-1 对账不可得）
        verify(security, times(1)).publishGuardrailEventOrThrow(eq("user-42"), anyMap());
    }
}
