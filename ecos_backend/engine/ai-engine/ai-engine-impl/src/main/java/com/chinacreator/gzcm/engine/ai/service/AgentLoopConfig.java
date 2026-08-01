package com.chinacreator.gzcm.engine.ai.service;

/**
 * Agent 循环推理配置 — system prompt / model / temperature / maxTokens。
 * <p>
 * 用于 AgentLoopService.run() 的第一个参数，聚合 AgentProfile 中的运行时参数。
 * </p>
 */
public class AgentLoopConfig {

    /** 系统提示词（注入 tools 定义 + 行为指令） */
    private String systemPrompt;

    /** LLM 模型名称（deepseek-chat / gpt-4o 等） */
    private String model;

    /** 温度（0-2），控制输出随机性 */
    private Double temperature;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    public AgentLoopConfig() {}

    public AgentLoopConfig(String systemPrompt, String model, Double temperature, Integer maxTokens) {
        this.systemPrompt = systemPrompt;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    @Override
    public String toString() {
        return "AgentLoopConfig{model=" + model + ", temp=" + temperature + ", maxTokens=" + maxTokens + "}";
    }
}
