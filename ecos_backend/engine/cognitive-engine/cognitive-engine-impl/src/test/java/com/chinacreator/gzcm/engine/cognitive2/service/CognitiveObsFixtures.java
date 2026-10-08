package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.observability.CognitiveObservability;
import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试装配夹具：构造未接 Spring 上下文的 {@link CognitiveObservability}（C.7 三出口）。
 *
 * <p>兜底文件固定落 {@code target/}（已 gitignore），禁写到模块工作目录污染工作树。
 * 本类放在 {@code cognitive2.service} 包内是为了能调用 {@link CognitiveAuditFallback}
 * 的包级可见 {@code setFallbackFile}，供其它测试包按 {@code public} 静态方法复用。</p>
 */
public final class CognitiveObsFixtures {

    /** 兜底落盘固定路径（surefire 工作目录 = 模块目录，故相对路径即 {@code <module>/target/…}）。 */
    public static final String TEST_FALLBACK_FILE = "target/cognitive-audit-fallback-test.jsonl";
    /** 目录当文件写 → 必然 IOException，用于验证 fail-closed 第三段。 */
    private static final String UNWRITABLE_FALLBACK_FILE = "target";

    private CognitiveObsFixtures() {}

    /** IMetricRecorder 与 EventBusService 均未装配（宿主降级形态）。 */
    public static CognitiveObservability unassembled() {
        return new CognitiveObservability(provider(null), provider(null), fallback(TEST_FALLBACK_FILE));
    }

    /** 仅装配指标门面（审计仍走兜底）。 */
    public static CognitiveObservability withRecorder(IMetricRecorder recorder) {
        return new CognitiveObservability(provider(recorder), provider(null), fallback(TEST_FALLBACK_FILE));
    }

    /** 仅装配审计主通道（指标缺位）。 */
    public static CognitiveObservability withEventBus(EventBusService bus) {
        return new CognitiveObservability(provider(null), provider(bus), fallback(TEST_FALLBACK_FILE));
    }

    /** 主通道缺失 + 兜底落盘必然失败（fail-closed 拒绝业务动作）。 */
    public static CognitiveObservability auditChannelBroken() {
        return new CognitiveObservability(provider(null), provider(null), fallback(UNWRITABLE_FALLBACK_FILE));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> mockProvider = mock(ObjectProvider.class);
        when(mockProvider.getIfAvailable()).thenReturn(bean);
        return mockProvider;
    }

    private static CognitiveAuditFallback fallback(String path) {
        CognitiveAuditFallback fallback = new CognitiveAuditFallback();
        fallback.setFallbackFile(path);
        return fallback;
    }
}
