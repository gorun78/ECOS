package com.chinacreator.gzcm.engine.ai.oag.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * OAG 强类型对话请求 DTO。
 *
 * <p>用于 {@code POST /api/v1/oag/chat/v2} 端点的 {@code @RequestBody} 绑定。
 * 相比旧版 {@code Map<String, Object>} 入参，提供类型安全和 Bean Validation 校验。</p>
 */
public class OagChatRequest {

    /** 用户消息（必填） */
    @NotBlank(message = "message 不能为空")
    private String message;

    /** 用户标识，默认 "anonymous" */
    private String userId;

    /** 租户标识，默认 "default" */
    private String tenantId;

    /** LLM 模型名称（可选，缺省时走 model-fallback 链第一项） */
    private String model;

    /** 采样温度 0.0~2.0（可选，默认 0.3） */
    private Double temperature;

    /** 最大生成 token 数（可选，默认 4096） */
    private Integer maxTokens;

    /** 业务领域（可选） */
    private String domain;

    /** 语言标识，如 zh-CN / en（可选） */
    private String language;

    /** 会话 ID（可选，复用外部传入会话） */
    private String sessionId;

    // ── getter / setter ──────────────────────────────────

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    /**
     * 拍平为 {@code Map<String, Object>}，兼容 {@link com.chinacreator.gzcm.engine.ai.oag.OagPipelineEngine#run}
     * 的入参签名。
     *
     * @return 不含 null 值的请求参数 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (message != null) { map.put("message", message); }
        if (userId != null) { map.put("userId", userId); }
        if (tenantId != null) { map.put("tenantId", tenantId); }
        if (model != null) { map.put("model", model); }
        if (temperature != null) { map.put("temperature", temperature); }
        if (maxTokens != null) { map.put("maxTokens", maxTokens); }
        if (domain != null) { map.put("domain", domain); }
        if (language != null) { map.put("language", language); }
        if (sessionId != null) { map.put("sessionId", sessionId); }
        return map;
    }
}
