package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.stream.OagSseFrameContract;
import com.chinacreator.gzcm.engine.ai.stream.OagSseFrames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAG 承流<b>入口级</b>用例（#143/B5-5a，D-B5-7 收口）。
 *
 * <p>帧序必须从真实发射面（{@code OagController#streamChannel}）断出来，而不是
 * "加个写出器再单测它自己"——后者对"控制器漏发末帧""blocked 只写 message"这类
 * 真实缺陷完全无感（沿用 B4-3d-4 / B5-2 的入口驱动纪律）。</p>
 *
 * <p>覆盖边界（如实登记）：本类驱动的是通道腿 {@code streamChannel}，
 * {@code chatStream} 里 {@code SseEmitter} 装配与 Spring MVC 的 SSE 序列化不经此处，
 * 属 live 面待验（同 B5-2 已登记的口径）。</p>
 */
@DisplayName("B5-5a 入口驱动：OAG 四条真实腿的帧序 + 载荷键面")
class OagSseChannelSequenceTest {

    private OagController controller;
    private OagPipelineEngine engine;
    private final List<String> events = new ArrayList<>();
    private final List<Map<String, Object>> payloads = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new OagController();
        engine = mock(OagPipelineEngine.class);
        ReflectionTestUtils.setField(controller, "pipelineEngine", engine);
        events.clear();
        payloads.clear();
    }

    private OagSseFrames frames() {
        return new OagSseFrames((event, data) -> {
            events.add(event);
            payloads.add(data);
        });
    }

    private static Map<String, Object> body(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (message != null) {
            body.put("message", message);
        }
        body.put("userId", "u-1");
        body.put("tenantId", "t-1");
        return body;
    }

    private static Map<String, Object> nodeEvent(OagPipelineContext ctx, String node) {
        Map<String, Object> event = new LinkedHashMap<>(ctx.toSummary());
        event.put("node", node);
        event.put("nodeElapsedMs", 7L);
        return event;
    }

    /** 桩：管道执行时先按序回调节点事件，再返回给定上下文。 */
    private void stubPipeline(OagPipelineContext ctx, List<String> nodes) {
        when(engine.run(anyString(), any(), anyString(), anyString(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<Map<String, Object>> callback = inv.getArgument(4);
            if (callback != null) {
                for (String node : nodes) {
                    callback.accept(nodeEvent(ctx, node));
                }
            }
            return ctx;
        });
    }

    private static OagPipelineContext context(String status) {
        OagPipelineContext ctx = new OagPipelineContext("u-1", "t-1");
        ctx.setStatus(status);
        ctx.setIntent("sales-query");
        return ctx;
    }

    private void assertSequenceIsContractLegal() {
        assertTrue(OagSseFrameContract.validateSequence(events).isEmpty(),
                "入口发出的整条事件序必须通过词表自身的复验，实测=" + events);
        for (Map<String, Object> payload : payloads) {
            assertEquals("traceId", payload.keySet().iterator().next(),
                    "每帧首键必须是 traceId（审计按位取首键对账）");
        }
    }

    @Test
    @DisplayName("COMPLETED 腿：node×2→response→done，逐帧 traceId 与管道上下文同值")
    void completedLegWritesResponseThenTerminalDone() {
        OagPipelineContext ctx = context("COMPLETED");
        ctx.setSecurityPassed(true);
        ctx.setFinalResponse("第三季度营收 12 亿元");
        stubPipeline(ctx, List.of("IntentClassifier", "ResponseCompiler"));

        controller.streamChannel(frames(), body("查一下三季度营收"));

        assertEquals(List.of("node", "node", "response", "done"), events);
        assertSequenceIsContractLegal();
        assertEquals("第三季度营收 12 亿元", payloads.get(2).get("content"));
        assertEquals("COMPLETED", payloads.get(3).get("status"));
        // 节点进度腿的键读取面：引擎内部事件 Map 与 SSE 载荷同名，任一侧改名即进度条空白
        assertEquals("IntentClassifier", payloads.get(0).get("node"));
        assertEquals("sales-query", payloads.get(0).get("intent"));
        assertEquals(Boolean.TRUE, payloads.get(0).get("securityPassed"));
        assertEquals(7L, payloads.get(0).get("nodeElapsedMs"));
        // 通道 traceId 单源：node 帧与末帧不得各说各话
        assertEquals(ctx.getTraceId(), payloads.get(0).get("traceId"));
        assertEquals(ctx.getTraceId(), payloads.get(3).get("traceId"));
    }

    @Test
    @DisplayName("BLOCKED 腿：拦截原因走 reason 键落到前端读取位（D-B5-13 收口）")
    void blockedLegExposesReasonOnTheKeyFrontendReads() {
        OagPipelineContext ctx = context("BLOCKED");
        ctx.setSecurityPassed(false);
        ctx.setSecurityBlockReason("安全护栏拒绝：越权查询他租户数据");
        stubPipeline(ctx, List.of("SecurityChecker"));

        controller.streamChannel(frames(), body("查隔壁租户的营收"));

        assertEquals(List.of("node", "blocked", "done"), events);
        assertSequenceIsContractLegal();
        Map<String, Object> blocked = payloads.get(1);
        assertEquals("安全护栏拒绝：越权查询他租户数据", blocked.get("reason"),
                "前端 onBlocked 只读 reason；改前该腿写 message ⇒ 界面表现为「被拦截但不知道为什么」");
        assertEquals(Boolean.FALSE, blocked.get("securityPassed"));
        assertEquals("BLOCKED", payloads.get(2).get("status"),
                "OAG 终态判定在 done 帧 status 键上，不靠帧名区分成功/失败");
    }

    @Test
    @DisplayName("FAILED 腿：error 紧接 done 之前，两条都在（改前缺末帧，流只能靠断连收尾）")
    void failedLegWritesErrorImmediatelyBeforeTerminalDone() {
        OagPipelineContext ctx = context("FAILED");
        ctx.setSecurityPassed(false);
        ctx.setErrorMessage("推理服务不可用");
        stubPipeline(ctx, List.of("ReasoningEngine"));

        controller.streamChannel(frames(), body("查一下三季度营收"));

        assertEquals(List.of("node", "error", "done"), events);
        assertSequenceIsContractLegal();
        assertEquals("推理服务不可用", payloads.get(1).get("message"));
        assertEquals("FAILED", payloads.get(2).get("status"));
    }

    @Test
    @DisplayName("入参空白腿：不进管道也要发 error→done，禁静默空流")
    void blankMessageStillWritesFailureTailWithoutRunningPipeline() {
        controller.streamChannel(frames(), body(null));

        assertEquals(List.of("error", "done"), events);
        assertSequenceIsContractLegal();
        assertEquals("message 不能为空", payloads.get(0).get("message"));
        verify(engine, never()).run(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("管道抛异常腿：只出模板+诊断码，异常原文入日志不入帧（D-B5-14）")
    void pipelineThrowNeverLeaksRawTextIntoFrames() {
        when(engine.run(anyString(), any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException(
                        "JDBC 连接失败 jdbc:postgresql://127.0.0.1:5432/sys_man?password=secret 第 47 行"));

        controller.streamChannel(frames(), body("查一下三季度营收"));

        assertEquals(List.of("error", "done"), events);
        assertSequenceIsContractLegal();
        String message = assertInstanceOf(String.class, payloads.get(0).get("message"));
        assertTrue(message.startsWith("OAG 管道失败"), "承流错误帧要出给用户看的模板文案，实测=" + message);
        assertTrue(message.contains("诊断码"), "缺诊断码则用户报了错也无法与审计对账");
        for (String forbidden : List.of("jdbc:", "postgresql", "password", "IllegalStateException", "第 47 行")) {
            assertTrue(!message.contains(forbidden),
                    "error 帧携带异常原文 " + forbidden + " ⇒ 数据源串/口令经承流面外泄：" + message);
        }
    }
}
