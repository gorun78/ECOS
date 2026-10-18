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

    // ═══ WAgent run 流八帧（详设-10 §10.3.6；#143/B5-5b 收口 D-B5-8）═══

    @Test
    @DisplayName("W 八帧词表与序逐字钉住，milestone 是补充帧、永不进八帧序")
    void wagentEightFrameOrderIsTheFrozenOrder() {
        assertEquals(List.of("meta", "step", "token", "delta", "citation", "degrade", "error", "done"),
                SseFrameContract.WAGENT_FRAME_ORDER);
        assertEquals(8, SseFrameContract.WAGENT_FRAME_ORDER.size());
        assertEquals(1, SseFrameContract.wagentFrameIndex(SseFrameContract.FRAME_META));
        assertEquals(6, SseFrameContract.wagentFrameIndex(SseFrameContract.FRAME_DEGRADE));
        assertEquals(8, SseFrameContract.wagentFrameIndex(SseFrameContract.FRAME_DONE));
        assertEquals((int) AgentLoopTrace.UNKNOWN, SseFrameContract.wagentFrameIndex(SseFrameContract.FRAME_MILESTONE),
                "milestone 不在 §10.3.6 表内，序位只能是「未知」——给它一个号即冒充设计原文");
        assertEquals(9, SseFrameContract.wagentVocabulary().size(), "W 词表 = 八帧 + milestone");
    }

    @Test
    @DisplayName("八帧逐帧键面全在登记表内且首键恒为 traceId（禁实现侧自造第四套键）")
    void everyWagentFrameHasItsOwnKeyTable() {
        assertEquals(8, SseFrameContract.WAGENT_FRAME_KEYS.size());
        for (String frame : SseFrameContract.WAGENT_FRAME_ORDER) {
            List<String> keys = SseFrameContract.WAGENT_FRAME_KEYS.get(frame);
            assertTrue(keys != null && !keys.isEmpty(), frame + " 无键面登记表");
            assertEquals(SseFrameContract.KEY_TRACE_ID, keys.get(0), frame + " 首键必须是 traceId");
        }
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_DG_CODE,
                        SseFrameContract.KEY_APPLIES_TO, SseFrameContract.KEY_REQUIRES_ACK,
                        SseFrameContract.KEY_NOTE),
                SseFrameContract.WAGENT_FRAME_KEYS.get(SseFrameContract.FRAME_DEGRADE));
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_EVENT_ID,
                        SseFrameContract.KEY_EVENT_TYPE, SseFrameContract.KEY_OCCUR_TIME,
                        SseFrameContract.KEY_PAYLOAD_KIND),
                SseFrameContract.MILESTONE_FRAME_KEYS);
    }

    @Test
    @DisplayName("W 装配入口只认本通道词表：非登记键即抛，三枚白名单自加键放行")
    void wagentFrameFailsLoudOnThirdKeySet() {
        java.util.Map<String, Object> whitelisted = new java.util.LinkedHashMap<>();
        whitelisted.put(SseFrameContract.KEY_RUN_ID, "r1");
        whitelisted.put(SseFrameContract.KEY_STREAM_MODE, "table-polling-replay");
        whitelisted.put(SseFrameContract.KEY_EVENT_ID, "e1");
        Map<String, Object> ok = SseFrameContract.wagentFrame(SseFrameContract.FRAME_META, "trace-1", whitelisted);
        assertEquals(List.of(SseFrameContract.KEY_TRACE_ID, SseFrameContract.KEY_RUN_ID,
                        SseFrameContract.KEY_STREAM_MODE, SseFrameContract.KEY_EVENT_ID),
                List.copyOf(ok.keySet()));

        IllegalArgumentException keyEx = assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.wagentFrame(SseFrameContract.FRAME_META, "trace-1",
                        Map.of(SseFrameContract.KEY_GRADE, "A")));
        assertTrue(keyEx.getMessage().contains("§10.3.6"),
                "违例文案要点名设计出处，便于定位: " + keyEx.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.wagentFrame("stream_ready", "trace-1", Map.of()),
                "改前自有词形不得经 W 入口装配");
        assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.frame(SseFrameContract.FRAME_DEGRADE, "trace-1", Map.of()),
                "七帧入口不得认 degrade（agent-loop 面出现降级帧＝未经登记的通道扩词）");
    }

    @Test
    @DisplayName("降级载荷四键：无码或无说明即抛（空降级不得成帧）")
    void degradeFieldsRefuseEmptyCodeOrNote() {
        assertEquals(List.of(SseFrameContract.KEY_DG_CODE, SseFrameContract.KEY_APPLIES_TO,
                        SseFrameContract.KEY_REQUIRES_ACK, SseFrameContract.KEY_NOTE),
                List.copyOf(SseFrameContract.degradeFields("DG-D1", "D,K", true, "数据层降级").keySet()));
        assertEquals(AgentLoopTrace.ABSENT,
                SseFrameContract.degradeFields("DG-D1", " ", true, "note").get(SseFrameContract.KEY_APPLIES_TO),
                "appliesTo 不可得时写缺失标记，禁空串冒充「无层位」");
        assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.degradeFields(null, "D", true, "note"));
        assertThrows(IllegalArgumentException.class,
                () -> SseFrameContract.degradeFields("DG-D1", "D", true, "  "));
    }

    @Test
    @DisplayName("W 帧序校验：合法尾序放行，改前自有词形与 milestone 冒充首末帧必红")
    void wagentSequenceGateAcceptsLegalAndCatchesPreFixShape() {
        assertTrue(SseFrameContract.validateWagentSequence(
                List.of("meta", "degrade", "milestone", "step", "done")).isEmpty());
        assertTrue(SseFrameContract.validateWagentSequence(List.of("meta", "error", "done")).isEmpty());
        assertTrue(SseFrameContract.validateWagentSequence(
                List.of("meta", "milestone", "error", "done")).isEmpty());

        // 缺陷原形 D-B5-8：改前该流出的就是这四个自有词形，本闸必须逐条抓红
        for (String oldName : List.of("stream_ready", "run_terminal", "stream_timeout", "stream_error")) {
            assertTrue(SseFrameContract.validateWagentSequence(
                    List.of("meta", oldName, "done")).orElseThrow().contains("未知 WAgent 帧名"),
                    "改前词形未被抓红: " + oldName);
        }
        assertTrue(SseFrameContract.validateWagentSequence(List.of("meta", "milestone"))
                .orElseThrow().contains("末帧"), "milestone 不得冒充末帧");
        assertTrue(SseFrameContract.validateWagentSequence(List.of("degrade", "done"))
                .orElseThrow().contains("首帧"));
        assertTrue(SseFrameContract.validateWagentSequence(List.of("meta", "error", "step", "done"))
                .orElseThrow().contains("紧接 done"));

        // 通道互斥：七帧面校验必须拒绝 W 专有帧，否则两词表实际已合流
        assertTrue(SseFrameContract.validateSequence(
                List.of("meta", "degrade", "done")).orElseThrow().contains("未知帧名"));
    }
}
