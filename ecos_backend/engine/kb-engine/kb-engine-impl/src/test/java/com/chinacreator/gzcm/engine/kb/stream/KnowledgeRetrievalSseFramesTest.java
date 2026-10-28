package com.chinacreator.gzcm.engine.kb.stream;

import com.chinacreator.gzcm.common.stream.SseFrameContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KnowledgeRetrievalSseFrames} 帧序门（附录 A 第 14 行 {@code generateKnowledgeAnswer}：事件
 * {@code delta / citations / done}，末帧带 traceId）。
 *
 * <p><b>本文件钉三件事</b>：① 帧名与帧序由 common-api 单源
 * {@link SseFrameContract#validateSequence} 复验（本写出器不自造词表，也不发单源不认识的
 * {@code citations} 复数帧）；② 缺 {@code meta} 直接 {@code done}、空终态这类编程缺陷 fail-loud；
 * ③ 空正文 / 缺引用要素一律不发帧（宁缺毋造），且 {@code done} 与 {@code meta} 的 {@code hits}
 * 是同一个数（终态与首帧不得各说一套）。</p>
 */
@DisplayName("#146r 知识问答承流：帧序单源复验 + 宁缺毋造")
class KnowledgeRetrievalSseFramesTest {

    private static final String TRACE = "trace-kb-7";

    private final List<String> events = new ArrayList<>();
    private final List<Map<String, Object>> payloads = new ArrayList<>();

    private KnowledgeRetrievalSseFrames frames() {
        return new KnowledgeRetrievalSseFrames(TRACE, (event, data) -> {
            events.add(event);
            payloads.add(data);
        });
    }

    @Test
    @DisplayName("happy：meta → delta → citation → done，整条序列过 SseFrameContract 校验且每帧带 traceId")
    void happySequencePassesSingleSourceValidator() throws IOException {
        KnowledgeRetrievalSseFrames frames = frames();
        frames.meta(2);
        frames.delta("按日计息，月末结算");
        assertTrue(frames.citation(1, "art-1", "chunk-1", 0.87));
        frames.done(KnowledgeRetrievalSseFrames.STATUS_ANSWERED, 2);

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DELTA,
                SseFrameContract.FRAME_CITATION, SseFrameContract.FRAME_DONE), events);
        assertEquals(events, frames.emittedNames());
        assertEquals(Optional.empty(), SseFrameContract.validateSequence(frames.emittedNames()),
                "写出顺序必须自证合法（词表与帧序都取 common-api 单源）");

        for (Map<String, Object> payload : payloads) {
            assertEquals(TRACE, payload.get(SseFrameContract.KEY_TRACE_ID), "每帧都必须带 traceId");
        }
        assertEquals(2, payloads.get(0).get(KnowledgeRetrievalSseFrames.KEY_HITS));
        assertEquals("按日计息，月末结算", payloads.get(1).get(SseFrameContract.KEY_TEXT));
        assertEquals(1, payloads.get(2).get(KnowledgeRetrievalSseFrames.KEY_INDEX));
        assertEquals("art-1", payloads.get(2).get(KnowledgeRetrievalSseFrames.KEY_SOURCE_ID));
        assertEquals("chunk-1", payloads.get(2).get(KnowledgeRetrievalSseFrames.KEY_CHUNK_ID));
        assertEquals(KnowledgeRetrievalSseFrames.STATUS_ANSWERED,
                payloads.get(3).get(SseFrameContract.KEY_STATUS));
        assertEquals(2, payloads.get(3).get(KnowledgeRetrievalSseFrames.KEY_HITS));
    }

    @Test
    @DisplayName("无据不答：只发 meta + done(NO_EVIDENCE)，不发空 delta 冒充\"模型答了\"")
    void noEvidenceEmitsMetaAndDoneOnly() throws IOException {
        KnowledgeRetrievalSseFrames frames = frames();
        frames.meta(0);
        frames.done(KnowledgeRetrievalSseFrames.STATUS_NO_EVIDENCE, 0);

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DONE), events);
        assertEquals(Optional.empty(), SseFrameContract.validateSequence(frames.emittedNames()));
        assertEquals(KnowledgeRetrievalSseFrames.STATUS_NO_EVIDENCE,
                payloads.get(1).get(SseFrameContract.KEY_STATUS));
        assertEquals(0, payloads.get(1).get(KnowledgeRetrievalSseFrames.KEY_HITS));
    }

    @Test
    @DisplayName("空正文不发帧（null 与空串都跳过）")
    void blankDeltaIsSkipped() throws IOException {
        KnowledgeRetrievalSseFrames frames = frames();
        frames.meta(0);
        frames.delta(null);
        frames.delta("");
        frames.done(KnowledgeRetrievalSseFrames.STATUS_NO_EVIDENCE, 0);

        assertEquals(List.of(SseFrameContract.FRAME_META, SseFrameContract.FRAME_DONE), events);
    }

    @Test
    @DisplayName("引用要素缺失（sourceId 或 chunkId 为空）⇒ 返回 false 且零帧")
    void citationRequiresBothIdentifiers() throws IOException {
        KnowledgeRetrievalSseFrames frames = frames();

        assertFalse(frames.citation(1, null, "chunk-1", 0.9));
        assertFalse(frames.citation(1, "art-1", "  ", 0.9));
        assertTrue(events.isEmpty(), "宁缺毋造：要素不齐不得发引用帧");

        frames.meta(1);
        assertTrue(frames.citation(1, "art-1", "chunk-1", null));
        frames.done(KnowledgeRetrievalSseFrames.STATUS_ANSWERED, 1);
        assertFalse(payloads.get(1).containsKey(KnowledgeRetrievalSseFrames.KEY_SCORE),
                "score 为 null 时不落键（禁补默认值冒充读数）");
    }

    @Test
    @DisplayName("缺 meta 直接 done ⇒ IllegalArgumentException（帧序违例 fail-loud，不许 200 + 错形流）")
    void doneWithoutMetaFailsLoud() {
        KnowledgeRetrievalSseFrames frames = frames();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> frames.done(KnowledgeRetrievalSseFrames.STATUS_ANSWERED, 0));
        assertTrue(e.getMessage().contains("帧序违例"), "违例原因须点名帧序：" + e.getMessage());
        assertTrue(events.isEmpty(), "校验失败不得先写出末帧");
    }

    @Test
    @DisplayName("done 终态为空 ⇒ IllegalArgumentException（禁空终态冒充完成）")
    void doneWithoutStatusFailsLoud() {
        KnowledgeRetrievalSseFrames frames = frames();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> frames.done(" ", 0));
        assertTrue(e.getMessage().contains("终态"));
    }

    @Test
    @DisplayName("sink 为空 ⇒ 构造即拒（承流写出面不得空装）")
    void nullSinkIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeRetrievalSseFrames(TRACE, null));
    }
}
