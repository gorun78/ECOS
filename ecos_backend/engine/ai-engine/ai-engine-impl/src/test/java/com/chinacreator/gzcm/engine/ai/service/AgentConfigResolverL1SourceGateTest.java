package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.runtime.llm.config.LlmSettings;
import com.chinacreator.gzcm.runtime.llm.config.LlmSettingsSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G-2 门禁 — {@code AgentConfigResolver} 的 L1 全局默认层取值链。
 *
 * <p>锁两件事：① 桥接装配且返回非空白 ⇒ 采用 sys_config 值；
 * ② 未装配 ⇒ 用 {@code @Value} 注入值（等价改造前）。</p>
 *
 * <p>为什么这层值得单测：L1 是「未显式配置的 agent」的唯一来源，
 * 一旦桥接读错键/读错类型，影响面是全部默认 agent（V354 初版播种值
 * 0.7/8192 与 {@code @Value} 缺省 0.3/8000 不符，就是这条链会放大的错）。</p>
 */
class AgentConfigResolverL1SourceGateTest {

    @Test
    @DisplayName("桥接有值 ⇒ L1 采用库值（覆盖 @Value）")
    void dbValuesWinOverValue() {
        AgentConfigResolver resolver = newResolver(
                new LlmSettings("db/model", 1.2d, 60_000L, 32000),
                "deepseek-chat", 0.3d, 300_000L, 8000);

        AgentLoopConfig c = resolver.resolve("default", null);

        assertEquals("db/model", c.getModel());
        assertEquals(1.2d, c.getTemperature());
        assertEquals(60_000L, c.getAgentTimeoutMs());
        assertEquals(32000, c.getMaxContextTokens());
    }

    @Test
    @DisplayName("桥接未装配 ⇒ L1 用 @Value（等价改造前）")
    void fallsBackToValueWhenNoBridge() {
        AgentConfigResolver resolver = newResolver(null,
                "yml-model", 0.3d, 300_000L, 8000);

        AgentLoopConfig c = resolver.resolve("default", null);

        assertEquals("yml-model", c.getModel());
        assertEquals(0.3d, c.getTemperature());
        assertEquals(8000, c.getMaxContextTokens());
    }

    @Test
    @DisplayName("LlmSettings.DEFAULTS 的 temperature/maxContext 必须等于 AgentConfigResolver 的 @Value 缺省")
    void recordDefaultsMatchValueDefaults() {
        // 这条断言防的是 V354 初版那类错：DEFAULTS 抄了 LLMGatewayProperties 而非 AgentConfigResolver
        assertEquals(0.3d, LlmSettings.DEFAULTS.defaultTemperature());
        assertEquals(8000, LlmSettings.DEFAULTS.maxContextTokens());
        assertEquals(300_000L, LlmSettings.DEFAULTS.agentTimeoutMs());
    }

    // ── 装配辅助 ────────────────────────────────────────────────

    private static AgentConfigResolver newResolver(LlmSettings dbSettings,
                                                   String model, double temp, long timeout, int maxCtx) {
        AgentConfigResolver r = new AgentConfigResolver();
        ReflectionTestUtils.setField(r, "l1DefaultProvider", "deepseek");
        ReflectionTestUtils.setField(r, "l1DefaultModel", model);
        ReflectionTestUtils.setField(r, "l1DefaultTemperature", temp);
        ReflectionTestUtils.setField(r, "l1MaxContextTokens", maxCtx);
        ReflectionTestUtils.setField(r, "l1AgentTimeoutMs", timeout);
        ReflectionTestUtils.setField(r, "l1MaxIterations", 5);
        ReflectionTestUtils.setField(r, "l1DefaultSystemPrompt", "sys");
        if (dbSettings != null) {
            LlmSettingsSource src = mock(LlmSettingsSource.class);
            when(src.settings()).thenReturn(dbSettings);
            ReflectionTestUtils.setField(r, "llmSettingsProvider", providerOf(src));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        return p;
    }
}
