package com.chinacreator.gzcm.engine.ai.stream;

import com.chinacreator.gzcm.common.stream.OagSseFrameContract;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.engine.ai.oag.dto.OagStepEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OAG 通道承流写出器（#143/B5-5a 收口 D-B5-7）。
 *
 * <p>存在理由与 {@link AgentLoopSseFrames} 同源：事件名一旦散在发射点，前端解析器、
 * 审计对账与网关透传就读到不同词表，且这种漂移不会让任何一条测试变红（大家都发得出帧）。
 * 本类把 OAG 通道的出帧收成一处：事件名取 {@link OagSseFrameContract} 单源、
 * 载荷装配唯一在 {@link OagStepEvent#toMap()}、末帧前复验帧序。</p>
 *
 * <p>与 agent-loop 写出器的两点差异（照登，不伪装同形）：①OAG 无 {@code meta} 通道
 * 上下文帧，通道 traceId 由管道上下文产生后才可得，故本类以 {@link #bindTraceId(String)}
 * <b>晚绑定</b>并禁止帧间漂移；②OAG 的终态判定在 {@code done} 帧的 {@code status} 键上，
 * 不是靠帧名区分成功/失败。</p>
 *
 * <p><b>末帧复验无豁免腿</b>：一旦为"已发过 error"放开复验，故障流就不再受任何帧序约束，
 * 而故障流恰恰是最需要可判读形状的那条。实测四条真实流（happy / blocked / FAILED /
 * 参数校验失败）都天然满足 {@code error} 紧邻 {@code done}，故违例一律抛。</p>
 */
public final class OagSseFrames {

    private final AgentLoopSseFrames.SseFrameSink sink;
    private final List<String> emitted = new ArrayList<>();
    private String traceId;

    public OagSseFrames(AgentLoopSseFrames.SseFrameSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("SseFrameSink 必填（OAG 承流写出面不得为空装）");
        }
        this.sink = sink;
    }

    /** 已写出的事件名序列（只读；帧序测试与断线排障用）。 */
    public List<String> emittedNames() {
        return List.copyOf(emitted);
    }

    /** 通道 traceId：未绑定即单源缺失标记，禁返回 null（前端诊断码位要出键）。 */
    public String traceId() {
        return traceId == null ? SseFrameContract.textOrAbsent(null) : traceId;
    }

    /**
     * 晚绑定通道 traceId：首次非空白即固定，之后异值即抛。
     *
     * <p>为什么禁漂移：OAG 的 node 事件各自带 Map 里的 traceId，若某节点回传另一条
     * 追踪 ID，前端就会把一次运行显示成两个诊断码，事后无法与审计对账。</p>
     */
    public void bindTraceId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        if (traceId == null) {
            traceId = candidate;
            return;
        }
        if (!traceId.equals(candidate)) {
            throw new IllegalStateException("OAG 通道 traceId 漂移: 已绑定 " + traceId + "，又见 " + candidate);
        }
    }

    /**
     * 写出一条非末帧事件（{@code node / response / blocked / error}）。
     *
     * <p>{@code done} 必须走 {@link #done(OagStepEvent)}——末帧前要复验帧序，
     * 走本方法即绕过校验，属发射侧编程缺陷。</p>
     */
    public void send(OagStepEvent event) throws IOException {
        String name = event.eventName();
        if (OagSseFrameContract.isTerminal(name)) {
            throw new IllegalArgumentException("done 事件必须经 OagSseFrames.done() 写出（末帧前需复验帧序）");
        }
        bindTraceId(event.getTraceId());
        event.applyChannelTraceId(traceId());
        write(name, event.toMap());
    }

    /** 写出末帧：先复验整条事件序，违例即抛（抛在写帧之前，不落半截流）。 */
    public void done(OagStepEvent event) throws IOException {
        if (!OagSseFrameContract.EVENT_DONE.equals(event.eventName())) {
            throw new IllegalArgumentException("末帧事件名必须为 " + OagSseFrameContract.EVENT_DONE);
        }
        bindTraceId(event.getTraceId());
        event.applyChannelTraceId(traceId());
        List<String> candidate = new ArrayList<>(emitted);
        candidate.add(OagSseFrameContract.EVENT_DONE);
        Optional<String> violation = OagSseFrameContract.validateSequence(candidate);
        if (violation.isPresent()) {
            throw new IllegalStateException("OAG 事件序违例: " + violation.get());
        }
        write(OagSseFrameContract.EVENT_DONE, event.toMap());
    }

    private void write(String event, Map<String, Object> data) throws IOException {
        sink.send(event, data);
        emitted.add(event);
    }
}
