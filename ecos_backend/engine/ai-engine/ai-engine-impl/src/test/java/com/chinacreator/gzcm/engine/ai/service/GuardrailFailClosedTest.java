package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.dao.GuardrailsPolicyDao;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.security.GuardrailAuditPublisher;
import com.chinacreator.gzcm.engine.ai.security.ToolSubjectResolver;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-04 验收 —— 输入/输出护栏必经 security-engine 且 fail-closed
 * （消灭 X-15 双重 fail-open 与 X-16 "从不问 security"）。
 *
 * <p>doc 冻结的三个标识：</p>
 * <ul>
 *   <li>{@code GuardrailFailClosedTest#absentSecurityEngineBlocksInputBeforeLlmCall} ——
 *       security 不可用时在<b>调用 LLM 之前</b>就终止（LLM 门面零交互，token 零消耗）；</li>
 *   <li>{@code GuardrailFailClosedTest#guardrailBeanMissingIsNotSilentlySkipped} ——
 *       护栏 Bean 缺失不再"判空跳过"（旧 {@code guardrailsService != null} 分支即本项消灭对象），
 *       装配点必须是必填；</li>
 *   <li>{@code GuardrailFailClosedTest#outputFilterEmitsAuditEvent} —— 输出命中后整段替换
 *       必须同时发 {@code GUARDRAIL_FILTER} 审计事件（否则 §1.5-1 对账不可得）。</li>
 * </ul>
 *
 * <p>DENY 与 FAIL_CLOSED 严格分态（F06-14）：不可用 ≠ 命中，前者 503 整体失败、
 * 后者 403 语义的整段替换，禁相互折叠。</p>
 */
class GuardrailFailClosedTest {

    private static final String USER = "u-9";
    private static final String SECRET_OUTPUT = "联系方式 13800001111，身份证号 520201199001011234";

    private LLMGateway llmGateway;
    private AgentConfigResolver configResolver;
    private AiSecurityEngineClient securityClient;
    private EventBusService eventBus;
    private ToolSubjectResolver subjectResolver;
    private GuardrailsServiceImpl guardrails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        llmGateway = mock(LLMGateway.class);
        configResolver = mock(AgentConfigResolver.class);
        AgentLoopConfig config = new AgentLoopConfig();
        config.setModel("deepseek-chat");
        config.setMaxIterations(1);
        when(configResolver.resolve(any(), any())).thenReturn(config);

        securityClient = mock(AiSecurityEngineClient.class);
        eventBus = mock(EventBusService.class);
        ObjectProvider<EventBusService> busProvider = mock(ObjectProvider.class);
        when(busProvider.getIfAvailable()).thenReturn(eventBus);
        GuardrailAuditPublisher publisher =
                new GuardrailAuditPublisher(busProvider, mock(
                        com.chinacreator.gzcm.engine.ai.dao.GuardrailDecisionLedgerDao.class));

        subjectResolver = mock(ToolSubjectResolver.class);
        when(subjectResolver.resolve()).thenReturn(new ToolAdjudicationRequest.Subject(
                USER, List.of("analyst"), "dept-a", "t-1"));

        guardrails = new GuardrailsServiceImpl(mock(GuardrailsPolicyDao.class),
                securityClient, publisher, subjectResolver);
    }

    // ── 验收 1：security 不可用 → LLM 调用之前就终止 ─────────────────────────

    @Test
    @DisplayName("F06-04 security-engine 不可用 → 输入护栏 FAIL_CLOSED，LLM 一次都没被调用")
    void absentSecurityEngineBlocksInputBeforeLlmCall() throws Exception {
        when(securityClient.evaluateData(any())).thenThrow(
                new AiSecurityEngineClient.SecurityResponseUnparsableException("connection refused"));

        AgentLoopResult r = loop(guardrails).run(new AgentLoopConfig(), "帮我查合同金额", session());

        assertFalse(r.isSuccess(), "不可用态不得返回未检查的模型输出");
        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getErrorMsg()));
        assertEquals("ECOS-AI-503", r.getErrorCode());
        assertNotNull(r.getTraceId(), "错误必须带诊断码（F06-14）");
        verifyNoInteractions(llmGateway);

        // 不可用态同样要留痕（§2.4-5），且事件里只有摘要没有原文
        Map<String, Object> event = captureFilterEvent();
        assertEquals(EventTypes.Guardrail.GUARDRAIL_FILTER, event.get("eventType"));
        assertEquals("FAIL_CLOSED", event.get("result"));
        Map<?, ?> detail = (Map<?, ?>) event.get("detail");
        assertEquals("input", detail.get("stage"));
        assertNotNull(detail.get("contentDigest"));
        assertFalse(objectMapper.writeValueAsString(event).contains("合同金额"),
                "护栏审计禁带输入原文: " + event);
    }

    @Test
    @DisplayName("F06-04 护栏调用抛异常不得被 catch→warn 吞掉（旧 fail-open 分支已消灭）")
    void guardrailBeanThrowIsFailClosedNotWarn() throws Exception {
        GuardrailsService throwing = new GuardrailsService() {
            @Override
            public Map<String, Object> validate(Map<String, Object> req) {
                throw new IllegalStateException("boom");
            }

            @Override
            public List<Map<String, Object>> listPolicies() {
                throw new UnsupportedOperationException("本用例只覆盖 validate 故障态");
            }

            @Override
            public Map<String, Object> createPolicy(Map<String, Object> policy) {
                throw new UnsupportedOperationException("本用例只覆盖 validate 故障态");
            }

            @Override
            public void deletePolicy(String id) {
                throw new UnsupportedOperationException("本用例只覆盖 validate 故障态");
            }
        };

        AgentLoopResult r = loop(throwing).run(new AgentLoopConfig(), "查一下", session());

        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getErrorMsg()));
        assertFalse(r.isSuccess());
        verifyNoInteractions(llmGateway);
    }

    // ── 验收 2：Bean 缺失不是"跳过检查" ─────────────────────────────────────

    @Test
    @DisplayName("F06-04 护栏 Bean 缺失 → FAIL_CLOSED 且装配点必填（@Autowired.required=true）")
    void guardrailBeanMissingIsNotSilentlySkipped() throws Exception {
        Field field = AgentLoopService.class.getDeclaredField("guardrailsService");
        Autowired annotation = field.getAnnotation(Autowired.class);
        assertNotNull(annotation, "护栏装配点必须有 @Autowired（旧 required=false 即 X-15 根因）");
        assertTrue(annotation.required(), "护栏依赖必须必填：可选注入 = 缺件时静默放行");

        AgentLoopResult r = loop(null).run(new AgentLoopConfig(), "查一下", session());

        assertEquals("GUARDRAIL_FAIL_CLOSED", r.getErrorClass(), String.valueOf(r.getErrorMsg()));
        assertFalse(r.isSuccess());
        verifyNoInteractions(llmGateway);
    }

    @Test
    @DisplayName("F06-04 输入护栏 DENY 不被折叠成不可用：403 语义 + LLM 零调用")
    void denyIsNotFoldedIntoFailClosed() throws Exception {
        stubVerdicts(Map.of("input", Map.of("allow", false,
                        "obligations", List.of(Map.of("deniedReason", "输入包含未授权数据范围"))),
                "output", Map.of("allow", true)));

        AgentLoopResult r = loop(guardrails).run(new AgentLoopConfig(), "越权问题", session());

        assertFalse(r.isSuccess());
        assertEquals("GUARDRAIL_DENIED", r.getErrorClass(), String.valueOf(r.getErrorMsg()));
        assertEquals("ECOS-AI-403", r.getErrorCode());
        assertNotEquals("ECOS-AI-503", r.getErrorCode(),
                "命中（策略拒绝）与不可用（服务故障）文案/重试语义不同，禁折叠");
        verifyNoInteractions(llmGateway);
    }

    // ── 验收 3：输出命中整段替换 + 留痕 ─────────────────────────────────────

    @Test
    @DisplayName("F06-04 输出护栏命中：整段替换为占位文案 + 恰一条 GUARDRAIL_FILTER 事件")
    void outputFilterEmitsAuditEvent() throws Exception {
        stubVerdicts(Map.of("input", Map.of("allow", true),
                "output", Map.of("allow", false,
                        "obligations", List.of(Map.of("deniedReason", "输出含未脱敏个人标识")))));
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(SECRET_OUTPUT, 5, 5, "deepseek-chat"));

        AgentLoopResult r = loop(guardrails).run(new AgentLoopConfig(), "查联系方式", session());

        assertTrue(r.isSuccess(), "命中态是 403 语义的整段替换，不是整体失败: " + r.getErrorMsg());
        assertEquals(GuardrailsServiceImpl.FILTERED_PLACEHOLDER, r.getContent(),
                "命中后不得把原文回给用户");
        assertFalse(String.valueOf(r.getContent()).contains("13800001111"), r.getContent());

        Map<String, Object> event = captureFilterEvent();
        assertEquals(EventTypes.Guardrail.GUARDRAIL_FILTER, event.get("eventType"));
        assertEquals("BLOCKED", event.get("result"));
        Map<?, ?> detail = (Map<?, ?>) event.get("detail");
        assertEquals("output", detail.get("stage"));
        assertNotNull(detail.get("contentDigest"));
        assertFalse(((String) detail.get("contentDigest")).contains("13800001111"),
                "留痕用摘要，禁原文入审计库（F06-03 要点 2）");
        assertEquals("t-1", event.get("tenantId"));
        assertEquals(USER, event.get("userId"));

        // 输入放行 + 输出命中：security 恰好被问 2 次（两个拦截点都必经）
        verify(securityClient, times(2)).evaluateData(any());
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), any());
    }

    @Test
    @DisplayName("F06-04 输出 ALLOW 且 security 下发脱敏结果时，用脱敏文本回用户")
    void maskedOutputFromSecurityIsUsed() throws Exception {
        Map<String, Object> allow = new LinkedHashMap<>();
        allow.put("allow", true);
        allow.put("obligations", List.of(Map.of("column", "phone", "rule", "phone")));
        stubVerdicts(Map.of("input", allow, "output", allow));
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok(SECRET_OUTPUT, 5, 5, "deepseek-chat"));
        when(securityClient.applyMasking(any(), any())).thenAnswer(inv -> {
            List<String> values = inv.getArgument(0);
            List<Map<String, Object>> out = new java.util.ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                out.add(Map.of("index", i, "masked", "138****1111", "rule", "phone"));
            }
            return out;
        });

        AgentLoopResult r = loop(guardrails).run(new AgentLoopConfig(), "查联系方式", session());

        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));
        assertFalse(String.valueOf(r.getContent()).contains("13800001111"),
                "security 下发的脱敏结果必须在回用户之前生效: " + r.getContent());
        assertTrue(String.valueOf(r.getContent()).contains("138****1111"), r.getContent());
    }

    @Test
    @DisplayName("F06-04 引擎不再有本地判定权：粗筛未命中也照样交 security 裁决")
    void harmlessContentIsStillAdjudicatedBySecurity() throws Exception {
        stubVerdicts(Map.of("input", Map.of("allow", true), "output", Map.of("allow", true)));
        when(llmGateway.call(any())).thenReturn(ChatResponse.ok("今日无异常", 1, 1, "deepseek-chat"));

        AgentLoopResult r = loop(guardrails).run(new AgentLoopConfig(), "随便聊聊", session());

        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));
        verify(securityClient, times(2)).evaluateData(any());
        verify(eventBus, never()).publish(anyString(), any());
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    private AgentLoopService loop(GuardrailsService guardrailsService) {
        AgentLoopService svc = new AgentLoopService();
        ReflectionTestUtils.setField(svc, "llmGateway", llmGateway);
        ReflectionTestUtils.setField(svc, "guardrailsService", guardrailsService);
        ReflectionTestUtils.setField(svc, "agentConfigResolver", configResolver);
        return svc;
    }

    private AgentSession session() {
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("s-1");
        when(session.getProfileName()).thenReturn("default");
        return session;
    }

    /** 按 stage 分发裁决响应（护栏有两个拦截点，同一个客户端桩按载荷 stage 区分）。 */
    @SuppressWarnings("unchecked")
    private void stubVerdicts(Map<String, Map<String, Object>> byStage) throws Exception {
        when(securityClient.evaluateData(any())).thenAnswer(inv -> {
            Map<String, Object> body = inv.getArgument(0);
            Map<String, Object> input = (Map<String, Object>) body.get("input");
            String stage = String.valueOf(input == null ? "?" : input.get("stage"));
            Map<String, Object> data = byStage.get(stage);
            if (data == null) {
                throw new IllegalStateException("装置未按 stage 桩配裁决: " + stage);
            }
            return data;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureFilterEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventBus, times(1)).publish(eq(KafkaTopics.AUDIT), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }
}
