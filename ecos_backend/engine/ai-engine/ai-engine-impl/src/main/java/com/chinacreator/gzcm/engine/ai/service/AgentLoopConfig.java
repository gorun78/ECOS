package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.core.agent.mesh.entity.AgentRegistryEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Agent 循环推理配置 — 聚合三层覆盖后的运行时参数。
 * <p>
 * 设计为可变配置对象，通过 {@code applyXxx()} 链式方法逐层叠加，
 * 先后调用顺序即为覆盖优先级：L1 → L2 → L3 → 运行时覆盖。
 * </p>
 *
 * <h3>三层覆盖模型</h3>
 * <ol>
 *   <li><b>L1 (application.yml)</b> — 全局默认值，由 {@link AgentConfigResolver} 在构造时注入</li>
 *   <li><b>L2 (Agent 模板)</b> — role=builtin 的 AgentRegistryEntity.metadata，通过 {@link #applyTemplate(Map)}</li>
 *   <li><b>L3 (Agent 实例)</b> — 用户级 AgentRegistryEntity，通过 {@link #applyInstance(AgentRegistryEntity)}</li>
 *   <li><b>运行时覆盖</b> — API 请求参数，通过 {@link #applyOverrides(Map)}</li>
 * </ol>
 *
 * <p>每层仅覆盖非 null / 非空字段，未设置的字段保留上一层的值。</p>
 */
public class AgentLoopConfig {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** LLM Provider 名称（如 deepseek, openai 等） */
    private String defaultProvider;

    /** LLM 模型名称（deepseek-chat / gpt-4o 等） */
    private String model;

    /** 温度（0-2），控制输出随机性 */
    private Double temperature;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    /** 上下文窗口最大 token 数（用于 trimHistory 预算管理） */
    private Integer maxContextTokens;

    /** Agent 推理最大迭代轮次 */
    private Integer maxIterations;

    /** Agent 推理总超时（毫秒） */
    private Long agentTimeoutMs;

    /** 系统提示词 */
    private String systemPrompt;

    // ─── 构造器 ────────────────────────────────────────────────────────

    public AgentLoopConfig() {}

    /**
     * 从 L1 默认值构造（由 AgentConfigResolver 调用）。
     *
     * @param defaultProvider   默认 LLM Provider
     * @param model             默认模型
     * @param temperature       默认温度
     * @param maxTokens         默认最大输出 token
     * @param maxContextTokens  默认上下文窗口
     * @param maxIterations     默认最大迭代轮次
     * @param agentTimeoutMs    默认超时（毫秒）
     * @param systemPrompt      默认系统提示词
     */
    public AgentLoopConfig(String defaultProvider, String model, Double temperature,
                           Integer maxTokens, Integer maxContextTokens,
                           Integer maxIterations, Long agentTimeoutMs, String systemPrompt) {
        this.defaultProvider = defaultProvider;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxContextTokens = maxContextTokens;
        this.maxIterations = maxIterations;
        this.agentTimeoutMs = agentTimeoutMs;
        this.systemPrompt = systemPrompt;
    }

    // ─── 三层覆盖方法 ──────────────────────────────────────────────────

    /**
     * L2: 应用 Agent 模板配置（role=builtin 的 metadata JSON Map）。
     * <p>
     * 只覆盖模板中显式设置的字段（非 null / 非空字符串），未设置的保留 L1 默认值。
     * </p>
     *
     * @param templateMeta 模板 metadata Map（从 AgentRegistryEntity.metadata JSON 解析）
     * @return this（链式调用）
     */
    @SuppressWarnings("unchecked")
    public AgentLoopConfig applyTemplate(Map<String, Object> templateMeta) {
        if (templateMeta == null || templateMeta.isEmpty()) {
            return this;
        }
        if (templateMeta.containsKey("defaultProvider") && templateMeta.get("defaultProvider") != null) {
            String v = stringOrNull(templateMeta.get("defaultProvider"));
            if (v != null) this.defaultProvider = v;
        }
        if (templateMeta.containsKey("model") && templateMeta.get("model") != null) {
            String v = stringOrNull(templateMeta.get("model"));
            if (v != null) this.model = v;
        }
        if (templateMeta.containsKey("temperature") && templateMeta.get("temperature") != null) {
            this.temperature = toDouble(templateMeta.get("temperature"));
        }
        if (templateMeta.containsKey("maxTokens") && templateMeta.get("maxTokens") != null) {
            this.maxTokens = toInteger(templateMeta.get("maxTokens"));
        }
        if (templateMeta.containsKey("maxContextTokens") && templateMeta.get("maxContextTokens") != null) {
            this.maxContextTokens = toInteger(templateMeta.get("maxContextTokens"));
        }
        if (templateMeta.containsKey("maxIterations") && templateMeta.get("maxIterations") != null) {
            this.maxIterations = toInteger(templateMeta.get("maxIterations"));
        }
        if (templateMeta.containsKey("agentTimeoutMs") && templateMeta.get("agentTimeoutMs") != null) {
            this.agentTimeoutMs = toLong(templateMeta.get("agentTimeoutMs"));
        }
        if (templateMeta.containsKey("systemPrompt") && templateMeta.get("systemPrompt") != null) {
            String v = stringOrNull(templateMeta.get("systemPrompt"));
            if (v != null) this.systemPrompt = v;
        }
        return this;
    }

    /**
     * L3: 应用 Agent 实例配置（用户级 AgentRegistryEntity）。
     * <p>
     * 从 entity.metadata JSON 中提取字段，只覆盖显式设置的值。
     * </p>
     *
     * @param instance 用户 Agent 实例
     * @return this（链式调用）
     */
    public AgentLoopConfig applyInstance(AgentRegistryEntity instance) {
        if (instance == null) {
            return this;
        }
        Map<String, Object> meta = parseMetadata(instance.getMetadata());
        if (meta == null || meta.isEmpty()) {
            return this;
        }
        return applyTemplate(meta); // 复用同一覆盖逻辑
    }

    /**
     * 应用运行时请求覆盖（API 请求参数 map）。
     * <p>
     * 最高优先级 — 覆盖所有已设置 L1/L2/L3 的值。
     * </p>
     *
     * @param overrides 请求参数字典
     * @return this（链式调用）
     */
    public AgentLoopConfig applyOverrides(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return this;
        }
        if (overrides.containsKey("model") && overrides.get("model") != null) {
            String v = stringOrNull(overrides.get("model"));
            if (v != null) this.model = v;
        }
        if (overrides.containsKey("temperature") && overrides.get("temperature") != null) {
            this.temperature = toDouble(overrides.get("temperature"));
        }
        if (overrides.containsKey("maxTokens") && overrides.get("maxTokens") != null) {
            this.maxTokens = toInteger(overrides.get("maxTokens"));
        }
        if (overrides.containsKey("maxContextTokens") && overrides.get("maxContextTokens") != null) {
            this.maxContextTokens = toInteger(overrides.get("maxContextTokens"));
        }
        if (overrides.containsKey("maxIterations") && overrides.get("maxIterations") != null) {
            this.maxIterations = toInteger(overrides.get("maxIterations"));
        }
        if (overrides.containsKey("agentTimeoutMs") && overrides.get("agentTimeoutMs") != null) {
            this.agentTimeoutMs = toLong(overrides.get("agentTimeoutMs"));
        }
        if (overrides.containsKey("systemPrompt") && overrides.get("systemPrompt") != null) {
            String v = stringOrNull(overrides.get("systemPrompt"));
            if (v != null) this.systemPrompt = v;
        }
        if (overrides.containsKey("provider") && overrides.get("provider") != null) {
            String v = stringOrNull(overrides.get("provider"));
            if (v != null) this.defaultProvider = v;
        }
        if (overrides.containsKey("defaultProvider") && overrides.get("defaultProvider") != null) {
            String v = stringOrNull(overrides.get("defaultProvider"));
            if (v != null) this.defaultProvider = v;
        }
        return this;
    }

    // ─── Getters / Setters ──────────────────────────────────────────────

    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }

    public Integer getMaxIterations() { return maxIterations; }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }

    public Long getAgentTimeoutMs() { return agentTimeoutMs; }
    public void setAgentTimeoutMs(Long agentTimeoutMs) { this.agentTimeoutMs = agentTimeoutMs; }

    // ─── 兼容旧构造器的静态工厂（避免破坏现有调用） ─────────────────────

    /**
     * 兼容旧版四参数构造器。
     * @deprecated 使用 {@link AgentConfigResolver} 获取分层配置
     */
    @Deprecated
    public static AgentLoopConfig of(String systemPrompt, String model, Double temperature, Integer maxTokens) {
        AgentLoopConfig c = new AgentLoopConfig();
        c.systemPrompt = systemPrompt;
        c.model = model;
        c.temperature = temperature;
        c.maxTokens = maxTokens;
        c.maxContextTokens = 8000;
        return c;
    }

    @Override
    public String toString() {
        return "AgentLoopConfig{provider=" + defaultProvider + ", model=" + model
                + ", temp=" + temperature + ", maxTokens=" + maxTokens
                + ", maxCtx=" + maxContextTokens + ", maxIter=" + maxIterations
                + ", timeout=" + agentTimeoutMs + "ms}";
    }

    // ─── 内部工具方法 ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) return null;
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOrNull(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }

    private static Integer toInteger(Object val) {
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return null; }
    }

    private static Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }
}
