package com.chinacreator.gzcm.engine.ai.guardrail;

import java.util.List;
import java.util.Map;

/**
 * 工具裁决结果 —— 详细设计-06 F06-02 要点 3 的 {@code Decision{allowed, reason, policyId, obligations, latencyMs}}。
 *
 * <p>三态语义（F06-01 要点 3 / F06-14）：</p>
 * <ul>
 *   <li>{@link Verdict#ALLOW}：security-engine 明确返回 {@code result=true}；可携带 obligations。</li>
 *   <li>{@link Verdict#DENY}：明确 DENY；错误分类 {@code GUARDRAIL_DENIED}（403，不重试）。</li>
 *   <li>{@link Verdict#FAIL_CLOSED}：不可用 / 超时 / 响应不可解析 / 非明确 ALLOW 的其余一切形态；
 *       错误分类 {@code GUARDRAIL_FAIL_CLOSED}（503，不重试，审计 + monitor）。</li>
 * </ul>
 *
 * <p><b>禁</b>：把 {@code reason} 原文写入响应体（X-49）；把 FAIL_CLOSED 折叠成 DENY 或 ALLOW。</p>
 */
public final class ToolDecision {

    /** 裁决三态（与 V204 台账 {@code decision} 检查约束字面量一致）。 */
    public enum Verdict {
        ALLOW, DENY, FAIL_CLOSED
    }

    private final Verdict verdict;
    private final String reason;
    private final String policyId;
    private final ToolObligations obligations;
    private final long latencyMs;

    private ToolDecision(Verdict verdict, String reason, String policyId,
                         ToolObligations obligations, long latencyMs) {
        this.verdict = verdict;
        this.reason = reason;
        this.policyId = policyId;
        this.obligations = obligations == null ? ToolObligations.none() : obligations;
        this.latencyMs = latencyMs;
    }

    public static ToolDecision allow(String policyId, ToolObligations obligations, long latencyMs) {
        return new ToolDecision(Verdict.ALLOW, null, policyId, obligations, latencyMs);
    }

    public static ToolDecision deny(String reason, String policyId, long latencyMs) {
        return new ToolDecision(Verdict.DENY, reason, policyId, ToolObligations.none(), latencyMs);
    }

    public static ToolDecision failClosed(String reason, long latencyMs) {
        return new ToolDecision(Verdict.FAIL_CLOSED, reason, null, ToolObligations.none(), latencyMs);
    }

    public boolean allowed() {
        return verdict == Verdict.ALLOW;
    }

    public boolean failClosed() {
        return verdict == Verdict.FAIL_CLOSED;
    }

    public Verdict verdict() {
        return verdict;
    }

    public String reason() {
        return reason;
    }

    public String policyId() {
        return policyId;
    }

    public ToolObligations obligations() {
        return obligations;
    }

    public long latencyMs() {
        return latencyMs;
    }

    /** 审计/台账用：只带摘要与 ID，禁带 SQL / 入参 / 结果原文（F06-03 要点 2）。 */
    public Map<String, Object> toAuditDetail(String toolKey) {
        return Map.of(
                "tool", toolKey == null ? "-" : toolKey,
                "decision", verdict.name(),
                "policyId", policyId == null ? "-" : policyId,
                "latencyMs", latencyMs,
                "obligations", obligationSummary());
    }

    /** obligations 摘要（列名/规则名，不含数据值）。 */
    public List<Map<String, Object>> obligationSummary() {
        return obligations.summary();
    }
}
