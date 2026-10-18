package com.chinacreator.gzcm.engine.ai.wagent.stream;

import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.engine.ai.stream.AgentLoopSseFrames;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WAgent run 流承帧写出器（详设-10 §10.3.6 八帧；#143/B5-5b 收口 D-B5-8）。
 *
 * <p>与 {@code ai.stream.AgentLoopSseFrames}／{@code ai.stream.OagSseFrames} 同源的存在理由：帧名与
 * 键面一旦散在发射点，前端解析器、审计对账与网关透传就读到不同词表，且没有任何一条测试会因此变红。
 * 本类把 W 通道的出帧收成一处：帧名与键面全部经 {@link SseFrameContract} 的 W 分表装配，
 * 末帧前复验帧序。包位取 {@code ..engine.ai.wagent.stream}（而非另两通道所在的 {@code ai.stream}）
 * 是因为 F10-01 术语门 {@code TerminologyArchTest} 要求 {@code WAgent*} 生产类只能住在 wagent 包下。</p>
 *
 * <p><b>三条与另两条通道不同的口径（照登，不伪装同形）</b>：</p>
 * <ol>
 *   <li>traceId 在<b>请求线程</b>捕获后固定（本流泵跑在独立线程池，MDC 是线程局部的，
 *       泵内重取会生成第二条追踪 ID ⇒ 一屏两个诊断码，无法与审计对账）；</li>
 *   <li>末帧复验<b>无豁免腿</b>：{@code error → done} 本就是合法尾序，没有任何一条 W 流
 *       需要"跳过校验才能收尾"，故违例一律抛（抛在写帧之前，不落半截流）；</li>
 *   <li>本类只装配、不判定：DG 码白名单、终态、错误码分类都由调用侧给，
 *       承流面因此不是第二真值面。</li>
 * </ol>
 */
public final class WAgentSseFrames {

    private final String traceId;
    private final AgentLoopSseFrames.SseFrameSink sink;
    private final List<String> emitted = new ArrayList<>();

    public WAgentSseFrames(String traceId, AgentLoopSseFrames.SseFrameSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("SseFrameSink 必填（W 通道承流写出面不得为空装）");
        }
        this.traceId = traceId;
        this.sink = sink;
    }

    /** 已写出的帧名序列（只读；帧序测试与断线排障用）。 */
    public List<String> emittedNames() {
        return List.copyOf(emitted);
    }

    /** 本通道 traceId（请求线程捕获值；缺失即 {@code -}，禁在帧间重取 MDC）。 */
    public String traceId() {
        return traceId;
    }

    /** 帧 1：首帧必发。{@code streamMode}/{@code payloadKind} 是 F10-16 与 §6.3 的诚实标注位。 */
    public void meta(String runId, String planSource, Object budget, String streamMode, String payloadKind)
            throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_RUN_ID, SseFrameContract.textOrAbsent(runId));
        fields.put(SseFrameContract.KEY_PLAN_SOURCE, SseFrameContract.textOrAbsent(planSource));
        fields.put(SseFrameContract.KEY_BUDGET, budget);
        fields.put(SseFrameContract.KEY_STREAM_MODE, SseFrameContract.textOrAbsent(streamMode));
        fields.put(SseFrameContract.KEY_PAYLOAD_KIND, SseFrameContract.textOrAbsent(payloadKind));
        write(SseFrameContract.FRAME_META, fields);
    }

    /** 帧 6：降级帧（前端降级横幅单源读此帧）；载荷四键由单源 {@code degradeFields} 装配。 */
    public void degrade(String dgCode, String appliesTo, boolean requiresAck, String note)
            throws IOException {
        write(SseFrameContract.FRAME_DEGRADE,
                SseFrameContract.degradeFields(dgCode, appliesTo, requiresAck, note));
    }

    /**
     * 帧 2：步骤帧。六键全部来自 V234 行（{@code step_key/step_type/tool_name/attempt/status/
     * policy_effect}）；不可得的键由 {@link SseFrameContract#wagentFrame} 的"null 即不落键"处理，
     * 禁在此补默认值冒充观测值。
     */
    public void step(String eventId, String stepKey, String stepType, String tool,
                     Integer attempt, String status, String policyEffect) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_EVENT_ID, eventId);
        fields.put(SseFrameContract.KEY_STEP_KEY, stepKey);
        fields.put(SseFrameContract.KEY_STEP_TYPE, stepType);
        fields.put(SseFrameContract.KEY_TOOL, tool);
        fields.put(SseFrameContract.KEY_ATTEMPT, attempt);
        fields.put(SseFrameContract.KEY_STATUS, status);
        fields.put(SseFrameContract.KEY_POLICY_EFFECT, policyEffect);
        write(SseFrameContract.FRAME_STEP, fields);
    }

    /**
     * 补充帧：Run 生命周期里程碑（V235 里八帧无对应帧位的那 8 类事件）。
     *
     * <p>载荷只有事件身份三件 + 载荷形态标注，<b>不带 payload 正文</b>：V235 的
     * {@code payload_ref_text} 实测是编排器内联写入的 JSON（含调用方提交的
     * {@code RunInputReceived} Map），承流面对它没有"哪些键可外泄"的裁决依据，
     * 因此按 §6.3 引用型口径一律不出正文。</p>
     */
    public void milestone(String eventId, String eventType, String occurTime, String payloadKind)
            throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_EVENT_ID, eventId);
        fields.put(SseFrameContract.KEY_EVENT_TYPE, eventType);
        fields.put(SseFrameContract.KEY_OCCUR_TIME, occurTime);
        fields.put(SseFrameContract.KEY_PAYLOAD_KIND, payloadKind);
        write(SseFrameContract.FRAME_MILESTONE, fields);
    }

    /**
     * 帧 7：故障帧。{@code message} 只能是已渲染的模板（含诊断码），异常原文无入口。
     *
     * <p>{@code retryable} 用包装类型而非 {@code boolean}：错误码未在 W 域错误码单源注册时
     * 无权威可重试性可给，此时该键<b>不落帧</b>（缺位），禁以 {@code false} 冒充"已判定不可重试"。
     * {@code errorCode} 同理：拿不到真实码时只出 message，码位为 {@code -}。</p>
     */
    public void error(String errorCode, String message, Boolean retryable) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_ERROR_CODE, SseFrameContract.textOrAbsent(errorCode));
        fields.put(SseFrameContract.KEY_MESSAGE, SseFrameContract.textOrAbsent(message));
        fields.put(SseFrameContract.KEY_RETRYABLE, retryable);
        write(SseFrameContract.FRAME_ERROR, fields);
    }

    /** 帧 8：末帧必发。写出前复验整条帧序，违例即抛（无豁免腿）。 */
    public void done(String runId, String status, String grade, List<String> degradations)
            throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(SseFrameContract.KEY_RUN_ID, SseFrameContract.textOrAbsent(runId));
        fields.put(SseFrameContract.KEY_STATUS, SseFrameContract.textOrAbsent(status));
        fields.put(SseFrameContract.KEY_GRADE, grade);
        fields.put(SseFrameContract.KEY_DEGRADATIONS, degradations == null ? List.of() : degradations);
        List<String> candidate = new ArrayList<>(emitted);
        candidate.add(SseFrameContract.FRAME_DONE);
        Optional<String> violation = SseFrameContract.validateWagentSequence(candidate);
        if (violation.isPresent()) {
            throw new IllegalStateException("WAgent 帧序违例: " + violation.get());
        }
        write(SseFrameContract.FRAME_DONE, fields);
    }

    private void write(String event, Map<String, ?> fields) throws IOException {
        sink.send(event, SseFrameContract.wagentFrame(event, traceId, fields));
        emitted.add(event);
    }
}
