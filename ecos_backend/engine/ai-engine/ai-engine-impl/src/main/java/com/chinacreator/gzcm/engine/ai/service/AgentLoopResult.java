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
        return map;
    }

    @Override
    public String toString() {
        return "AgentLoopResult{turns=" + turns + ", success=" + success
                + ", tokens=" + totalTokens + ", session=" + sessionId + "}";
    }
}
