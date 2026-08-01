package com.chinacreator.gzcm.engine.ai.service;

/**
 * 工具执行结果 — 对应一次 ToolCall.execute() 的返回值。
 */
public class ToolResult {

    private String toolCallId;
    private String toolName;
    private boolean success;
    private String content;
    private String error;
    private long durationMs;

    public ToolResult() {}

    public static ToolResult ok(String toolCallId, String toolName, String content, long durationMs) {
        ToolResult r = new ToolResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = true;
        r.content = content;
        r.durationMs = durationMs;
        return r;
    }

    public static ToolResult fail(String toolCallId, String toolName, String error, long durationMs) {
        ToolResult r = new ToolResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = false;
        r.error = error;
        r.content = "{\"error\":\"" + (error != null ? error.replace("\"", "\\\"") : "unknown") + "\"}";
        r.durationMs = durationMs;
        return r;
    }

    public static ToolResult timeout(String toolCallId, String toolName) {
        return fail(toolCallId, toolName, "timeout", 30_000L);
    }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    @Override
    public String toString() {
        return "ToolResult{tool=" + toolName + ", success=" + success + ", durationMs=" + durationMs + "}";
    }
}
