package com.chinacreator.gzcm.runtime.hermes.gateway;

import java.util.List;

/**
 * LLM 调用请求
 */
public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private Double temperature;
    private Integer maxTokens;
    private Boolean stream;
    private String apiKey;

    public ChatRequest() {}

    public ChatRequest(String model, List<ChatMessage> messages, Double temperature,
                       Integer maxTokens, Boolean stream) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = stream;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
