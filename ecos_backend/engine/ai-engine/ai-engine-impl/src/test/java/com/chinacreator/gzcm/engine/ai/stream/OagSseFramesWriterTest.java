package com.chinacreator.gzcm.engine.ai.stream;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagStepEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAG 写出器用例（#143/B5-5a，D-B5-7）。
 *
 * <p>本类只管写出器自身（晚绑定、末帧复验、失败即不落半截流）；<b>通道级帧序</b>
 * 由入口驱动的 {@code OagSseChannelSequenceTest} 从 {@code OagController#streamChannel}
 * 断出，两者不可互替（沿用 B4-3d-4 {@code RateLimitChokepointTest} 的入口驱动纪律）。</p>
 */
@DisplayName("B5-5a OAG 写出器：晚绑定 traceId + 末帧复验无豁免")
class OagSseFramesWriterTest {

    private final List<String> events = new ArrayList<>();
    private final List<Map<String, Object>> payloads = new ArrayList<>();

    private OagSseFrames frames() {
        return new OagSseFrames((event, data) -> {
            events.add(event);
            payloads.add(data);
        });
    }

    @Test
    @DisplayName("写出面为空装即拒：sink 缺失不可能发出任何帧，禁静默成功")
    void rejectsNullSink() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new OagSseFrames(null));
        assertTrue(ex.getMessage().contains("SseFrameSink 必填"));
    }

    @Test
    @DisplayName("done 走 send() 即抛：绕过末帧复验属发射侧编程缺陷")
    void terminalFrameMustNotGoThroughSend() {
        OagSseFrames frames = frames();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> frames.send(OagStepEvent.done("COMPLETED", 12L, "trace-1", null, true)));
        assertTrue(ex.getMessage().contains("OagSseFrames.done"));
        assertTrue(events.isEmpty(), "抛之前不得落帧");
    }

    @Test
    @DisplayName("未绑定通道 traceId 时出缺失标记，禁返回 null")
    void traceIdBeforeBindingIsAbsentMarker() {
        OagSseFrames frames = frames();
        assertEquals(AgentLoopTrace.ABSENT, frames.traceId());
    }

    @Test
    @DisplayName("晚绑定首次生效、空白忽略、异值即抛（一次运行不得出现两个诊断码）")
    void bindsOnceAndRejectsDrift() {
        OagSseFrames frames = frames();
        frames.bindTraceId("  ");
        assertEquals(AgentLoopTrace.ABSENT, frames.traceId(), "空白候选不得占用绑定位");
        frames.bindTraceId("oag-abc");
        frames.bindTraceId("oag-abc");
        assertEquals("oag-abc", frames.traceId());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> frames.bindTraceId("oag-other"));
        assertTrue(ex.getMessage().contains("traceId 漂移"));
    }

    @Test
    @DisplayName("节点事件自带 traceId 即触发绑定，后续帧复用同一诊断码")
    void sendBindsFromEventPayload() throws IOException {
        OagSseFrames frames = frames();
        frames.send(OagStepEvent.nodeDone("IntentClassifier", "DONE", 5L,
                "oag-xyz", "sales-query", true));
        frames.done(OagStepEvent.done("COMPLETED", 20L, null, "sales-query", true));

        assertEquals(List.of("node", "done"), frames.emittedNames());
        // 末帧工厂传 null：写出器补通道值，前端才不会出现「有进度没诊断码」
        assertEquals("oag-xyz", payloads.get(1).get("traceId"));
    }

    @Test
    @DisplayName("帧序违例抛在写帧之前：违规流不落半截（response 两条 + done）")
    void sequenceViolationThrowsBeforeWritingTail() throws IOException {
        OagSseFrames frames = frames();
        frames.send(OagStepEvent.response("第一段", "oag-1"));
        frames.send(OagStepEvent.response("第二段", "oag-1"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> frames.done(OagStepEvent.done("COMPLETED", 9L, "oag-1", null, true)));
        assertTrue(ex.getMessage().contains("事件序违例"));
        assertEquals(List.of("response", "response"), events,
                "末帧必须在复验之后才落线路，否则前端先收到两条正文、事后才被判违规");
    }

    @Test
    @DisplayName("error 后还能发 node 也判违例：故障流不设豁免腿")
    void errorFollowedByNodeThenDoneIsStillAViolation() throws IOException {
        OagSseFrames frames = frames();
        frames.send(OagStepEvent.error("ReasoningEngine", "FAILED", 3L, "oag-2", "上游超时"));
        frames.send(OagStepEvent.nodeDone("AuditLogger", "DONE", 1L, "oag-2", null, false));

        assertThrows(IllegalStateException.class,
                () -> frames.done(OagStepEvent.done("FAILED", 4L, "oag-2", null, false)),
                "一旦为「已发过 error」放开复验，最需要对账的故障流反而不受任何帧序约束");
        assertEquals(List.of("error", "node"), events);
    }
}
