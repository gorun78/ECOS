package com.chinacreator.gzcm.engine.ai.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 推理循环内部消息模型 — 比 gateway.ChatMessage 多承载 tool_call / tool_result
 * <p>
 * role: system / user / assistant / tool<br>
 * content: 文本内容（assistant 纯文本 / tool 返回结果）<br>
 * toolCalls: assistant 消息中携带的工具调用列表（非空时 content 可为 null）<br>
 * toolCallId: tool 消息对应的调用 ID
 * </p>
 */
public class Message {

    private String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String toolName;

    public Message() {}

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // ─── Static factories ──────────────────────────────────────────────

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    /** assistant 消息 — 携带工具调用 */
    public static Message assistant(ToolCall toolCall) {
        Message m = new Message("assistant", null);
        m.toolCalls = new ArrayList<>(Collections.singletonList(toolCall));
        return m;
    }

    /** assistant 消息 — 携带多个工具调用 */
    public static Message assistant(List<ToolCall> toolCalls) {
        Message m = new Message("assistant", null);
        m.toolCalls = new ArrayList<>(toolCalls);
        return m;
    }

    /** tool 结果消息 — 工具执行结果反馈给 LLM */
    public static Message toolResult(ToolExecutorService.ToolResult result) {
        Message m = new Message("tool", result.getContent());
        m.toolCallId = result.getToolCallId();
        m.toolName = result.getToolName();
        return m;
    }

    // ─── Getters / Setters ─────────────────────────────────────────────

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    // ─── Serialisation helpers ─────────────────────────────────────────

    /**
     * 序列化为 Map（用于日志 / session 审计）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", truncate(content, 2000));
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> tcList = new ArrayList<>();
            for (ToolCall tc : toolCalls) {
                Map<String, Object> tcm = new LinkedHashMap<>();
                tcm.put("id", tc.getId());
                tcm.put("name", tc.getName());
                tcm.put("arguments", tc.getArguments());
                tcList.add(tcm);
            }
            map.put("toolCalls", tcList);
        }
        return map;
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...[truncated]";
    }

    @Override
    public String toString() {
        return "Message{role=" + role + ", content=" + truncate(content, 120) + "}";
    }
}
