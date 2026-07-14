package com.chinacreator.gzcm.runtime.core.agent.tool;

import java.util.Date;
import java.util.Map;

/**
 * 工具返回实体
 * 表示一次工具调用的返回结果
 *
 * @author CDRC Design Team
 */
public class ToolResult {

    private String toolCallId;          // 对应的工具调用 ID
    private String toolName;            // 工具名称
    private boolean success;            // 是否成功
    private String content;             // 结果内容（文本格式，供 LLM 理解）
    private Object data;                // 结构化数据（供程序使用）
    private String errorMessage;        // 错误消息（失败时）
    private long durationMs;            // 执行耗时
    private Map<String, Object> metadata;
    private Date completedAt;

    public ToolResult() {
        this.completedAt = new Date();
    }

    // ── Factory methods ──────────────────────────

    public static ToolResult success(String toolCallId, String toolName, String content, Object data, long durationMs) {
        ToolResult r = new ToolResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = true;
        r.content = content;
        r.data = data;
        r.durationMs = durationMs;
        return r;
    }

    public static ToolResult error(String toolCallId, String toolName, String errorMessage) {
        ToolResult r = new ToolResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    // ── Getters & Setters ──────────────────────────

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
}
