package com.chinacreator.gzcm.runtime.llm.gateway;

import com.chinacreator.gzcm.runtime.llm.model.ProfileConfig;

/**
 * LLM 连接配置 — provider、model、baseUrl、apiKey、temperature、maxTokens
 */
public class LLMConfig {

    private String provider;
    private String model;
    private String baseUrl;
    /**
     * @deprecated PMO-74 N14：明文字段不再被 {@code LLMGatewayImpl} 消费（保留兼容既有签名）。
     *             请使用 {@link #apiKeyRef}，密钥经 security-engine 密钥服务在服务端解析。
     */
    @Deprecated
    private String apiKey;
    /** provider 密钥引用（secret:/enc:/裸 secretId），{@code ProfileConfig.apiKeyRef} 的运行时载体 */
    private String apiKeyRef;
    private Double temperature;
    private Integer maxTokens;

    public LLMConfig() {}

    /**
     * @deprecated 第四参为明文密钥的旧构造（PMO-74 N14 起服务端不消费）；请改用
     *             无参构造 + {@link #setApiKeyRef(String)}。
     */
    @Deprecated
    public LLMConfig(String provider, String model, String baseUrl, String apiKey,
                     Double temperature, Integer maxTokens) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /**
     * 从 ProfileConfig 快捷构建。
     * <p>PMO-74 N14 接线修正：此前把 {@code apiKeyRef}（引用）塞进 {@code apiKey}（明文槽位），
     * 导致下游把引用字符串直接当 Bearer 密钥外发。现改为落入独立的 {@code apiKeyRef} 槽位，
     * 由 {@code SecurityEngineBridge} 在服务端解析为真实密钥。</p>
     */
    public static LLMConfig fromProfile(ProfileConfig profile) {
        LLMConfig config = new LLMConfig();
        config.setProvider(profile.getProvider());
        config.setModel(profile.getModel());
        config.setBaseUrl(profile.getBaseUrl());
        config.setApiKeyRef(profile.getApiKeyRef());
        config.setTemperature(profile.getTemperature());
        config.setMaxTokens(profile.getMaxTokens());
        return config;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /** @deprecated 见 {@link #apiKey} 字段说明（PMO-74 N14） */
    @Deprecated
    public String getApiKey() { return apiKey; }

    /** @deprecated 见 {@link #apiKey} 字段说明（PMO-74 N14，请改用 {@link #setApiKeyRef}） */
    @Deprecated
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiKeyRef() { return apiKeyRef; }
    public void setApiKeyRef(String apiKeyRef) { this.apiKeyRef = apiKeyRef; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
