package com.chinacreator.gzcm.engine.ai.service;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * 契约版本号（#143/B5-1，详设-10 F10-17 版本闭合）。
     *
     * <p>W 契约目录的唯一键是 {@code uniq(name, version)}，且只有 active 版可执行；
     * 版本若不随调用一起进咽喉，咽喉只能按名字解析 ⇒ 契约工具恒「工具未找到」。
     * LLM 直连的通用工具路径为 {@code null}（无版本即不解析契约，禁"取最新版"猜测）。</p>
     */
    private String version;

    /** Schema 校验失败的参数名列表（供 LLM 下一轮修正参数） */
    private List<String> invalidParams;

    public ToolCall() {
        this.invalidParams = new ArrayList<>();
    }

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, null);
    }

    public ToolCall(String id, String name, Map<String, Object> arguments, String version) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
        this.version = version;
        this.invalidParams = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<String> getInvalidParams() { return invalidParams; }
    public void setInvalidParams(List<String> invalidParams) { this.invalidParams = invalidParams; }

    @Override
    public String toString() {
        return "ToolCall{id=" + id + ", name=" + name + ", args=" + arguments
                + ", invalidParams=" + invalidParams + "}";
    }
}
