package com.chinacreator.gzcm.runtime.core.agent.tool;

import java.util.Date;
import java.util.Map;

/**
 * 工具调用实体
 * 表示一次具体的工具调用请求
 *
 * @author CDRC Design Team
 */
public class ToolCall {

    private String id;                  // 工具调用 ID
    private String toolName;            // 工具名称
    private Map<String, Object> arguments; // 调用参数
    private String sessionId;           // 所属会话
    private Date timestamp;

    public ToolCall() {
        this.timestamp = new Date();
    }

    public ToolCall(String toolName, Map<String, Object> arguments) {
        this();
        this.toolName = toolName;
        this.arguments = arguments;
    }

    // ── Getters & Setters ──────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
