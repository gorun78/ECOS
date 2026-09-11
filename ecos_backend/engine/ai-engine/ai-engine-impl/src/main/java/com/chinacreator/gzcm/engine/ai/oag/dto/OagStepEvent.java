package com.chinacreator.gzcm.engine.ai.oag.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAG SSE 步骤事件 DTO — 强类型版本。
 *
 * <p>替代旧版 SSE 推送中直接使用 {@code Map<String, Object>} 的事件 payload，
 * 通过 {@link #toMap()} 序列化后保持与旧前端兼容的嵌套 JSON 结构。
 * 事件名保持 {@code node / response / done / error / blocked} 不变。</p>
 */
public class OagStepEvent {

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

    // ── 工厂方法 ──────────────────────────────────────────

    /**
     * 构建节点完成事件。
     *
     * @param node        节点名称
     * @param status      节点状态（DONE）
     * @param nodeElapsedMs 节点耗时
     * @param traceId     追踪 ID
     * @param intent      意图
     * @param securityPassed 安全通过标志
     * @return 节点完成事件
     */
    public static OagStepEvent nodeDone(String node, String status, long nodeElapsedMs,
                                        String traceId, String intent, boolean securityPassed) {
        OagStepEvent e = new OagStepEvent();
        e.node = node;
        e.status = status;
        e.nodeElapsedMs = nodeElapsedMs;
        e.traceId = traceId;
        e.intent = intent;
        e.securityPassed = securityPassed;
        return e;
    }

    /**
     * 构建错误事件。
     *
     * @param node        节点名称
     * @param status      节点状态（FAILED）
     * @param nodeElapsedMs 节点耗时
     * @param traceId     追踪 ID
     * @param message     错误信息
     * @return 错误事件
     */
    public static OagStepEvent error(String node, String status, long nodeElapsedMs,
                                     String traceId, String message) {
        OagStepEvent e = new OagStepEvent();
        e.node = node;
        e.status = status;
        e.nodeElapsedMs = nodeElapsedMs;
        e.traceId = traceId;
        e.message = message;
        return e;
    }

    // ── 序列化 ────────────────────────────────────────────

    /**
     * 序列化为 Map（用于 SSE 事件 JSON payload），仅包含非 null 字段。
     *
     * @return 可 JSON 序列化的 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (node != null) { m.put("node", node); }
        if (status != null) { m.put("status", status); }
        m.put("nodeElapsedMs", nodeElapsedMs);
        if (traceId != null) { m.put("traceId", traceId); }
        if (intent != null) { m.put("intent", intent); }
        m.put("securityPassed", securityPassed);
        if (message != null) { m.put("message", message); }
        return m;
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
}
