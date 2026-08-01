package com.chinacreator.gzcm.engine.ai.service;

import java.util.Map;

/**
 * LLM 返回的工具调用描述。
 */
public class ToolCall {

    /** LLM 返回的 tool_call id（OpenAI/DeepSeek 风格） */
    private String id;

    /** 工具名称（函数名） */
    private String name;

    /** 工具参数 JSON（已反序列化为 Map） */
    private Map<String, Object> arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    @Override
    public String toString() {
        return "ToolCall{id=" + id + ", name=" + name + ", args=" + arguments + "}";
    }
}
