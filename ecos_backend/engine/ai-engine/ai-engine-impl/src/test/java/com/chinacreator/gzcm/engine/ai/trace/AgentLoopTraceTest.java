package com.chinacreator.gzcm.engine.ai.trace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.ai.GuardrailsService;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import com.chinacreator.gzcm.engine.ai.service.AgentConfigResolver;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.engine.ai.service.ToolExecutorService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详细设计-06 F06-06 验收 —— traceId 单源（MDC）贯通 + Loop 每步恰一条结构化行 + token 字段禁恒 0。
 *
 * <p>doc 冻结的三个标识：{@code entryHeaderBecomesMdcAndIsPropagatedToSecurityAndGateway}、
 * {@code everyStepEmitsOneStructuredLine}、{@code tokenFieldsNeverZero}。</p>
 *
 * <p>本类同时钉住两条反 X-36/X-38 的不变式：</p>
 * <ul>
 *   <li>入口已有 traceId 时引擎<b>不得</b>另起短串（唯一源 = MDC）；</li>
 *   <li>结构化行只经 {@code ECOS_AGENT_LOOP} 通道输出，且是 F06-08 回放的输入面，
 *       故断言用解析器（{@link AgentLoopTrace#parse}）而非字符串包含 —— 写读两端同源才叫契约。</li>
 * </ul>
 */
class AgentLoopTraceTest {

    private static final String ENTRY_TRACE = "0f6c1c2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f";
    private static final String TOOL_CALLS_JSON =
            "[{\"id\":\"call_1\",\"function\":{\"name\":\"query_db\","
                    + "\"arguments\":\"{\\\"sql\\\":\\\"SELECT 1\\\"}\"}}]";

    private LLMGateway llmGateway;
    private AgentConfigResolver configResolver;
    private GuardrailsService guardrails;
    private ListAppender<ILoggingEvent> appender;
    private Logger channelLogger;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LLMGateway.class);
        configResolver = mock(AgentConfigResolver.class);
        AgentLoopConfig config = new AgentLoopConfig();
        config.setModel("deepseek-chat");
        config.setMaxIterations(3);
        when(configResolver.resolve(any(), any())).thenReturn(config);

        guardrails = mock(GuardrailsService.class);
        when(guardrails.validate(any())).thenReturn(Map.of("passed", true));

        channelLogger = (Logger) LoggerFactory.getLogger(AgentLoopTrace.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        channelLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        channelLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    // ── 验收 1：入口头 → MDC → security / gateway 出站 ───────────────────────

    @Test
    @DisplayName("F06-06 要点 1/2：入口 X-Request-Id 落 MDC 后，security 出站头与网关调用同源")
    @SuppressWarnings("unchecked")
    void entryHeaderBecomesMdcAndIsPropagatedToSecurityAndGateway() throws Exception {
        // 入口过滤器（gateway RequestContextFilter）语义等价：把头值写进 MDC 键 traceId
        MDC.put(EcosTraceContext.MDC_KEY_TRACE_ID, ENTRY_TRACE);
        assertTrue(AiTraceSupport.hasEntryTraceId(), "入口已给 traceId 时不得视为无链");
        assertEquals(ENTRY_TRACE, AiTraceSupport.currentTraceId(), "唯一源 = MDC，禁自造短串（X-36）");

        // ── security 出站：真实客户端 + 桩 RestTemplate，断言的是真上的头 ──
        RestTemplate restTemplate = mock(RestTemplate.class);
        Map<String, Object> securityBody = Map.of("data", Map.of("allow", true));
        ResponseEntity<Map> securityResponse = ResponseEntity.ok(securityBody);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(securityResponse);
        AiSecurityEngineClient client =
                new AiSecurityEngineClient("http://security:18081", 2000, mock(
                        org.springframework.beans.factory.ObjectProvider.class));
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);

        client.evaluateData(Map.of("input", Map.of("stage", "input", "content", "查合同金额")));

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(Map.class));
        assertEquals(ENTRY_TRACE,
                entityCaptor.getValue().getHeaders().getFirst(EcosTraceContext.HEADER_REQUEST_ID),
                "security 出站头必须等于入口 traceId");

        // ── gateway 出站：网关实现按 MDC 注入 provider 头，故断言调用发生在同一 MDC 上下文 ──
        ChatResponse ok = ChatResponse.ok("合同金额为 12 万元", 40, 8, "deepseek-chat");
        ok.setUsageReported(true);
        when(llmGateway.call(any())).thenAnswer(invocation -> {
            assertEquals(ENTRY_TRACE, EcosTraceContext.currentTraceId(),
                    "调用网关时 MDC 必须仍是入口 traceId（否则网关注入的头会断链）");
            return ok;
        });

        AgentLoopResult r = loop().run(new AgentLoopConfig(), "查合同金额", session());

        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));
        assertEquals(ENTRY_TRACE, r.getTraceId(), "响应诊断码必须等于入口 traceId");
        assertEquals(ENTRY_TRACE, EcosTraceContext.outboundHeader().value(),
                "出站头单源：同一 traceId 同一个头名");
        assertEquals("X-Request-Id", EcosTraceContext.outboundHeader().name());

        List<AgentLoopTrace.Line> lines = parsedLines();
        assertTrue(lines.stream().allMatch(l -> ENTRY_TRACE.equals(l.traceId())),
                "每行必须自带检索键 trace=入口 traceId（F06-08 回放的前提）: " + rawLines());
    }

    // ── 验收 2：每步恰一条结构化行 ───────────────────────────────────────────

    @Test
    @DisplayName("F06-06 要点 3：plan→llm_call→tool_call→tool_result→llm_call→final 一步一行，不多不少")
    void everyStepEmitsOneStructuredLine() {
        ChatResponse toolTurn = ChatResponse.ok(TOOL_CALLS_JSON, 100, 20, "deepseek-chat");
        toolTurn.setUsageReported(true);
        ChatResponse finalTurn = ChatResponse.ok("合同金额为 12 万元", 120, 30, "deepseek-chat");
        finalTurn.setUsageReported(true);
        when(llmGateway.call(any())).thenReturn(toolTurn, finalTurn);

        AgentLoopResult r = loop().run(new AgentLoopConfig(), "查合同金额", session());
        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));

        List<AgentLoopTrace.Line> lines = parsedLines();
        List<String> wire = lines.stream()
                .map(l -> l.op().wire() + "@" + l.step())
                .toList();
        assertEquals(List.of("plan@0", "llm_call@1", "tool_call@1", "tool_result@1",
                "llm_call@2", "final@2"), wire, "步骤序列必须与 Loop 实际经过一致: " + rawLines());

        // 每步恰一行：按 (step, op, tool) 归并后行数不变（重试行只在该 op 真重复时出现）
        long distinct = lines.stream()
                .map(l -> l.step() + "|" + l.op().wire() + "|" + (l.tool() == null ? "-" : l.tool()))
                .distinct().count();
        assertEquals(lines.size(), distinct, "同步同 op 同行不得重复输出: " + rawLines());
        assertEquals(1, lines.stream().filter(l -> l.op() == AgentLoopTrace.Op.PLAN).count(),
                "plan 只一条（step 0）");
        assertEquals(1, lines.stream().filter(l -> l.op() == AgentLoopTrace.Op.FINAL).count(),
                "final 只一条");
    }

    // ── 验收 3：token 字段禁恒 0 ─────────────────────────────────────────────

    @Test
    @DisplayName("F06-06 要点 3：provider 未上报用量时 token 字段写 -1（缺失），禁静默 0 冒充实测")
    void tokenFieldsNeverZero() {
        ChatResponse unreported = ChatResponse.ok("合同金额为 12 万元", 0, 0, "deepseek-chat");
        // 网关未上报也未估算 → hasUsage()=false，回放面必须记缺失而非 0
        when(llmGateway.call(any())).thenReturn(unreported);

        AgentLoopResult r = loop().run(new AgentLoopConfig(), "查合同金额", session());
        assertTrue(r.isSuccess(), String.valueOf(r.getErrorMsg()));

        List<AgentLoopTrace.Line> llmLines = parsedLines().stream()
                .filter(l -> l.op() == AgentLoopTrace.Op.LLM_CALL).toList();
        assertEquals(1, llmLines.size());
        assertEquals(AgentLoopTrace.UNKNOWN, llmLines.get(0).promptTokens(),
                "无真值必须写 -1（X-38 恒 0 面的反向断言）");
        assertEquals(AgentLoopTrace.UNKNOWN, llmLines.get(0).completionTokens());
        assertNotNull(llmLines.get(0).model());

        // 有真值时原样落行，不被"缺失态"覆盖
        ChatResponse reported = ChatResponse.ok("答复", 1200, 300, "deepseek-v3");
        reported.setUsageReported(true);
        when(llmGateway.call(any())).thenReturn(reported);
        appender.list.clear();

        AgentLoopResult r2 = loop().run(new AgentLoopConfig(), "再查一次", session());
        assertTrue(r2.isSuccess(), String.valueOf(r2.getErrorMsg()));
        AgentLoopTrace.Line line = parsedLines().stream()
                .filter(l -> l.op() == AgentLoopTrace.Op.LLM_CALL).findFirst().orElseThrow();
        assertEquals(1200L, line.promptTokens());
        assertEquals(300L, line.completionTokens());
        assertFalse(rawLines().isEmpty());
    }

    // ── 装置 ────────────────────────────────────────────────────────────────

    private AgentLoopService loop() {
        AgentLoopService svc = new AgentLoopService();
        ReflectionTestUtils.setField(svc, "llmGateway", llmGateway);
        ReflectionTestUtils.setField(svc, "guardrailsService", guardrails);
        ReflectionTestUtils.setField(svc, "agentConfigResolver", configResolver);
        // F06-13 规则 3 下"工具全部失败"必须改判拒绝答复，本类断言的是步骤行序列，
        // 故装置必须给出一个真实可用的执行器（旧装置依赖的 mock-success 兜底已废止 X-50）
        ToolExecutorService tools = mock(ToolExecutorService.class);
        when(tools.execute(anyString(), any())).thenReturn(
                ToolExecutorService.ToolResult.ok("call_1", "query_db", "{\"rows\":[]}", 1L));
        ReflectionTestUtils.setField(svc, "toolExecutorService", tools);
        return svc;
    }

    private AgentSession session() {
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionId()).thenReturn("s-trace-1");
        when(session.getProfileName()).thenReturn("default");
        return session;
    }

    /** 用行契约自己的解析器读回（写读同源，禁在断言里另立一套分隔符理解）。 */
    private List<AgentLoopTrace.Line> parsedLines() {
        List<AgentLoopTrace.Line> lines = new java.util.ArrayList<>();
        long seq = 0;
        for (ILoggingEvent event : appender.list) {
            AgentLoopTrace.Line line = AgentLoopTrace.parse(event.getFormattedMessage(), seq++);
            assertNotNull(line, "ECOS_AGENT_LOOP 通道只允许出现固定格式结构化行: "
                    + event.getFormattedMessage());
            lines.add(line);
        }
        return lines;
    }

    private List<String> rawLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
