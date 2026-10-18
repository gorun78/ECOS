package com.chinacreator.gzcm.common.stream;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 承流帧契约单源（#143/B5-2，差距分析 §1.9.2 行「帧序固定七帧无契约测试」收口）。
 *
 * <p>断言取向：这里钉的是<b>契约形状</b>（词表、键序、缺失标记、序规则），
 * 不钉任何业务裁决 —— 裁决在 ai-engine 与安全通道，本类只保证「一处定义、错形必红」。</p>
 */
class SseFrameContractTest {

    @Test
    @DisplayName("七帧词表与序逐字钉住（§9.3.3 表首列）")
    void sevenFrameVocabularyIsTheFrozenOrder() {
        assertEquals(List.of("meta", "step", "token", "delta", "citation", "error", "done"),
                SseFrameContract.AGENT_LOOP_FRAME_ORDER);
        assertEquals(7, SseFrameContract.AGENT_LOOP_FRAME_ORDER.size());
        assertEquals(1, SseFrameContract.frameIndex(SseFrameContract.FRAME_META));
        assertEquals(7, SseFrameContract.frameIndex(SseFrameContract.FRAME_DONE));
        assertEquals((int) AgentLoopTrace.UNKNOWN, SseFrameContract.frameIndex("no_such_frame"));
        assertTrue(SseFrameContract.isTerminal(SseFrameContract.FRAME_DONE));
        assertFalse(SseFrameContract.isTerminal(SseFrameContract.FRAME_ERROR));
    }

    @Test
    @DisplayName("每帧必带 traceId 且落在首位，业务键按传入序追加（§1.9.2「每帧带 traceId」）")
    void frameAlwaysCarriesTraceIdFirst() {
        Map<String, Object> data = SseFrameContract.frame(SseFrameContract.FRAME_DELTA, "trace-1",
                Map.of(SseFrameContract.KEY_TEXT, "营收 12 亿"));
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_TEXT),
                List.copyOf(data.keySet()));
        assertEquals("trace-1", data.get(SseFrameContract.KEY_TRACE_ID));
    }

    @Test
    @DisplayName("traceId 缺失归一为单源缺失标记，禁 null 下发")
    void absentTraceIdNormalizesToMarker() {
        Map<String, Object> data = SseFrameContract.frame(SseFrameContract.FRAME_META, null, null);
        assertEquals(AgentLoopTrace.ABSENT, data.get(SseFrameContract.KEY_TRACE_ID));
        assertEquals(1, data.size());
    }

    @Test
    @DisplayName("调用方自带的 traceId 不得覆盖帧面 traceId，null 值键不落帧")
    void callerSuppliedTraceIdCannotOverrideAndNullKeysAreDropped() {
        Map<String, Object> data = SseFrameContract.frame(SseFrameContract.FRAME_STEP, "trace-9",
                new java.util.LinkedHashMap<>(Map.of(
                        SseFrameContract.KEY_TRACE_ID, "forged",
                        SseFrameContract.KEY_OP, "tool_call")));
        assertEquals("trace-9", data.get(SseFrameContract.KEY_TRACE_ID));
        assertEquals("tool_call", data.get(SseFrameContract.KEY_OP));
        assertEquals(2, data.size());

        java.util.Map<String, Object> withNull = new java.util.LinkedHashMap<>();
        withNull.put(SseFrameContract.KEY_TOOL, null);
        withNull.put(SseFrameContract.KEY_MODEL, "deepseek-chat");
        assertFalse(SseFrameContract.frame(SseFrameContract.FRAME_STEP, "t", withNull)
                .containsKey(SseFrameContract.KEY_TOOL));
    }

    @Test
    @DisplayName("未知帧名即抛（发射侧自造词形必须炸，不发往通道）")
    void unknownFrameNameFailsLoud() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.frame("tool_result", "trace-1", Map.of()));
        assertTrue(ex.getMessage().contains("SseFrameContract"), "违例文案要指名单源，便于定位: " + ex.getMessage());
    }

    @Test
    @DisplayName("用量帧：分项缺失写 -1 不写 0，estimated 标明估算态")
    void usageFrameUsesMinusOneInsteadOfFakeZero() {
        Map<String, Object> data = SseFrameContract.usageFrame("trace-1",
                SseFrameContract.unknownToken(), SseFrameContract.unknownToken(), false);
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_PROMPT_TOKENS,
                        SseFrameContract.KEY_COMPLETION_TOKENS, SseFrameContract.KEY_ESTIMATED),
                List.copyOf(data.keySet()));
        assertEquals(AgentLoopTrace.UNKNOWN, data.get(SseFrameContract.KEY_PROMPT_TOKENS));
        assertEquals(Boolean.FALSE, data.get(SseFrameContract.KEY_ESTIMATED));
        assertEquals(AgentLoopTrace.UNKNOWN, SseFrameContract.unknownToken(),
                "缺失标记单源在 AgentLoopTrace，承流面禁另写 -1");
    }

    @Test
    @DisplayName("引用帧四要素缺一即不发；齐备时 links 缺省为空列表")
    void citationFrameRefusesIncompleteCitation() {
        Map<String, Object> complete = Map.of(
                SseFrameContract.KEY_FORECAST_RUN_ID, "run-7",
                SseFrameContract.KEY_CALIBER_ID, "cal-revenue",
                SseFrameContract.KEY_VERSION, "v3",
                SseFrameContract.KEY_AS_OF_TIME, "2026-09-30T00:00:00Z");
        Optional<Map<String, Object>> frame = SseFrameContract.citationFrame("trace-1", complete);
        assertTrue(frame.isPresent());
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_FORECAST_RUN_ID,
                        SseFrameContract.KEY_CALIBER_ID, SseFrameContract.KEY_VERSION,
                        SseFrameContract.KEY_AS_OF_TIME, SseFrameContract.KEY_LINKS),
                List.copyOf(frame.get().keySet()));
        assertEquals(List.of(), frame.get().get(SseFrameContract.KEY_LINKS));

        for (String key : SseFrameContract.CITATION_REQUIRED_KEYS) {
            java.util.Map<String, Object> missingOne = new java.util.LinkedHashMap<>(complete);
            missingOne.put(key, " ");
            assertTrue(SseFrameContract.citationFrame("trace-1", missingOne).isEmpty(),
                    key + " 缺失/空白时不得发帧（发即伪造引用）");
        }
        assertTrue(SseFrameContract.citationFrame("trace-1", null).isEmpty());
    }

    @Test
    @DisplayName("故障帧只搬四键 + traceId，多余键（异常原文载体）一律不落帧")
    void errorFrameDropsAnythingBeyondTheFourKeys() {
        Map<String, Object> fromAiError = new java.util.LinkedHashMap<>();
        fromAiError.put(SseFrameContract.KEY_ERROR_CODE, "ECOS-AI-400");
        fromAiError.put(SseFrameContract.KEY_CLASS, "INVALID_ARGUMENT");
        fromAiError.put(SseFrameContract.KEY_MESSAGE, "参数不合法（诊断码 trace-1）");
        fromAiError.put(SseFrameContract.KEY_RETRYABLE, false);
        fromAiError.put("stack", "java.lang.NullPointerException ...");
        fromAiError.put("rawReason", "连接串: postgres://pwd@host");

        Map<String, Object> frame = SseFrameContract.errorFrame("trace-1", fromAiError);
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_ERROR_CODE,
                        SseFrameContract.KEY_CLASS, SseFrameContract.KEY_MESSAGE,
                        SseFrameContract.KEY_RETRYABLE),
                List.copyOf(frame.keySet()));
        assertFalse(String.valueOf(frame.values()).contains("NullPointerException"));
        assertTrue(SseFrameContract.errorFrame("trace-1", null).get(SseFrameContract.KEY_ERROR_CODE) == null);
    }

    @Test
    @DisplayName("合法帧序：meta 首、done 末、同类帧可重复、多轮 delta/step 交错不误报")
    void legalSequencesAreAccepted() {
        assertTrue(SseFrameContract.validateSequence(
                List.of("meta", "step", "step", "token", "delta", "delta", "citation", "done")).isEmpty());
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "error", "done")).isEmpty());
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "done")).isEmpty());
        // 多轮循环里 delta 之后再出一轮 step/token 属合规形态：本契约不校验全局单调（校验即自造判据）
        assertTrue(SseFrameContract.validateSequence(
                List.of("meta", "step", "delta", "step", "token", "delta", "done")).isEmpty());
    }

    @Test
    @DisplayName("违例帧序逐条具名抓红：空序列 / 缺首帧 / 缺末帧 / 未知帧 / error 位置与条数")
    void illegalSequencesAreRejectedByName() {
        assertEquals("空序列：承流必须至少含 meta 与 done 两帧",
                SseFrameContract.validateSequence(List.of()).orElseThrow());
        assertTrue(SseFrameContract.validateSequence(List.of("step", "done")).orElseThrow().contains("首帧"));
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "delta")).orElseThrow().contains("末帧"));
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "tool_call", "done"))
                .orElseThrow().contains("未知帧名"));
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "error", "error", "done"))
                .orElseThrow().contains("至多一条"));
        assertTrue(SseFrameContract.validateSequence(List.of("meta", "error", "delta", "done"))
                .orElseThrow().contains("紧接 done"));
    }

    @Test
    @DisplayName("textOrAbsent 复用日志面缺失标记，不产生第二种「无值」写法")
    void textOrAbsentReusesTraceMarker() {
        assertEquals(AgentLoopTrace.ABSENT, SseFrameContract.textOrAbsent(null));
        assertEquals(AgentLoopTrace.ABSENT, SseFrameContract.textOrAbsent("   "));
        assertEquals("sess-1", SseFrameContract.textOrAbsent("sess-1"));
    }
}
