package com.chinacreator.gzcm.runtime.core.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 对话请求
 * 封装发送给 LLM 的完整请求
 *
 * @author CDRC Design Team
 */
public class ChatRequest {

    private String model;                       // 模型名称
    private List<ChatMessage> messages;         // 对话消息列表
    private List<Map<String, Object>> tools;    // 可用工具定义（function calling）
    private String toolChoice;                  // "auto" / "none" / specific tool
    private Double temperature;
    private Integer maxTokens;
    private Map<String, Object> extraParams;    // 额外参数

    // ── Inner class ──────────────────────────

    public static class ChatMessage {
        private String role;        // system / user / assistant / tool
        private String content;     // 文本内容
        private String name;        // 工具名称（role=tool 时使用）
        private String toolCallId;  // 工具调用 ID
        private List<ToolCallRequest> toolCalls; // 助手请求工具调用

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }

        public static ChatMessage tool(String toolCallId, String toolName, String content) {
            ChatMessage msg = new ChatMessage("tool", content);
            msg.toolCallId = toolCallId;
            msg.name = toolName;
            return msg;
        }

        // ── Getters & Setters ──────────────────

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getToolCallId() { return toolCallId; }
        public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

        public List<ToolCallRequest> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<ToolCallRequest> toolCalls) { this.toolCalls = toolCalls; }
    }

    public static class ToolCallRequest {
        private String id;
        private String type = "function";
        private FunctionCall function;

        public ToolCallRequest() {}

        public ToolCallRequest(String id, String name, String arguments) {
            this.id = id;
            this.function = new FunctionCall(name, arguments);
        }

        // ── Inner class ──────────────────────────

        public static class FunctionCall {
            private String name;
            private String arguments; // JSON string

            public FunctionCall() {}

            public FunctionCall(String name, String arguments) {
                this.name = name;
                this.arguments = arguments;
            }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public String getArguments() { return arguments; }
            public void setArguments(String arguments) { this.arguments = arguments; }
        }

        // ── Getters & Setters ──────────────────

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public FunctionCall getFunction() { return function; }
        public void setFunction(FunctionCall function) { this.function = function; }
    }

    // ── Getters & Setters ──────────────────────────

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public List<Map<String, Object>> getTools() { return tools; }
    public void setTools(List<Map<String, Object>> tools) { this.tools = tools; }

    public String getToolChoice() { return toolChoice; }
    public void setToolChoice(String toolChoice) { this.toolChoice = toolChoice; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Map<String, Object> getExtraParams() { return extraParams; }
    public void setExtraParams(Map<String, Object> extraParams) { this.extraParams = extraParams; }
}
