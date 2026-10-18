package com.chinacreator.gzcm.engine.ai.stream;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 承流写出器状态机单测（#143/B5-2，详细设计-06 §9.3.3）。
 *
 * <p>本类只管<b>写出器自身的不变式</b>（首帧必发、违例即炸、无真值不发帧、缺失标记归一）；
 * 端点真实出帧序由 {@code AgentLoopSseFrameSequenceTest} 从入口驱动验证，
 * 两者不重叠 —— 只测本类不算收口（承流契约的验收面在入口）。</p>
 */
@DisplayName("B5-2 §9.3.3 承流写出器：帧序不变式 + 无真值不发帧")
class AgentLoopSseFramesWriterTest {

    /** 记录型 sink：同时留帧名与整份载荷，供逐帧断言。 */
    private static final class Recorder {
        private final List<String> events = new ArrayList<>();
        private final List<Map<String, Object>> payloads = new ArrayList<>();

        AgentLoopSseFrames.SseFrameSink sink() {
            return (event, data) -> {
                events.add(event);
                payloads.add(data);
            };
        }
    }

    private static AgentLoopSseFrames frames(Recorder rec) {
        return new AgentLoopSseFrames("trace-w", rec.sink());
    }

    @Test
    @DisplayName("空装 sink 即拒（承流写出面不允许静默无出口）")
    void nullSinkFailsLoudAtConstruction() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AgentLoopSseFrames("trace-x", null));
        assertTrue(ex.getMessage().contains("SseFrameSink"));
    }

    @Test
    @DisplayName("缺首帧 meta 就想收末帧 ⇒ 帧序违例必抛，不出错形流")
    void doneWithoutMetaThrowsSequenceViolation() {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> f.done(Map.of(SseFrameContract.KEY_TOTAL_TOKENS, 12)));
        assertTrue(ex.getMessage().contains("SSE 帧序违例"), ex.getMessage());
        assertTrue(ex.getMessage().contains(SseFrameContract.FRAME_META),
                "违例文案要指名缺的是哪一帧: " + ex.getMessage());
        // 抛错发生在写帧之前 ⇒ done 绝不落到通道（前端不会收到"没有 meta 却有 done"的流）
        assertEquals(List.of(), rec.events);
    }

    @Test
    @DisplayName("故障尾（error 已发）时 done 不复验抛错，兜底腿不得二次炸")
    void failureTailSuppressesSecondThrow() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.error(AiError.of(AiErrorClass.LLM_TIMEOUT, f.traceId()));
        f.done(Map.of("success", false));
        assertEquals(List.of(SseFrameContract.FRAME_ERROR, SseFrameContract.FRAME_DONE), rec.events);
    }

    @Test
    @DisplayName("step 帧：op 必填，decision 空白归一为 NA（不产生第二种无裁决写法）")
    void stepRequiresOpAndNormalizesDecision() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.step(AgentLoopTrace.Op.TOOL_CALL, 2, "forecast_query", "deepseek-chat", "  ");
        Map<String, Object> data = rec.payloads.get(0);
        assertEquals(AgentLoopTrace.Op.TOOL_CALL.wire(), data.get(SseFrameContract.KEY_OP));
        assertEquals(AgentLoopTrace.Decision.NA.name(), data.get(SseFrameContract.KEY_DECISION));
        assertEquals("forecast_query", data.get(SseFrameContract.KEY_TOOL));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> f.step(null, 3, null, null, "ALLOW"));
        assertTrue(ex.getMessage().contains("AgentLoopTrace.Op"), "要指名枚举单源: " + ex.getMessage());
    }

    @Test
    @DisplayName("step 帧 tool/model 空白不落键（禁下发空串冒充已知值）")
    void stepDropsBlankToolAndModel() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.step(AgentLoopTrace.Op.LLM_CALL, 1, "", "   ", AgentLoopTrace.Decision.ALLOW.name());
        Map<String, Object> data = rec.payloads.get(0);
        assertFalse(data.containsKey(SseFrameContract.KEY_TOOL));
        assertFalse(data.containsKey(SseFrameContract.KEY_MODEL));
        assertEquals(AgentLoopTrace.Decision.ALLOW.name(), data.get(SseFrameContract.KEY_DECISION));
    }

    @Test
    @DisplayName("delta 空文本不发帧（空正文帧属伪造，前端会渲染出空气泡）")
    void blankDeltaEmitsNothing() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.meta("sess-1", "deepseek-chat", null, null);
        f.delta(null);
        f.delta("");
        f.done(Map.of("success", true));
        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DONE), rec.events);
    }

    @Test
    @DisplayName("meta 数值缺失写 -1、字符串缺失写 -，禁 0 与 null 冒充")
    void metaNormalizesMissingValuesToSingleSourceMarkers() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.meta(null, null, null, null);
        Map<String, Object> data = rec.payloads.get(0);
        assertEquals(AgentLoopTrace.ABSENT, data.get(SseFrameContract.KEY_SESSION_ID));
        assertEquals(AgentLoopTrace.ABSENT, data.get(SseFrameContract.KEY_MODEL));
        assertEquals(AgentLoopTrace.UNKNOWN, data.get(SseFrameContract.KEY_CONTEXT_LIMIT));
        assertEquals(AgentLoopTrace.UNKNOWN, data.get(SseFrameContract.KEY_ESTIMATED_TOKENS));
        assertEquals("trace-w", data.get(SseFrameContract.KEY_TRACE_ID));
    }

    @Test
    @DisplayName("引用四要素不齐 ⇒ citation 返回 false 且不发帧（发即伪造引用）")
    void citationRefusesWhenBlocksAbsent() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        assertFalse(f.citation("根据数据，营收约为 12 亿元。"));
        assertFalse(f.citation(null));
        assertEquals(List.of(), rec.events);
    }

    @Test
    @DisplayName("引用块齐备 ⇒ citation 落帧，字段逐字取自上游渲染的块（承流侧不重算）")
    void citationEmitsWhenBlockComplete() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        String block = "[运行 run-7 · 口径 cal-rev@v3 · 截至 2026-09-30T00:00:00Z · 证据 ev-1,ev-2]";
        assertTrue(f.citation("前置文案 " + block + " 后置文案"));
        Map<String, Object> data = rec.payloads.get(0);
        assertEquals(SseFrameContract.FRAME_CITATION, rec.events.get(0));
        assertEquals("run-7", data.get(SseFrameContract.KEY_FORECAST_RUN_ID));
        assertEquals("cal-rev", data.get(SseFrameContract.KEY_CALIBER_ID));
        assertEquals("v3", data.get(SseFrameContract.KEY_VERSION));
        assertEquals("2026-09-30T00:00:00Z", data.get(SseFrameContract.KEY_AS_OF_TIME));
        assertEquals(List.of("ev-1", "ev-2"), data.get(SseFrameContract.KEY_LINKS));
    }

    @Test
    @DisplayName("故障帧载荷只有分类码 + 渲染文案 + 重试态，异常原文无入口")
    void errorFrameCarriesNoRawReason() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.error(AiError.of(AiErrorClass.TOOL_BACKEND_ERROR, f.traceId(),
                "jdbc:postgresql://10.0.0.1:5432/sys_man?password=secret"));
        Map<String, Object> data = rec.payloads.get(0);
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_ERROR_CODE,
                        SseFrameContract.KEY_CLASS, SseFrameContract.KEY_MESSAGE,
                        SseFrameContract.KEY_RETRYABLE),
                List.copyOf(data.keySet()));
        assertFalse(String.valueOf(data.values()).contains("password"),
                "原始失败原因（含连接串）绝不可入帧");
    }

    @Test
    @DisplayName("sink 抛 IOException 原样透传（写出器不吞通道故障）")
    void sinkIoExceptionPropagates() {
        AgentLoopSseFrames f = new AgentLoopSseFrames("trace-io",
                (event, data) -> {
                    throw new IOException("connection reset");
                });
        assertThrows(IOException.class, () -> f.meta("sess-9", "deepseek-chat", 8192, 120));
    }

    @Test
    @DisplayName("emittedNames 只读：写出器之外改不了帧序（防回退序列伪造）")
    void emittedNamesAreImmutable() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.meta("sess-1", "deepseek-chat", 8192, 120);
        List<String> names = f.emittedNames();
        assertThrows(UnsupportedOperationException.class, () -> names.add(SseFrameContract.FRAME_DONE));
        assertEquals(List.of(SseFrameContract.FRAME_META), f.emittedNames());
    }

    @Test
    @DisplayName("合规多轮流全序通过复验：meta/step/token/delta/citation/done")
    void legalFullSequenceWritesEveryFrame() throws IOException {
        Recorder rec = new Recorder();
        AgentLoopSseFrames f = frames(rec);
        f.meta("sess-1", "deepseek-chat", 8192, 120);
        f.step(AgentLoopTrace.Op.TOOL_CALL, 1, "forecast_query", null, AgentLoopTrace.Decision.ALLOW.name());
        f.step(AgentLoopTrace.Op.TOOL_RESULT, 1, "forecast_query", null, AgentLoopTrace.Decision.ALLOW.name());
        f.step(AgentLoopTrace.Op.LLM_CALL, 2, null, "deepseek-chat", AgentLoopTrace.Decision.NA.name());
        f.tokenUsage(AgentLoopTrace.UNKNOWN, AgentLoopTrace.UNKNOWN, false);
        f.delta("营收 12 亿元。");
        assertTrue(f.citation("[运行 run-1 · 口径 cal@v1 · 截至 2026-09-30 · 证据 ]"));
        Map<String, Object> done = new LinkedHashMap<>();
        done.put(SseFrameContract.KEY_TOTAL_TOKENS, 321);
        f.done(done);
        assertEquals(List.of("meta", "step", "step", "step", "token", "delta", "citation", "done"),
                rec.events);
        assertEquals(List.of("meta", "step", "step", "step", "token", "delta", "citation", "done"),
                f.emittedNames());
    }
}
