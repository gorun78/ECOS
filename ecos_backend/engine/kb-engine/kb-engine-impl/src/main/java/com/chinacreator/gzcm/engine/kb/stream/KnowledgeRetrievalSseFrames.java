package com.chinacreator.gzcm.engine.kb.stream;

import com.chinacreator.gzcm.common.stream.SseFrameContract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识问答承流帧写出器（附录 A 第 14 行 {@code generateKnowledgeAnswer}：事件
 * {@code delta / citations / done}，末帧带 traceId）。
 *
 * <p>帧名与帧序全部落在 common-api 单源 {@link SseFrameContract}：首帧 {@code meta}、
 * 末帧 {@code done}、{@code error} 至多一条且紧邻 {@code done} 之前。{@link #done} 写出前
 * 复验整条序列，违例即抛 {@link IllegalArgumentException}（编程缺陷，fail-loud），
 * 不许以"200 + 错形流"收场 —— 与 ai 域 {@code AgentLoopSseFrames} 同一纪律。</p>
 *
 * <p><b>与设计词面的两处显式差异（均已入台账）</b>：</p>
 * <ol>
 *   <li>契约写 {@code citations}，而帧词表单源只有单数 {@code citation}（且该帧的引用四要素
 *       是 AI 域的 {@code forecastRunId/caliberId/version/asOfTime}）。本写出器按单源发
 *       {@code citation} 帧，载荷改带 kb 侧真实可得的 {@code index/sourceId/chunkId/score}；
 *       禁自造 {@code citations} 帧名绕开单源校验。</li>
 *   <li>本端点<b>不是 token 级流</b>：llm-gateway 无流式通道，答案整段生成后以单条
 *       {@code delta} 帧下发。该形态如实登记，不做"逐字切片"的伪流。</li>
 * </ol>
 *
 * <p><b>为何没有 error 帧写出方法</b>：鉴权（403/503）、取数（502）、LLM（503）三类故障
 * 全部在请求线程、{@code SseEmitter} 创建之前完成，落成真实 HTTP 状态行（由
 * {@code KbErrorAdvice} 渲染）；流一旦开始，剩下的失败面只有客户端断连（走
 * {@code completeWithError}，没有收件人可发帧）。发一条客户端已无人接收的 error 帧
 * 只会把通道故障伪装成已完成的事件序列。</p>
 */
public final class KnowledgeRetrievalSseFrames {

    /** 出帧汇（生产形态由控制器桥给 {@code SseEmitter}，测试形态为记录器）。 */
    @FunctionalInterface
    public interface SseFrameSink {
        void send(String event, Map<String, Object> data) throws IOException;
    }

    /** 命中条数键（meta/done 同名字段）。 */
    public static final String KEY_HITS = "hits";
    /** 引用帧：来源文章 id。 */
    public static final String KEY_SOURCE_ID = "sourceId";
    /** 引用帧：向量块 id。 */
    public static final String KEY_CHUNK_ID = "chunkId";
    /** 引用帧：相似度读数。 */
    public static final String KEY_SCORE = "score";
    /** 引用帧：命中序号（1 起，与 {@code hits[]} 顺序对齐）。 */
    public static final String KEY_INDEX = "index";

    /** 末帧终态：答案已生成。 */
    public static final String STATUS_ANSWERED = "ANSWERED";
    /** 末帧终态：零命中（无据不答，未调 LLM）。 */
    public static final String STATUS_NO_EVIDENCE = "NO_EVIDENCE";

    private final String traceId;
    private final SseFrameSink sink;
    private final List<String> emitted = new ArrayList<>();

    public KnowledgeRetrievalSseFrames(String traceId, SseFrameSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("SseFrameSink 必填（承流写出面不得为空装）");
        }
        this.traceId = traceId;
        this.sink = sink;
    }

    /** 已写出的帧名序列（只读；帧序测试用）。 */
    public List<String> emittedNames() {
        return List.copyOf(emitted);
    }

    /** 本通道 traceId（请求线程捕获值；帧载荷与故障分类必须同一条）。 */
    public String traceId() {
        return traceId;
    }

    /** 帧 1：首帧必发（通道上下文＝traceId；本通道无 model/contextLimit 载体，不放假值）。 */
    public void meta(int hits) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(KEY_HITS, hits);
        write(SseFrameContract.FRAME_META, fields);
    }

    /** 帧 4：正文（整段一帧，见类注释；空正文不发帧）。 */
    public void delta(String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_TEXT, text);
        write(SseFrameContract.FRAME_DELTA, fields);
    }

    /**
     * 帧 5：引用锚。
     *
     * @return 是否真的发出；{@code sourceId}/{@code chunkId} 任一为空 ⇒ 不发帧（宁缺毋造）
     */
    public boolean citation(int index, String sourceId, String chunkId, Object score) throws IOException {
        if (sourceId == null || sourceId.isBlank() || chunkId == null || chunkId.isBlank()) {
            return false;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(KEY_INDEX, index);
        fields.put(KEY_SOURCE_ID, sourceId);
        fields.put(KEY_CHUNK_ID, chunkId);
        if (score != null) {
            fields.put(KEY_SCORE, score);
        }
        write(SseFrameContract.FRAME_CITATION, fields);
        return true;
    }

    /**
     * 帧 7：末帧。写出前复验整条帧序；违例 ⇒ {@link IllegalArgumentException}。
     *
     * @param status {@link #STATUS_ANSWERED} / {@link #STATUS_NO_EVIDENCE}
     */
    public void done(String status, int hits) throws IOException {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("done 帧必须带终态（禁空终态冒充完成）");
        }
        List<String> candidate = new ArrayList<>(emitted);
        candidate.add(SseFrameContract.FRAME_DONE);
        Optional<String> violation = SseFrameContract.validateSequence(candidate);
        if (violation.isPresent()) {
            throw new IllegalArgumentException("SSE 帧序违例: " + violation.get());
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_STATUS, status);
        fields.put(KEY_HITS, hits);
        write(SseFrameContract.FRAME_DONE, fields);
    }

    private void write(String event, Map<String, ?> fields) throws IOException {
        Map<String, Object> data = SseFrameContract.frame(event, traceId, fields);
        sink.send(event, data);
        emitted.add(event);
    }
}
