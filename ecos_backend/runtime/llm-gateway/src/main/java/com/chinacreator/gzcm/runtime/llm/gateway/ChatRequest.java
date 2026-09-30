package com.chinacreator.gzcm.runtime.llm.gateway;

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
    /**
     * @deprecated PMO-74 N14 / 铁律 §2.4：调用方自报的明文 provider 密钥一律不被信任，
     *             {@code LLMGatewayImpl} 不再消费本字段（出现即 warn + 默认 DENY）。
     *             密钥请改经 {@link #apiKeyRef} 引用 + security-engine 密钥服务取得。
     *             字段保留仅为兼容既有签名（API 只增不改），后续波次随调用方迁移删除。
     */
    @Deprecated
    private String apiKey;
    /**
     * provider 密钥<b>引用</b>（非明文）— 由 {@code SecurityEngineBridge} 经 security-engine
     * 密钥/解密服务解析。格式：{@code secret:<secretId>} / {@code enc:<keyId>:<cipher>} /
     * 裸 {@code <secretId>}（设计意图 = {@code ProfileConfig.apiKeyRef} 的运行时接线，PMO-74 N14）。
     */
    private String apiKeyRef;
    /** 调用方主体标识（如 {@code llm-gateway:<subsystem>:<profile>}），用于密钥权限校验与审计主体 */
    private String callerId;

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

    /** @deprecated 见 {@link #apiKey} 字段说明（PMO-74 N14，服务端不再消费） */
    @Deprecated
    public String getApiKey() { return apiKey; }

    /** @deprecated 见 {@link #apiKey} 字段说明（PMO-74 N14，请改用 {@link #setApiKeyRef}） */
    @Deprecated
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiKeyRef() { return apiKeyRef; }
    public void setApiKeyRef(String apiKeyRef) { this.apiKeyRef = apiKeyRef; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }
}
