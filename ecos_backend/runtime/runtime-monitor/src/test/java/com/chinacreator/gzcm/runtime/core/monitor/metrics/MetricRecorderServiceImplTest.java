package com.chinacreator.gzcm.runtime.core.monitor.metrics;

import com.chinacreator.gzcm.runtime.core.monitor.service.impl.MetricRecorderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.7-4 指标底座实现验收：计数累计、维度分桶、快照只读（设计-05 三枚认知计数即走本门面）。
 */
class MetricRecorderServiceImplTest {

    /** 指标名冻结字面量（单源在各域 api 包；runtime-monitor 禁反向依赖引擎，故测试内用字面量）。 */
    private static final String METRIC = "cognitive_eval_truncated_total";

    @Test
    @DisplayName("同名自增累计；blank 指标名丢弃不计数")
    void incrementsAccumulatePerName() {
        MetricRecorderServiceImpl recorder = new MetricRecorderServiceImpl();
        recorder.increment(METRIC);
        recorder.increment(METRIC, Map.of());
        recorder.increment("  ");
        recorder.increment(null);

        assertEquals(2L, recorder.snapshot().get(METRIC));
        assertEquals(1, recorder.snapshot().size(), "blank/null 指标名不得进快照");
    }

    @Test
    @DisplayName("tags 参与 key 且顺序稳定（TreeMap 归一，禁 HashMap 遍历序）")
    void tagsFormStableKeyOrdering() {
        MetricRecorderServiceImpl recorder = new MetricRecorderServiceImpl();
        recorder.increment("cognitive_capability_unmapped_total", Map.of("domain", "finance", "stage", "e4"));
        recorder.increment("cognitive_capability_unmapped_total", Map.of("stage", "e4", "domain", "finance"));

        Map<String, Long> snapshot = recorder.snapshot();
        assertEquals(1, snapshot.size(), "同维度集合必须归一为同一 key");
        assertEquals(2L, snapshot.values().iterator().next());
        assertTrue(snapshot.keySet().iterator().next().endsWith("domain=finance,stage=e4"));
    }
}
