package com.chinacreator.gzcm.common.stream;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAG 通道承流事件单源用例（#143/B5-5a，D-B5-7）。
 *
 * <p>本类钉的是"词表 + 键面 + 末帧规则"三件事，写法上有意识地避开只测常量值：
 * 每条断言都对应一个改前真实可发生的漂移形态（发射点自拼事件名、traceId 为 null 时整键缺席、
 * 失败腿不发末帧），故任一条在生产侧被破坏时都会变红，不是"读一遍常量自证清白"。</p>
 */
@DisplayName("B5-5a OAG 事件单源：词表/键面/事件序")
class OagSseFrameContractTest {

    /** 词表枚数下限（前端 handlers 消费 node/response/blocked/error/done 五名，少一名即断供）。 */
    private static final int EVENT_VOCABULARY_FLOOR = 5;

    @Test
    @DisplayName("词表恰为 node/response/blocked/error/done，且 traceId 键与 agent-loop 通道同源")
    void vocabularyMatchesFrontendHandlersAndSharesTraceIdKey() {
        assertEquals(EVENT_VOCABULARY_FLOOR, OagSseFrameContract.events().size(),
                "OAG 事件词表枚数漂移 ⇒ 前端 handlers 或网关透传必有一侧读不到新名字");
        assertEquals(List.of("node", "response", "blocked", "error", "done"),
                OagSseFrameContract.events(),
                "事件名是既有 API 面（API 只增不改），改名即前端解析器无帧可读");
        // 跨通道同名键必须字面量同源，禁两处各抄一遍后各自漂移
        assertEquals(SseFrameContract.KEY_TRACE_ID, OagSseFrameContract.KEY_TRACE_ID);
        assertEquals(9, OagSseFrameContract.keys().size());
    }

    @Test
    @DisplayName("traceId 缺失写单源标记，禁整键缺席；业务键里的 traceId 不得覆盖通道位")
    void payloadAlwaysCarriesTraceIdAndRejectsOverride() {
        Map<String, Object> payload = OagSseFrameContract.payload(
                OagSseFrameContract.EVENT_ERROR, null,
                Map.of(OagSseFrameContract.KEY_TRACE_ID, "伪造值",
                        OagSseFrameContract.KEY_MESSAGE, "message 不能为空"));

        assertEquals(AgentLoopTrace.ABSENT, payload.get("traceId"),
                "traceId 为 null 时整键缺席 ⇒ 前端错误态无诊断码可显示");
        assertEquals("message 不能为空", payload.get("message"));
        // 首键固定 traceId：前端/审计按位取首键做对账，插入顺序也是契约的一部分
        assertEquals("traceId", payload.keySet().iterator().next());
    }

    @Test
    @DisplayName("value 为 null 的业务键跳写；未知事件名 fail-loud 抛 IAE")
    void payloadSkipsNullFieldsAndFailsLoudOnUnknownEvent() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(OagSseFrameContract.KEY_NODE, "IntentClassifier");
        fields.put(OagSseFrameContract.KEY_INTENT, null);
        Map<String, Object> payload = OagSseFrameContract.payload(
                OagSseFrameContract.EVENT_NODE, "trace-1", fields);

        assertEquals("trace-1", payload.get("traceId"));
        assertEquals("IntentClassifier", payload.get("node"));
        assertFalse(payload.containsKey("intent"),
                "意图未识别时该键缺席即表达未知；写 null 会让前端把 null 渲染成字符串");
        assertThrows(IllegalArgumentException.class,
                () -> OagSseFrameContract.payload("token", "trace-1", Map.of()),
                "token 属 agent-loop 七帧词表，出现在 OAG 通道即两通道混用，必须抛而非静默发出");
        assertThrows(IllegalArgumentException.class,
                () -> OagSseFrameContract.payload(null, "trace-1", Map.of()));
    }

    @Test
    @DisplayName("合法序（node×2→response→done）通过；缺末帧/空序即红")
    void legalSequencePassesAndMissingTailFails() {
        assertTrue(OagSseFrameContract
                .validateSequence(List.of("node", "node", "response", "done")).isEmpty());
        assertTrue(OagSseFrameContract.validateSequence(List.of("done")).isEmpty(),
                "参数校验失败腿可以只有末帧，禁把它判成违例");

        assertEquals("空序列：OAG 承流必须至少以 done 收尾",
                OagSseFrameContract.validateSequence(List.of()).orElse("漏判"));
        // 改前真实缺陷形态：管道抛异常后只 completeWithError，流里根本没有 done
        assertTrue(OagSseFrameContract.validateSequence(List.of("node", "response"))
                .orElse("")
                .contains("末帧必须为 done"));
    }

    @Test
    @DisplayName("error 至多一条且必须紧邻 done 之前；response 至多一条")
    void errorAndResponseCountsAreBounded() {
        assertTrue(OagSseFrameContract.validateSequence(List.of("error", "done")).isEmpty());
        assertTrue(OagSseFrameContract.validateSequence(List.of("node", "error", "done")).isEmpty());

        Optional<String> misplaced = OagSseFrameContract.validateSequence(
                List.of("error", "node", "done"));
        assertTrue(misplaced.isPresent() && misplaced.get().contains("error 事件必须紧接 done 之前"),
                "error 后还能发 node ⇒ 前端把已判定失败的流继续渲染成进度，违例必须被指出");

        assertTrue(OagSseFrameContract.validateSequence(List.of("error", "error", "done"))
                .orElse("").contains("error 事件至多一条"));
        assertTrue(OagSseFrameContract.validateSequence(
                List.of("response", "response", "done")).orElse("").contains("response 事件至多一条"));
    }

    @Test
    @DisplayName("不校验首帧：OAG 无 meta 通道帧属既有形状，凭空规定首帧＝自造判据")
    void firstFrameIsNotConstrainedBecauseOagHasNoMetaFrame() {
        assertFalse(OagSseFrameContract.validateSequence(List.of("response", "done")).isPresent(),
                "以 response 起始的流在改前就是合规的，加首帧判据会把真实合规流判红");
        assertEquals(1, OagSseFrameContract.eventIndex(OagSseFrameContract.EVENT_NODE),
                "事件位与七帧面 frameIndex 同口径（1 起）：同一概念在两条通道必须同一 numbering");
        assertEquals((int) AgentLoopTrace.UNKNOWN, OagSseFrameContract.eventIndex("meta"),
                "未知事件名写单源 UNKNOWN 标记，禁自造 -1");
        assertEquals((int) AgentLoopTrace.UNKNOWN, OagSseFrameContract.eventIndex(null));
        assertTrue(OagSseFrameContract.isTerminal("done"));
        assertFalse(OagSseFrameContract.isTerminal("error"));
    }
}
