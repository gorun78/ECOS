package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.engine.ai.stream.AgentLoopSseFrames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SSE 七帧承流契约的<b>入口级</b>用例（#143/B5-2，详细设计-06 §1.9.2 + §9.3.3）。
 *
 * <p>纪律：帧序必须从真实出帧面（{@code AgentLoopController#runStream}）断出来，
 * 而不是"加个写出器再单测它自己"—— 后者对"控制器漏发 meta""正文仍发成 token 帧"
 * 这类真实缺陷完全无感（沿用 B4-3d-4 {@code RateLimitChokepointTest} 的入口驱动纪律）。</p>
 *
 * <p>覆盖边界（如实登记）：本类驱动的是写出面 {@code runStream}/{@code failClosedTail}，
 * {@code chatStream} 里 {@code SseEmitter} 装配腿（event 名 → SSE 报文、complete 信号）
 * 不经此处；Spring MVC 侧的 SSE 序列化没有可注入的观测点，属 live 面待验。</p>
 */
@DisplayName("B5-2 §9.3.3 承流入口：七帧序 + 每帧 traceId + 无真值不发帧")
class AgentLoopSseFrameSequenceTest {

    private static final String TRACE = "trace-e";
    private static final String CITATION_BLOCK =
            "[运行 run-9 · 口径 cal-rev@v3 · 截至 2026-09-30T00:00:00Z · 证据 ev-1,ev-2]";

    private AgentLoopController controller;
    private AgentLoopService service;
    private List<String> events;
    private List<Map<String, Object>> payloads;

    @BeforeEach
    void setUp() {
        controller = new AgentLoopController();
        service = mock(AgentLoopService.class);
        ReflectionTestUtils.setField(controller, "agentLoopService", service);
        events = new ArrayList<>();
        payloads = new ArrayList<>();
    }

    private AgentLoopSseFrames frames() {
        return new AgentLoopSseFrames(TRACE, (event, data) -> {
            events.add(event);
            payloads.add(data);
        });
    }

    private static Map<String, Object> toolRecord(String toolName, int turn, String decision) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("turn", turn);
        record.put("toolName", toolName);
        record.put("toolCallId", "call-1");
        record.put("durationMs", 42);
        record.put("success", true);
        record.put("resultSnippet", "营收 12 亿元");
        if (decision != null) {
            record.put("decision", decision);
        }
        return record;
    }

    private static Map<String, Object> body(String message, String agentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("sessionId", "sess-1");
        body.put("model", "deepseek-chat");
        body.put("agentId", agentId);
        return body;
    }

    @Test
    @DisplayName("§9.3.3 happy path：meta→step×3→token→delta×2→citation→done，帧名不得再是 tool_call/tool_result")
    void happyPathWritesContractSequence() throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Object> withCitation = toolRecord("forecast_query", 1, AgentLoopTrace.Decision.ALLOW.name());
        withCitation.put("citationBlock", CITATION_BLOCK);
        records.add(withCitation);
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。\n\n同比增长 8%。",
                2, "sess-1", 321, records);
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查询 Q3 营收", "default"), frames());

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_STEP,
                        SseFrameContract.FRAME_STEP, SseFrameContract.FRAME_STEP,
                        SseFrameContract.FRAME_TOKEN, SseFrameContract.FRAME_DELTA,
                        SseFrameContract.FRAME_DELTA, SseFrameContract.FRAME_CITATION,
                        SseFrameContract.FRAME_DONE),
                events);
        assertTrue(SseFrameContract.validateSequence(events).isEmpty(),
                () -> "入口写出的帧序必须通过契约复验: " + SseFrameContract.validateSequence(events));
        // 旧形态把工具事件当帧名（tool_call/tool_result），§9.3.3 把它们收为 step 帧的 op 取值
        assertFalse(events.contains("tool_call"), "tool_call 不得再作 event 名");
        assertFalse(events.contains("tool_result"), "tool_result 不得再作 event 名");
        // 旧形态把正文增量发成 token 帧：token 帧名只允许是用量帧
        assertEquals(1, events.stream().filter(SseFrameContract.FRAME_TOKEN::equals).count());
    }

    @Test
    @DisplayName("§1.9.2 每帧带 traceId（同一条，取请求线程捕获值），且 meta 内容口径正确")
    void everyFrameCarriesTheSameTraceId() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("今日无异常。", 1, "sess-1", 50, List.of());
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("随便聊聊", "default"), frames());

        payloads.forEach(p -> assertEquals(TRACE, p.get(SseFrameContract.KEY_TRACE_ID)));
        assertEquals(events.size(), payloads.size());
        Map<String, Object> meta = payloads.get(0);
        assertEquals("sess-1", meta.get(SseFrameContract.KEY_SESSION_ID));
        assertEquals("deepseek-chat", meta.get(SseFrameContract.KEY_MODEL));
        // 上下文上限未由 buildConfig 设定 ⇒ -1（未上报），禁伪造一个默认窗口值
        assertEquals(AgentLoopTrace.UNKNOWN, meta.get(SseFrameContract.KEY_CONTEXT_LIMIT));
    }

    @Test
    @DisplayName("step 帧逐条带 op/step/tool/decision；正文增量走 delta 且拼接后与终稿逐字相等")
    void stepAndDeltaFramesCarryTheirOwnKeys() throws Exception {
        Map<String, Object> record = toolRecord("forecast_query", 1, AgentLoopTrace.Decision.ALLOW.name());
        AgentLoopResult result = AgentLoopResult.success("第一段。\n第二段。", 1, "sess-1", 80, List.of(record));
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        Map<String, Object> callFrame = payloads.get(1);
        Map<String, Object> resultFrame = payloads.get(2);
        assertEquals(AgentLoopTrace.Op.TOOL_CALL.wire(), callFrame.get(SseFrameContract.KEY_OP));
        assertEquals(AgentLoopTrace.Op.TOOL_RESULT.wire(), resultFrame.get(SseFrameContract.KEY_OP));
        assertEquals("forecast_query", callFrame.get(SseFrameContract.KEY_TOOL));
        assertEquals(1, callFrame.get(SseFrameContract.KEY_STEP));
        assertEquals(AgentLoopTrace.Decision.ALLOW.name(), callFrame.get(SseFrameContract.KEY_DECISION));

        List<String> deltas = new ArrayList<>();
        for (int i = 3; i < payloads.size(); i++) {
            if (SseFrameContract.FRAME_DELTA.equals(events.get(i))) {
                deltas.add(String.valueOf(payloads.get(i).get(SseFrameContract.KEY_TEXT)));
            }
        }
        assertEquals("第一段。\n第二段。", String.join("", deltas),
                "delta 分片拼接必须与终稿逐字相等（分段不得吞字或加字）");
    }

    @Test
    @DisplayName("token 帧只有用量语义，分项未传播 ⇒ -1/-1 且 estimated=false（禁拿 totalTokens 折算冒充分项）")
    void usageFrameNeverFakesBreakdown() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 999, List.of());
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        int tokenAt = events.indexOf(SseFrameContract.FRAME_TOKEN);
        assertTrue(tokenAt >= 0, "成功腿必须出一帧用量");
        Map<String, Object> token = payloads.get(tokenAt);
        assertEquals(AgentLoopTrace.UNKNOWN, token.get(SseFrameContract.KEY_PROMPT_TOKENS));
        assertEquals(AgentLoopTrace.UNKNOWN, token.get(SseFrameContract.KEY_COMPLETION_TOKENS));
        assertEquals(Boolean.FALSE, token.get(SseFrameContract.KEY_ESTIMATED));
        assertEquals(999, payloads.get(payloads.size() - 1).get(SseFrameContract.KEY_TOTAL_TOKENS),
                "totalTokens 仍如实落在 done 帧，不被搬去冒充分项");
    }

    @Test
    @DisplayName("引用不齐 ⇒ 整条流不出 citation 帧（宁缺毋造），帧序仍合法")
    void noCitationFrameWithoutUpstreamBlock() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30,
                List.of(toolRecord("forecast_query", 1, AgentLoopTrace.Decision.ALLOW.name())));
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        assertFalse(events.contains(SseFrameContract.FRAME_CITATION),
                "无确定性引用块时不得发引用帧");
        assertTrue(SseFrameContract.validateSequence(events).isEmpty(),
                () -> "帧序仍需通过契约复验: " + events);
    }

    @Test
    @DisplayName("引用帧字段逐字取自工具记录的 citationBlock（承流侧不重算口径）")
    void citationFrameFieldsComeFromToolRecord() throws Exception {
        Map<String, Object> record = toolRecord("forecast_query", 1, AgentLoopTrace.Decision.ALLOW.name());
        record.put("citationBlock", "分析结论：" + CITATION_BLOCK);
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30, List.of(record));
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        Map<String, Object> citation = payloads.get(events.indexOf(SseFrameContract.FRAME_CITATION));
        assertEquals("run-9", citation.get(SseFrameContract.KEY_FORECAST_RUN_ID));
        assertEquals("cal-rev", citation.get(SseFrameContract.KEY_CALIBER_ID));
        assertEquals("v3", citation.get(SseFrameContract.KEY_VERSION));
        assertEquals("2026-09-30T00:00:00Z", citation.get(SseFrameContract.KEY_AS_OF_TIME));
        assertEquals(List.of("ev-1", "ev-2"), citation.get(SseFrameContract.KEY_LINKS));
    }

    @Test
    @DisplayName("请求体非法 ⇒ meta 已出再 error→done（早退分支也守帧序），且推理循环零调用")
    void blankMessageStillClosesWithFrameOrder() throws Exception {
        controller.runStream(body("   ", "default"), frames());

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_ERROR,
                SseFrameContract.FRAME_DONE), events);
        Map<String, Object> error = payloads.get(1);
        assertEquals("ECOS-AI-400", error.get(SseFrameContract.KEY_ERROR_CODE));
        assertEquals("INVALID_ARGUMENT", error.get(SseFrameContract.KEY_CLASS));
        assertFalse(error.containsKey(SseFrameContract.KEY_TEXT));
        verify(service, never()).run(any(), anyString(), any());
    }

    @Test
    @DisplayName("熔断打开 ⇒ 早退腿出 meta→error(LLM_RATE_LIMITED)→done，请求不再进 Loop")
    void openCircuitShortCircuitsBeforeLoop() throws Exception {
        AgentLoopResult failure = AgentLoopResult.error("sess-1", "下游服务异常（诊断码 " + TRACE + "）", 0);
        failure.setErrorClass("TOOL_BACKEND_ERROR");
        when(service.run(any(), anyString(), any())).thenReturn(failure);

        // 三次真实失败把该 agentId 的熔断推到 OPEN（走生产计数，不手工改状态）
        for (int i = 0; i < 3; i++) {
            controller.runStream(body("查营收", "b52-open-agent"), frames());
        }
        events.clear();
        payloads.clear();
        controller.runStream(body("查营收", "b52-open-agent"), frames());

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_ERROR,
                SseFrameContract.FRAME_DONE), events);
        assertEquals("ECOS-AI-429", payloads.get(1).get(SseFrameContract.KEY_ERROR_CODE));
        verify(service, times(3)).run(any(), anyString(), any());
    }

    @Test
    @DisplayName("失败腿不编正文：只出 error→done，两帧分类同源，专项文案留在 done")
    void failureLegEmitsErrorThenDoneOnly() throws Exception {
        AgentLoopResult failure = AgentLoopResult.error("sess-1",
                "会话 token 配额超限: 已用 9,120/上限 8,000（诊断码 " + TRACE + "）", 10);
        failure.setErrorClass("CONTEXT_OVERFLOW");
        failure.setErrorCode("ECOS-AI-413");
        when(service.run(any(), anyString(), any())).thenReturn(failure);

        controller.runStream(body("查营收", "default"), frames());

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_ERROR,
                SseFrameContract.FRAME_DONE), events);
        Map<String, Object> error = payloads.get(1);
        Map<String, Object> done = payloads.get(2);
        assertEquals("ECOS-AI-413", error.get(SseFrameContract.KEY_ERROR_CODE));
        assertEquals("CONTEXT_OVERFLOW", error.get(SseFrameContract.KEY_CLASS));
        // error 帧按 §9.3.3 只出分类模板文案；上游已渲染的专项文案走 done.message，两帧不同形但同源
        assertEquals(error.get(SseFrameContract.KEY_ERROR_CODE), done.get(SseFrameContract.KEY_ERROR_CODE));
        assertEquals("CONTEXT_OVERFLOW", done.get("errorClass"));
        assertEquals(failure.getErrorMsg(), done.get(SseFrameContract.KEY_MESSAGE));
        assertEquals(Boolean.FALSE, done.get("success"));
    }

    @Test
    @DisplayName("压缩态未判定 ⇒ done 三个键全缺席（前端渲染未上报，禁伪造默认值）")
    void compressionKeysAbsentWhenNotAssessed() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30, List.of());
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        Map<String, Object> done = payloads.get(payloads.size() - 1);
        assertFalse(done.containsKey(SseFrameContract.KEY_COMPRESSION_MODE));
        assertFalse(done.containsKey("compressedTurns"));
        assertFalse(done.containsKey("compressSkipped"));
    }

    @Test
    @DisplayName("压缩态已判定 ⇒ done 原样上报（含 skipped=true 这种不利形态）")
    void compressionKeysReportedWhenAssessed() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30, List.of());
        result.applyCompression(null, 0, true);
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        Map<String, Object> done = payloads.get(payloads.size() - 1);
        assertEquals(Boolean.TRUE, done.get("compressSkipped"),
                "未压缩必须原样上报，不得因压缩没发生就不报");
    }

    @Test
    @DisplayName("工具记录缺 decision ⇒ step 帧落 NA（无裁决环节的单源写法）")
    void missingDecisionInToolRecordNormalizesToNa() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30,
                List.of(toolRecord("forecast_query", 1, null)));
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        assertEquals(AgentLoopTrace.Decision.NA.name(), payloads.get(1).get(SseFrameContract.KEY_DECISION));
    }

    @Test
    @DisplayName("工具记录列表为 null ⇒ 不编造工具轮 step、不出 citation，且不炸（承流面不得因上游空集断流）")
    void nullToolRecordsYieldNoStepFrames() throws Exception {
        AgentLoopResult result = AgentLoopResult.success("营收 12 亿元。", 1, "sess-1", 30, List.of());
        result.setToolCalls(null);
        when(service.run(any(), anyString(), any())).thenReturn(result);

        controller.runStream(body("查营收", "default"), frames());

        // 七帧序里 step 恒有 LLM_CALL 一条（§9.3.3 帧 2）；null 工具集的语义是"没有工具轮 step"，不是"没有 step"
        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_STEP,
                        SseFrameContract.FRAME_TOKEN, SseFrameContract.FRAME_DELTA,
                        SseFrameContract.FRAME_DONE),
                events);
        assertEquals(AgentLoopTrace.Op.LLM_CALL.wire(), payloads.get(1).get(SseFrameContract.KEY_OP));
        assertFalse(events.contains(SseFrameContract.FRAME_CITATION),
                "引用帧只能来自工具返回体的引用块，空工具集不得凭空造帧");
    }

    @Test
    @DisplayName("兜底尾帧：异常原文不入帧，meta 缺席时先补首帧")
    void failClosedTailPrependsMetaAndLeaksNothing() throws Exception {
        AgentLoopSseFrames f = frames();
        f.meta("sess-1", "deepseek-chat", null, null);
        controller.failClosedTail(f, new RuntimeException("jdbc:postgresql://u:p@10.0.0.1/sys_man"));

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_ERROR,
                SseFrameContract.FRAME_DONE), f.emittedNames());
        String rendered = String.valueOf(payloads.get(1).values()) + String.valueOf(payloads.get(2).values());
        assertFalse(rendered.contains("jdbc:postgresql"), "异常原文（含连接串）不得入帧: " + rendered);

        AgentLoopSseFrames empty = frames();
        controller.failClosedTail(empty, new IllegalStateException("boom"));
        assertEquals(SseFrameContract.FRAME_META, empty.emittedNames().get(0),
                "首帧必发：尾帧腿在空序列上要先补 meta");
    }

    @Test
    @DisplayName("通道已断（sink 抛 IOException）时兜底腿不得二次异常，只记日志")
    void failClosedTailSwallowsClosedChannel() {
        AgentLoopSseFrames broken = new AgentLoopSseFrames(TRACE, (event, data) -> {
            throw new IOException("connection reset");
        });
        assertDoesNotThrow(() -> controller.failClosedTail(broken,
                new RuntimeException("boom")));
        assertEquals(List.of(), broken.emittedNames(), "写不出去的帧不得记入已写序列");
    }
}
