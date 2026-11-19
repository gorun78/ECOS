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

    /** 未桥接/读取失败时的兜底（= LLMGatewayProperties 现行缺省，禁 fail-open 改行为）。 */
    public static final LlmSettings DEFAULTS = new LlmSettings(
            "deepseek/deepseek-chat", 0.7d, 300_000L, 8192);
}
