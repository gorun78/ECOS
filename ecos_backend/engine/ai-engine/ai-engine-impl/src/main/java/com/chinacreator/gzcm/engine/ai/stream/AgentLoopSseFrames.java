package com.chinacreator.gzcm.engine.ai.stream;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.tool.ToolCitation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent Loop 承流帧写出器（详细设计-06 §9.3.3 七帧；#143/B5-2）。
 *
 * <p>存在理由：帧的<b>顺序</b>与<b>形状</b>只能有一个执行处。控制器若逐处
 * {@code emitter.send(event().name("..."))} 自拼帧名，词表就散在发射点里，
 * 前端按序假设的解析器无法对齐（本批要收的正是这个形态）。本类把写出面收成
 * 一个有序状态机：{@link #done} 之前用 {@link SseFrameContract#validateSequence}
 * 复验，违例即抛，让通道以失败形态可见，而不是"200 + 错形流"。</p>
 *
 * <p>依赖方向：只写帧、不判定。引用是否成立由上游 F06-12 后校验裁决，本类只在
 * 拿到<b>已确定性渲染</b>的引用块时发帧；拿不到就不发（禁在此补默认值伪造引用）。</p>
 */
public final class AgentLoopSseFrames {

    /** 出帧汇（生产形态由控制器桥给 {@code SseEmitter}，测试形态为记录器）。 */
    @FunctionalInterface
    public interface SseFrameSink {
        void send(String event, Map<String, Object> data) throws IOException;
    }

    private final String traceId;
    private final SseFrameSink sink;
    private final List<String> emitted = new ArrayList<>();

    /** 故障尾已写出：此时 {@link #done} 不再复验抛错，避免兜底腿二次异常。 */
    private boolean failureTail;

    public AgentLoopSseFrames(String traceId, SseFrameSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("SseFrameSink 必填（承流写出面不得为空装）");
        }
        this.traceId = traceId;
        this.sink = sink;
    }

    /** 已写出的帧名序列（只读；帧序测试与断线续传排障用）。 */
    public List<String> emittedNames() {
        return List.copyOf(emitted);
    }

    /** 本通道 traceId（请求线程捕获值；帧载荷与故障分类必须同一条，禁各自重取 MDC）。 */
    public String traceId() {
        return traceId;
    }

    /** 帧 1：首帧必发。数值缺失写 {@code -1}，字符串缺失写 {@code "-"}。 */
    public void meta(String sessionId, String model, Integer contextLimit, Integer estimatedTokens)
            throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_SESSION_ID, SseFrameContract.textOrAbsent(sessionId));
        fields.put(SseFrameContract.KEY_MODEL, SseFrameContract.textOrAbsent(model));
        fields.put(SseFrameContract.KEY_CONTEXT_LIMIT,
                contextLimit == null ? SseFrameContract.unknownToken() : contextLimit.longValue());
        fields.put(SseFrameContract.KEY_ESTIMATED_TOKENS,
                estimatedTokens == null ? SseFrameContract.unknownToken() : estimatedTokens.longValue());
        write(SseFrameContract.FRAME_META, fields);
    }

    /**
     * 帧 2：步骤帧。{@code op} 取 common-api 的 {@link AgentLoopTrace.Op} 单源，
     * {@code decision} 取上游<b>已映射</b>的裁决态字面量（本类不再二次映射，
     * 映射单源在 {@code AgentLoopService#decisionOf}）。
     */
    public void step(AgentLoopTrace.Op op, int step, String tool, String model, String decision)
            throws IOException {
        if (op == null) {
            throw new IllegalArgumentException("step 帧 op 必填（枚举单源=AgentLoopTrace.Op）");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_OP, op.wire());
        fields.put(SseFrameContract.KEY_STEP, step);
        if (tool != null && !tool.isBlank()) {
            fields.put(SseFrameContract.KEY_TOOL, tool);
        }
        if (model != null && !model.isBlank()) {
            fields.put(SseFrameContract.KEY_MODEL, model);
        }
        fields.put(SseFrameContract.KEY_DECISION,
                decision == null || decision.isBlank() ? AgentLoopTrace.Decision.NA.name() : decision);
        write(SseFrameContract.FRAME_STEP, fields);
    }

    /** 帧 3：用量帧（不是正文帧）。分项不可得即 {@code -1}，禁 0 冒充。 */
    public void tokenUsage(long promptTokens, long completionTokens, boolean estimated) throws IOException {
        write(SseFrameContract.FRAME_TOKEN,
                SseFrameContract.usageFrame(traceId, promptTokens, completionTokens, estimated));
    }

    /** 帧 4：模型增量正文。 */
    public void delta(String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_TEXT, text);
        write(SseFrameContract.FRAME_DELTA, fields);
    }

    /**
     * 帧 5：引用帧。
     *
     * @param citationBlock 上游由工具返回体确定性渲染的引用块（模板单源 {@link ToolCitation}）
     * @return 是否真的发出了一帧；引用四要素不齐 ⇒ 不发帧并返回 {@code false}
     */
    public boolean citation(String citationBlock) throws IOException {
        List<ToolCitation> blocks = ToolCitation.parseBlocks(citationBlock);
        if (blocks.isEmpty()) {
            return false;
        }
        ToolCitation first = blocks.get(0);
        Map<String, Object> citationFields = new LinkedHashMap<>();
        citationFields.put(SseFrameContract.KEY_FORECAST_RUN_ID, first.forecastRunId());
        citationFields.put(SseFrameContract.KEY_CALIBER_ID, first.caliberId());
        citationFields.put(SseFrameContract.KEY_VERSION, first.version());
        citationFields.put(SseFrameContract.KEY_AS_OF_TIME, first.asOfTime());
        citationFields.put(SseFrameContract.KEY_LINKS, first.evidenceLinks());
        Optional<Map<String, Object>> frame = SseFrameContract.citationFrame(traceId, citationFields);
        if (frame.isEmpty()) {
            return false;
        }
        write(SseFrameContract.FRAME_CITATION, frame.get());
        return true;
    }

    /** 帧 6：故障帧，载荷只取 {@link AiError#toSseErrorFrame()} 的四键，异常原文无入口。 */
    public void error(AiError error) throws IOException {
        failureTail = true;
        write(SseFrameContract.FRAME_ERROR, SseFrameContract.errorFrame(traceId, error.toSseErrorFrame()));
    }

    /** 帧 7：末帧。写出前复验整条帧序；违例且非故障尾 ⇒ 抛错让通道失败可见。 */
    public void done(Map<String, ?> fields) throws IOException {
        List<String> candidate = new ArrayList<>(emitted);
        candidate.add(SseFrameContract.FRAME_DONE);
        Optional<String> violation = SseFrameContract.validateSequence(candidate);
        if (violation.isPresent() && !failureTail) {
            throw new IllegalStateException("SSE 帧序违例: " + violation.get());
        }
        write(SseFrameContract.FRAME_DONE, fields);
    }

    private void write(String event, Map<String, ?> fields) throws IOException {
        Map<String, Object> data = SseFrameContract.frame(event, traceId, fields);
        sink.send(event, data);
        emitted.add(event);
    }
}
