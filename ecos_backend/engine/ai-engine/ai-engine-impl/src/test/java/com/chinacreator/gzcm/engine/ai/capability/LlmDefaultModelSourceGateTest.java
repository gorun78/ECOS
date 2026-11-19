package com.chinacreator.gzcm.engine.ai.capability;

import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.runtime.llm.config.LLMGatewayProperties;
import com.chinacreator.gzcm.runtime.llm.config.LlmSettings;
import com.chinacreator.gzcm.runtime.llm.config.LlmSettingsSource;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * G-2 消费端门禁 — {@code llm.default-model} 的 sys_config → yml 取值链。
 *
 * <p>背景：V354 把 llm 组播种进 sys_config，但 {@code LlmSettingsSource} 一度**零消费方**
 * （数据进了库、没人读 = 半成品）。本用例锁死两件事：</p>
 * <ol>
 *   <li><b>sys_config 优先</b>：桥接 Bean 装配且返回非空白值 ⇒ 采用库内值；</li>
 *   <li><b>回落 yml</b>：桥接未装配 / 返回空白 ⇒ 回落 {@code LLMGatewayProperties}（行为等价改造前）。</li>
 * </ol>
 *
 * <p>做法说明：{@code defaultModel()} 是私有方法（仅内部缺省路径调用），
 * 这里用反射直测判定逻辑本身，避免为可测性把私有方法提为公开 API
 * （那不是本批要解决的问题，且会扩大对外面）。</p>
 */
class LlmDefaultModelSourceGateTest {

    @Test
    @DisplayName("sys_config 有值 ⇒ 采用库内值（不再读 yml）")
    void dbValueWins() throws Exception {
        LlmSettingsSource source = mock(LlmSettingsSource.class);
        when(source.settings()).thenReturn(new LlmSettings("db/model-x", 0.5d, 1000L, 4096));

        assertEquals("db/model-x", invokeDefaultModel(providerOf(source), providerOf(ymlProps("yml/model"))));
    }

    @Test
    @DisplayName("sys_config 未装配 ⇒ 回落 yml（等价改造前）")
    void fallsBackToYmlWhenNoSource() throws Exception {
        assertEquals("yml/model", invokeDefaultModel(providerOf(null), providerOf(ymlProps("yml/model"))));
    }

    @Test
    @DisplayName("sys_config 返回空白 ⇒ 回落 yml（禁空白模型名外发）")
    void fallsBackWhenDbBlank() throws Exception {
        LlmSettingsSource source = mock(LlmSettingsSource.class);
        when(source.settings()).thenReturn(new LlmSettings("   ", 0.5d, 1000L, 4096));

        assertEquals("yml/model", invokeDefaultModel(providerOf(source), providerOf(ymlProps("yml/model"))));
    }

    @Test
    @DisplayName("读库抛异常 ⇒ 回落 yml（配置面故障不得让能力整体不可用）")
    void fallsBackWhenReadThrows() throws Exception {
        LlmSettingsSource source = mock(LlmSettingsSource.class);
        when(source.settings()).thenThrow(new IllegalStateException("db down"));

        assertEquals("yml/model", invokeDefaultModel(providerOf(source), providerOf(ymlProps("yml/model"))));
    }

    // ── 装配辅助 ────────────────────────────────────────────────

    private static String invokeDefaultModel(ObjectProvider<LlmSettingsSource> settingsProvider,
                                             ObjectProvider<LLMGatewayProperties> propsProvider) throws Exception {
        ModelCapabilityExecutorImpl executor = new ModelCapabilityExecutorImpl(
                mock(CapabilityLedgerDao.class),
                providerOf(mock(LLMGateway.class)),
                propsProvider,
                settingsProvider,
                providerOf(mock(ITaskManagementService.class)),
                providerOf(mock(EventBusService.class)));
        Method m = ModelCapabilityExecutorImpl.class.getDeclaredMethod("defaultModel");
        m.setAccessible(true);
        return (String) m.invoke(executor);
    }

    private static LLMGatewayProperties ymlProps(String defaultModel) {
        // LLMGatewayProperties 只有 getter（@ConfigurationProperties 绑定用），故以 mock 构造 yml 视图
        LLMGatewayProperties props = mock(LLMGatewayProperties.class);
        LLMGatewayProperties.Engine engine = mock(LLMGatewayProperties.Engine.class);
        when(engine.getDefaultModel()).thenReturn(defaultModel);
        when(props.getEngine()).thenReturn(engine);
        return props;
    }

    /** 非空则 getIfAvailable 返回该值，null 则返回 null（模拟 Bean 缺失）。 */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        return p;
    }
}
