package com.chinacreator.gzcm.runtime.core.agent;

import java.util.Date;
import java.util.Map;

/**
 * Agent 消息实体
 * 表示 Agent 对话中的一条消息，可以是用户输入、助手回复或工具调用结果
 *
 * @author CDRC Design Team
 */
public class AgentMessage {

    /** 消息角色 */
    public enum Role {
        SYSTEM,     // 系统提示
        USER,       // 用户输入
        ASSISTANT,  // 助手回复
        TOOL        // 工具调用结果
    }

    private String id;
    private Role role;
    private String content;
    private String toolCallId;       // 工具调用 ID（role=TOOL 时使用）
    private String toolName;         // 工具名称（role=ASSISTANT 含工具调用时使用）
    private Map<String, Object> metadata;
    private Date timestamp;

    public AgentMessage() {
        this.timestamp = new Date();
    }

    public AgentMessage(Role role, String content) {
        this();
        this.role = role;
        this.content = content;
    }

    // ── Getters & Setters ──────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    // ── Factory methods ──────────────────────────

    public static AgentMessage system(String content) {
        return new AgentMessage(Role.SYSTEM, content);
    }

    public static AgentMessage user(String content) {
        return new AgentMessage(Role.USER, content);
    }

    public static AgentMessage assistant(String content) {
        return new AgentMessage(Role.ASSISTANT, content);
    }

    public static AgentMessage tool(String toolCallId, String toolName, String content) {
        AgentMessage msg = new AgentMessage(Role.TOOL, content);
        msg.toolCallId = toolCallId;
        msg.toolName = toolName;
        return msg;
    }
}
