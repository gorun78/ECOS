package com.chinacreator.gzcm.runtime.llm.config;

/**
 * LLM 运行期行为配置（不可变快照）。
 *
 * <p>只收「运行期在线调整有意义」的键（G-2 整改）：模型/温度/超时/上下文上限。
 * provider 选择、api-key-ref、模型目录清单（catalog）属部署形态，<b>不在本快照</b>，
 * 仍由 {@link LLMGatewayProperties}（yml）承载。</p>
 *
 * <p>字段语义与 {@code sys_config config_group=llm}（V354 播种）一一对应；
 * 缺省值对齐 {@link LLMGatewayProperties} 现行缺省，保证「未播种/未桥接 ⇒ 行为不变」。</p>
 */
public record LlmSettings(
        String defaultModel,
        double defaultTemperature,
        long agentTimeoutMs,
        int maxContextTokens) {

    /**
     * 未桥接/读取失败时的兜底。
     *
     * <p>取值以<b>代码真相</b>为准（2026-11-19 接线时实测定）：</p>
     * <ul>
     *   <li>{@code defaultModel = "deepseek/deepseek-chat"} —
     *       {@code ModelCapabilityExecutorImpl} 缺省路径 / LLMGatewayProperties 缺省；</li>
     *   <li>{@code defaultTemperature = 0.3} — {@code AgentConfigResolver}
     *       {@code @Value("${llm.default-temperature:0.3}")}；</li>
     *   <li>{@code agentTimeoutMs = 300000} — 同上 {@code ${llm.agent-timeout-ms:300000}}；</li>
     *   <li>{@code maxContextTokens = 8000} — 同上 {@code ${llm.max-context-tokens:8000}}。</li>
     * </ul>
     * <p>注意 V354 曾按 0.7 / 8192 播种（模仿 LLMGatewayProperties 而非 AgentConfigResolver），
     * 与真实 L1 缺省不符；桥接生效后若读数仍是 0.7/8192，会**改变**现有 agent 行为，
     * 故本批同时把 V354 播种值订正为 0.3/8000。</p>
     */
    public static final LlmSettings DEFAULTS = new LlmSettings(
            "deepseek/deepseek-chat", 0.3d, 300_000L, 8000);
}
