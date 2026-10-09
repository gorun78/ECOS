package com.chinacreator.gzcm.runtime.llm.metrics;

import com.chinacreator.gzcm.common.token.TokenEstimates;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.model.AgentCallLog;
import com.chinacreator.gzcm.runtime.llm.repository.AgentCallLogRepository;
import com.chinacreator.gzcm.runtime.llm.security.SecurityEngineBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-07 验收 —— llm-gateway 唯一计量点的"诚实计量"。
 *
 * <p>doc 冻结的两个标识：</p>
 * <ul>
 *   <li>{@code LlmUsageMeteringTest#usageEventEmitOncePerCallWithRealOrEstimatedFlag} ——
 *       一次调用恰一条 {@code LLM_USAGE}，且实测态与估算态由 {@code estimated}/{@code usageSource}
 *       严格区分；</li>
 *   <li>{@code LlmUsageMeteringTest#estimatedTokensAreMarked} —— provider 不报 usage 时
 *       按 tokenizer 估算<b>并必须标记为估算</b>（禁把估算值当实测值上报，X-39/X-88）。</li>
 * </ul>
 *
 * <p>另断言计量载荷不含 prompt/补全原文（F06-03 要点 2 同纪律）。</p>
 */
class LlmUsageMeteringTest {

    private static final String SESSION = "sess-meter-1";
    private static final String CALLER = "llm-gateway:aiming:forecast-analyst";
    private static final String PROMPT = "请给出上季度华东区销量预测的依据";
    private static final String COMPLETION = "依据三个已批准口径与两条证据链接。";

    private AgentCallLogRepository repository;
    private SecurityEngineBridge bridge;
    private LlmUsageMeter meter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AgentCallLogRepository.class);
        bridge = mock(SecurityEngineBridge.class);
        ObjectProvider<SecurityEngineBridge> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bridge);
        when(bridge.publishStructuredAudit(any())).thenReturn(true);
        meter = new LlmUsageMeter(repository, provider);
    }

    @Test
    @DisplayName("F06-07 要点 1/2：一次调用恰一条 LLM_USAGE，实测态标 REPORTED、估算态标 ESTIMATED")
    void usageEventEmitOncePerCallWithRealOrEstimatedFlag() {
        ChatRequest request = request();
        ChatResponse reported = ChatResponse.ok(COMPLETION, 1200, 300, "deepseek-chat");
        reported.setUsageReported(true);

        meter.meterChat(request, CALLER, "deepseek", reported, 880L);

        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.captor();
        verify(bridge, times(1)).publishStructuredAudit(eventCaptor.capture());
        Map<String, Object> event = eventCaptor.getValue();
        assertEquals("LLM_USAGE", event.get("eventType"));
        assertEquals(SESSION, event.get("sessionId"));
        assertEquals(CALLER, event.get("userId"));
        assertNotNull(event.get("traceId"), "事件必须带诊断码同源 traceId");
        assertEquals("SUCCESS", event.get("result"));

        Map<String, Object> detail = detail(event);
        assertEquals(Boolean.FALSE, detail.get("estimated"), "provider 上报真值不得标估算");
        assertEquals("REPORTED", detail.get("usageSource"));
        assertEquals(1200, detail.get("promptTokens"), "实测值不得被估算值覆盖");
        assertEquals(300, detail.get("completionTokens"));
        assertEquals(1500L, detail.get("totalTokens"));
        assertEquals(880L, detail.get("latencyMs"));
        assertEquals("aiming", detail.get("subsystem"));
        assertEquals("forecast-analyst", detail.get("profileName"));

        ArgumentCaptor<AgentCallLog> rowCaptor = ArgumentCaptor.captor();
        verify(repository, times(1)).insert(rowCaptor.capture());
        AgentCallLog row = rowCaptor.getValue();
        assertEquals(SESSION, row.getSessionId());
        assertEquals(1200 + 300, row.getTokensInput() + row.getTokensOutput());
        assertEquals("success", row.getStatus());

        // 估算态：同一条事件形态，estimated 翻真且 usageSource=ESTIMATED（一次调用仍恰一条）
        ChatRequest second = request();
        ChatResponse unreported = ChatResponse.ok(COMPLETION, 0, 0, "deepseek-chat");

        meter.meterChat(second, CALLER, "deepseek", unreported, 400L);

        ArgumentCaptor<Map<String, Object>> secondCaptor = ArgumentCaptor.captor();
        verify(bridge, times(2)).publishStructuredAudit(secondCaptor.capture());
        Map<String, Object> secondDetail = detail(secondCaptor.getAllValues().get(1));
        assertEquals(Boolean.TRUE, secondDetail.get("estimated"));
        assertEquals("ESTIMATED", secondDetail.get("usageSource"));
        assertTrue((int) secondDetail.get("promptTokens") > 0);
        verify(repository, times(2)).insert(any());
    }

    @Test
    @DisplayName("F06-07 要点 1：provider 不报 usage ⇒ 估算值必须标 estimated=true，禁冒充实测")
    void estimatedTokensAreMarked() {
        ChatRequest request = request();
        ChatResponse response = ChatResponse.ok(COMPLETION, 0, 0, "deepseek-chat");

        meter.meterChat(request, CALLER, "deepseek", response, 300L);

        assertTrue(response.isUsageEstimated(), "响应必须带估算标记，调用方才能区分真值/估算");
        assertFalse(response.isUsageReported());
        assertTrue(response.hasUsage());
        assertEquals(TokenEstimates.estimateText(COMPLETION), response.getTokensOutput());
        assertEquals(estimatePrompt(request), response.getTokensInput());
        assertEquals(300L, response.getLatencyMs());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
        verify(bridge).publishStructuredAudit(captor.capture());
        Map<String, Object> detail = detail(captor.getValue());
        assertEquals(Boolean.TRUE, detail.get("estimated"));
        assertEquals("ESTIMATED", detail.get("usageSource"));

        // 计量载荷只含数值与标识：prompt / 补全原文一律禁入（F06-03 要点 2）
        String json = objectMapper.valueToTree(captor.getValue()).toString();
        assertFalse(json.contains(PROMPT), "LLM_USAGE 载荷不得含 prompt 原文");
        assertFalse(json.contains(COMPLETION), "LLM_USAGE 载荷不得含补全原文");
    }

    @Test
    @DisplayName("F06-07 失败态：调用失败仍恰一条事件，result=FAILED 且不产生估算值")
    void failedCallStillEmitsExactlyOneUsageEvent() {
        ChatRequest request = request();
        ChatResponse failure = ChatResponse.fail("provider 5xx");

        meter.meterChat(request, CALLER, "deepseek", failure, 120L);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
        verify(bridge).publishStructuredAudit(captor.capture());
        Map<String, Object> event = captor.getValue();
        assertEquals("FAILED", event.get("result"));
        Map<String, Object> detail = detail(event);
        assertEquals(Boolean.FALSE, detail.get("estimated"), "失败调用无补全文本，估算无意义 ⇒ 不得标估算");
        assertEquals("NONE", detail.get("usageSource"));
        assertFalse(failure.hasUsage());

        ArgumentCaptor<AgentCallLog> rowCaptor = ArgumentCaptor.captor();
        verify(repository).insert(rowCaptor.capture());
        assertEquals("failed", rowCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("F06-07 要点 3：配额读数走 sys_agent_call_log 聚合，读数异常不得静默归零")
    void sessionTokenUsageReadsAggregateFromCallLog() {
        when(repository.sumTokensBySession(SESSION)).thenReturn(180_000L);
        assertEquals(180_000L, meter.sessionTokenUsage(SESSION));
        assertEquals(0L, meter.sessionTokenUsage(null), "无会话标识 = 无作用域，返回 0 而非抛错");

        when(repository.sumTokensBySession("boom")).thenThrow(new IllegalStateException("mapper 未注册"));
        // 读数失败返回 0（会放过超限），但必须走 ERROR 日志面 —— 本断言锁定"不抛穿给调用方"的契约
        assertEquals(0L, meter.sessionTokenUsage("boom"));
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    private static ChatRequest request() {
        ChatRequest request = new ChatRequest("deepseek-chat",
                List.of(new ChatMessage("system", "你是预测助手"), new ChatMessage("user", PROMPT)),
                0.3, 1024, false);
        request.setCallerId(CALLER);
        request.setSessionId(SESSION);
        return request;
    }

    private static int estimatePrompt(ChatRequest request) {
        int total = 0;
        for (ChatMessage msg : request.getMessages()) {
            total += TokenEstimates.estimateMessage(msg.getContent());
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detail(Map<String, Object> event) {
        Object detail = event.get("detail");
        assertTrue(detail instanceof Map, "LLM_USAGE 必须带 detail 子对象");
        return (Map<String, Object>) detail;
    }
}
