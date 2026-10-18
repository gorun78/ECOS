package com.chinacreator.gzcm.engine.ai.oag.dto;

import com.chinacreator.gzcm.common.stream.OagSseFrameContract;
import com.chinacreator.gzcm.common.stream.SseFrameContract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAG SSE 步骤事件 DTO — 强类型版本。
 *
 * <p>替代旧版 SSE 推送中直接使用 {@code Map<String, Object>} 的事件 payload，
 * 通过 {@link #toMap()} 序列化后保持与旧前端兼容的嵌套 JSON 结构。
 * 事件名保持 {@code node / response / done / error / blocked} 不变。</p>
 *
 * <p><b>#143/B5-5a 收口 D-B5-7 的两处改动</b>：①键名不再由本类自抄字面量，一律取
 * {@link OagSseFrameContract} 单源；②{@code traceId} 恒出键（缺失写
 * {@link SseFrameContract#textOrAbsent(String)} 的标记），禁"为 null 就整键缺席"
 * ——前端错误态要把诊断码显示出来，缺席与"未上报"是两种形状。</p>
 *
 * <p>本类是 OAG 通道<b>唯一</b>的载荷装配面：写出面（{@code stream/OagSseFrames}）
 * 只决定事件名与帧序，不再另拼一份 Map，否则键面又会分裂。</p>
 */
public class OagStepEvent {

    /** 本事件将要以哪个 SSE 事件名发出（词表单源 {@link OagSseFrameContract}）。 */
    private String eventName;

    /** 当前节点名称（如 ReasoningEngine） */
    private String node;

    /** 节点状态：RUNNING / DONE */
    private String status;

    /** 节点耗时（ms） */
    private long nodeElapsedMs;

    /** 追踪 ID */
    private String traceId;

    /** 意图分类结果 */
    private String intent;

    /** 安全检查是否通过 */
    private boolean securityPassed;

    /** 错误信息（错误时） */
    private String message;

    /** 拦截原因（blocked 事件；前端 onBlocked 读此键） */
    private String reason;

    /** 最终正文（response 事件） */
    private String content;

    // ── 工厂方法 ──────────────────────────────────────────

    /**
     * 构建节点完成事件（事件名 {@code node}）。
     *
     * @param node          节点名称
     * @param status        节点状态（DONE）
     * @param nodeElapsedMs 节点耗时
     * @param traceId       追踪 ID
     * @param intent        意图
     * @param securityPassed 安全通过标志
     * @return 节点完成事件
     */
    public static OagStepEvent nodeDone(String node, String status, long nodeElapsedMs,
                                        String traceId, String intent, boolean securityPassed) {
        OagStepEvent e = new OagStepEvent();
        e.eventName = OagSseFrameContract.EVENT_NODE;
        e.node = node;
        e.status = status;
        e.nodeElapsedMs = nodeElapsedMs;
        e.traceId = traceId;
        e.intent = intent;
        e.securityPassed = securityPassed;
        return e;
    }

    /**
     * 构建错误事件（事件名 {@code error}，永不携带异常原文：入参已是模板化消息）。
     *
     * @param node          节点名称
     * @param status        节点状态（FAILED）
     * @param nodeElapsedMs 节点耗时
     * @param traceId       追踪 ID
     * @param message       错误信息
     * @return 错误事件
     */
    public static OagStepEvent error(String node, String status, long nodeElapsedMs,
                                     String traceId, String message) {
        OagStepEvent e = new OagStepEvent();
        e.eventName = OagSseFrameContract.EVENT_ERROR;
        e.node = node;
        e.status = status;
        e.nodeElapsedMs = nodeElapsedMs;
        e.traceId = traceId;
        e.message = message;
        return e;
    }

    /**
     * 构建末帧事件（事件名 {@code done}）。
     *
     * @param status         管道终态（COMPLETED / BLOCKED / FAILED）
     * @param nodeElapsedMs  总耗时
     * @param traceId        追踪 ID
     * @param intent         意图
     * @param securityPassed 安全通过标志
     * @return 末帧事件
     */
    public static OagStepEvent done(String status, long nodeElapsedMs, String traceId,
                                   String intent, boolean securityPassed) {
        OagStepEvent e = nodeDone(null, status, nodeElapsedMs, traceId, intent, securityPassed);
        e.eventName = OagSseFrameContract.EVENT_DONE;
        return e;
    }

    /**
     * 构建拦截事件（事件名 {@code blocked}）。
     *
     * <p>{@code reason} 同时写入 {@code reason} 与 {@code message} 两键：前者是前端
     * {@code onBlocked} 的读取位（D-B5-13 的病灶即两键不同形），后者保留给既有纯文本
     * 兜底腿，禁因新增键而断供。</p>
     */
    public static OagStepEvent blocked(String reason, String traceId, String intent,
                                       boolean securityPassed) {
        OagStepEvent e = nodeDone(null, "BLOCKED", 0L, traceId, intent, securityPassed);
        e.eventName = OagSseFrameContract.EVENT_BLOCKED;
        e.reason = reason;
        e.message = reason;
        return e;
    }

    /** 构建正文事件（事件名 {@code response}）。 */
    public static OagStepEvent response(String content, String traceId) {
        OagStepEvent e = new OagStepEvent();
        e.eventName = OagSseFrameContract.EVENT_RESPONSE;
        e.content = content;
        e.traceId = traceId;
        return e;
    }

    // ── 序列化 ────────────────────────────────────────────

    /**
     * 序列化为 SSE 事件 payload（键名单源 {@link OagSseFrameContract}）。
     *
     * <p>{@code traceId} 恒在首位；业务键为 null 时缺席（与旧前端兼容：旧形态即
     * {@code if != null put}），但 {@code nodeElapsedMs}/{@code securityPassed} 两个
     * 原始类型键恒出，保持改前形状。</p>
     *
     * @return 可 JSON 序列化的不可变 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (node != null) {
            fields.put(OagSseFrameContract.KEY_NODE, node);
        }
        if (status != null) {
            fields.put(OagSseFrameContract.KEY_STATUS, status);
        }
        fields.put(OagSseFrameContract.KEY_NODE_ELAPSED_MS, nodeElapsedMs);
        if (intent != null) {
            fields.put(OagSseFrameContract.KEY_INTENT, intent);
        }
        fields.put(OagSseFrameContract.KEY_SECURITY_PASSED, securityPassed);
        if (message != null) {
            fields.put(OagSseFrameContract.KEY_MESSAGE, message);
        }
        if (reason != null) {
            fields.put(OagSseFrameContract.KEY_REASON, reason);
        }
        if (content != null) {
            fields.put(OagSseFrameContract.KEY_CONTENT, content);
        }
        return OagSseFrameContract.payload(eventName, traceId, fields);
    }

    /** 本事件将要以哪个 SSE 事件名发出。 */
    public String eventName() {
        return eventName;
    }

    /** 供写出面绑定通道 traceId（工厂方法已设置事件名，本方法只补追踪 ID）。 */
    public void applyChannelTraceId(String channelTraceId) {
        if (this.traceId == null || this.traceId.isBlank()) {
            this.traceId = channelTraceId;
        }
    }

    // ── getter / setter ──────────────────────────────────

    public String getNode() { return node; }
    public void setNode(String node) { this.node = node; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getNodeElapsedMs() { return nodeElapsedMs; }
    public void setNodeElapsedMs(long nodeElapsedMs) { this.nodeElapsedMs = nodeElapsedMs; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public boolean isSecurityPassed() { return securityPassed; }
    public void setSecurityPassed(boolean securityPassed) { this.securityPassed = securityPassed; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
