package com.chinacreator.gzcm.engine.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 循环推理结果 — turns / content / toolCalls / totalTokens / sessionId。
 */
public class AgentLoopResult {

    private int turns;
    private String content;
    private List<Map<String, Object>> toolCalls = new ArrayList<>();
    private int totalTokens;
    private String sessionId;
    private boolean success;
    private String errorMsg;
    private String traceId;
    private String errorCode;
    private String errorClass;
    private boolean retryable;

    /**
     * F06-09 ⑥：本轮压缩判定快照（供 SSE done 帧与前端压缩状态条）。
     * {@code compressionAssessed=false} 表示本轮未做判定（如无会话作用域），
     * done 帧不带压缩键 —— 前端按"未上报"渲染，禁伪造默认值。
     */
    private boolean compressionAssessed;
    private String compressionMode;
    private int compressedTurns;
    private boolean compressSkipped;

    /**
     * F06-13 答复类型（{@code NORMAL} / {@code CLARIFY} / {@code REFUSED}）。
     * 前置判定命中即为 CLARIFY/REFUSED，不进 LLM 数值路径；{@code null} = 本轮未判定（视同 NORMAL）。
     */
    private String answerType;

    /** F06-13 澄清载荷 {@code {missing, nextAction}}，仅 answerType=CLARIFY 时非空。 */
    private Map<String, Object> clarification;

    /**
     * F06-12 引用后校验结果。{@code citationAssessed=false} 表示未做判定（如无任何工具结果），
     * done 帧不带引用键 —— 前端按"未上报"渲染，禁伪造默认值。
     */
    private boolean citationAssessed;
    private boolean citationComplete;
    private String citationBlock;
    private String citationViolation;
    private int citationRegenerations;

    public AgentLoopResult() {}

    /** 成功终止 — LLM 返回了最终回复 */
    public static AgentLoopResult success(String content, int turns, String sessionId,
                                          int totalTokens, List<Map<String, Object>> toolCalls) {
        AgentLoopResult r = new AgentLoopResult();
        r.turns = turns;
        r.content = content;
        r.sessionId = sessionId;
        r.totalTokens = totalTokens;
        r.success = true;
        if (toolCalls != null) {
            r.toolCalls = toolCalls;
        }
        return r;
    }

    /** 超过最大轮次 — 5轮上限后强制终止 */
    public static AgentLoopResult maxTurnsExceeded(String sessionId, int totalTokens,
                                                    List<Map<String, Object>> toolCalls) {
        AgentLoopResult r = new AgentLoopResult();
        r.turns = 5;
        r.content = null;
        r.sessionId = sessionId;
        r.totalTokens = totalTokens;
        r.success = false;
        r.errorMsg = "Agent loop exceeded maximum turns (5)";
        if (toolCalls != null) {
            r.toolCalls = toolCalls;
        }
        return r;
    }

    /** 通用错误 */
    public static AgentLoopResult error(String sessionId, String errorMsg, int totalTokens) {
        AgentLoopResult r = new AgentLoopResult();
        r.turns = 0;
        r.sessionId = sessionId;
        r.totalTokens = totalTokens;
        r.success = false;
        r.errorMsg = errorMsg;
        return r;
    }

    /**
     * 护栏拒绝/不可用错误（详细设计-06 F06-04 + D.5 码表）。
     *
     * <p>{@code errorMsg} 必须是 {@link com.chinacreator.gzcm.common.error.AiError#userMessage()}
     * 渲染后的可读消息（含诊断码），异常原文只入日志（X-49）。</p>
     */
    public static AgentLoopResult guardrailError(String sessionId, String errorMsg, String errorCode,
                                                 String errorClass, String traceId, boolean retryable) {
        AgentLoopResult r = error(sessionId, errorMsg, 0);
        r.errorCode = errorCode;
        r.errorClass = errorClass;
        r.retryable = retryable;
        r.traceId = traceId;
        return r;
    }

    /**
     * 会话 token 配额超限（详细设计-06 F06-07 要点 3；错误码沿用 D.5 的 {@code CONTEXT_OVERFLOW}）。
     *
     * <p>{@code errorMsg} 为可读消息（含已用/上限/诊断码），异常原文与 {@code reason}
     * 只入日志（X-49）。</p>
     */
    public static AgentLoopResult contextOverflow(String sessionId, String errorMsg, String errorCode,
                                                  String errorClass, String traceId, int totalTokens) {
        AgentLoopResult r = error(sessionId, errorMsg, totalTokens);
        r.errorCode = errorCode;
        r.errorClass = errorClass;
        r.traceId = traceId;
        r.retryable = false;
        return r;
    }

    /**
     * F06-13 前置判定命中 —— 口径缺失/未批准（CLARIFY）或估算金额/工具全失败（REFUSED）。
     *
     * <p>本工厂产出的结果<b>不代表</b> LLM 失败：success=true（请求已按规则正确答复），
     * 但 content 不含数值，answerType 明示答复类型，前端据此渲染澄清问句或分类原因。</p>
     */
    public static AgentLoopResult refused(String sessionId, String answerType, String content,
                                         Map<String, Object> clarification, String errorCode,
                                         String errorClass, String traceId, boolean success) {
        AgentLoopResult r = new AgentLoopResult();
        r.turns = 0;
        r.sessionId = sessionId;
        r.content = content;
        r.success = success;
        r.answerType = answerType;
        r.clarification = clarification;
        r.errorCode = errorCode;
        r.errorClass = errorClass;
        r.traceId = traceId;
        return r;
    }

    // ─── Getters / Setters ─────────────────────────────────────────────

    public int getTurns() { return turns; }
    public void setTurns(int turns) { this.turns = turns; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }

    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }

    /**
     * 附着本轮压缩判定（F06-09 ⑥）。
     *
     * @param compressionMode {@code LLM_SUMMARY} / {@code TRUNCATE_FALLBACK}，未压缩时 null
     * @param compressedTurns 实际归档的轮数
     * @param compressSkipped true = 未压缩（开关关闭/不可判定/无可压缩段），必须原样上报
     */
    public void applyCompression(String compressionMode, int compressedTurns, boolean compressSkipped) {
        this.compressionAssessed = true;
        this.compressionMode = compressionMode;
        this.compressedTurns = compressedTurns;
        this.compressSkipped = compressSkipped;
    }

    public boolean isCompressionAssessed() { return compressionAssessed; }
    public String getCompressionMode() { return compressionMode; }
    public int getCompressedTurns() { return compressedTurns; }
    public boolean isCompressSkipped() { return compressSkipped; }

    /**
     * 附着本轮引用后校验结论（F06-12）。
     *
     * @param citationComplete   true = 数值主张全部有逐字引用块
     * @param citationBlock      通过时随答复下发的冻结引用块，未通过为 null
     * @param citationViolation  未通过类型（{@code MISSING_CITATION_BLOCK} / {@code HALLUCINATED_NUMBER}），通过为 null
     * @param regenerations      实际重生成次数（≤2，与 PARSE_FAILURE 共用配额语义）
     */
    public void applyCitation(boolean citationComplete, String citationBlock, String citationViolation,
                              int regenerations) {
        this.citationAssessed = true;
        this.citationComplete = citationComplete;
        this.citationBlock = citationBlock;
        this.citationViolation = citationViolation;
        this.citationRegenerations = regenerations;
    }

    public String getAnswerType() { return answerType; }
    public void setAnswerType(String answerType) { this.answerType = answerType; }

    public Map<String, Object> getClarification() { return clarification; }
    public void setClarification(Map<String, Object> clarification) { this.clarification = clarification; }

    public boolean isCitationAssessed() { return citationAssessed; }
    public boolean isCitationComplete() { return citationComplete; }
    public String getCitationBlock() { return citationBlock; }
    public String getCitationViolation() { return citationViolation; }
    public int getCitationRegenerations() { return citationRegenerations; }

    // ─── Serialisation ──────────────────────────────────────────────────

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("turns", turns);
        map.put("content", content);
        map.put("toolCalls", toolCalls);
        map.put("totalTokens", totalTokens);
        map.put("sessionId", sessionId);
        map.put("success", success);
        if (errorMsg != null) {
            map.put("errorMsg", errorMsg);
        }
        if (traceId != null) {
            map.put("traceId", traceId);
        }
        if (errorCode != null) {
            map.put("errorCode", errorCode);
            map.put("errorClass", errorClass);
            map.put("retryable", retryable);
        }
        if (compressionAssessed) {
            map.put("compressionMode", compressionMode);
            map.put("compressedTurns", compressedTurns);
            map.put("compressSkipped", compressSkipped);
        }
        if (answerType != null) {
            map.put("answerType", answerType);
        }
        if (clarification != null) {
            map.put("clarification", clarification);
        }
        if (citationAssessed) {
            map.put("citationComplete", citationComplete);
            if (citationBlock != null) {
                map.put("citationBlock", citationBlock);
            }
            if (citationViolation != null) {
                map.put("citationViolation", citationViolation);
            }
            map.put("citationRegenerations", citationRegenerations);
        }
        return map;
    }

    @Override
    public String toString() {
        return "AgentLoopResult{turns=" + turns + ", success=" + success
                + ", tokens=" + totalTokens + ", session=" + sessionId + "}";
    }
}
