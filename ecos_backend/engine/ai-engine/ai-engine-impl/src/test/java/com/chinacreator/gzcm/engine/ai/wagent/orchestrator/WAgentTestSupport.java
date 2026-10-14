package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessService;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 编排器测试切片用的 {@link ObjectProvider} 桩（F10-08 首评挂钩第 12 个构造参）。
 *
 * <p>{@code WAgentOrchestrator} 对 {@link ReadinessService} 走懒解析（readiness 侧反向依赖
 * 本包的 Question/Budget 服务，直接构造注入会成 Spring 环），测试面必须显式给两态：
 * <b>缺席</b>（{@link #noReadinessService()}，主链用例只验编排，不该被探测拖住）与
 * <b>在位</b>（{@link #serving}，首评挂钩用例专用）。禁在用例里裸写 {@code mock(ObjectProvider.class)}
 * 后忘了 stub {@code getIfAvailable()} —— 默认 null 会让"在位"用例假绿。</p>
 */
final class WAgentTestSupport {

    private WAgentTestSupport() {
    }

    /** ReadinessService 缺席态：{@code getIfAvailable()} 返 null ⇒ 编排器只 WARN 跳过首评。 */
    @SuppressWarnings("unchecked")
    static ObjectProvider<ReadinessService> noReadinessService() {
        return mock(ObjectProvider.class);
    }

    /** ReadinessService 在位态：返回给定实例（可为 mock），供首评挂钩断言。 */
    @SuppressWarnings("unchecked")
    static ObjectProvider<ReadinessService> serving(ReadinessService readinessService) {
        ObjectProvider<ReadinessService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(readinessService);
        return provider;
    }
}
