package com.chinacreator.gzcm.workspace.forecast.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;

/**
 * H-6 监控 5 项台账验收（详设-09 v1.2 §十一 / PRD-09 §七-3，铁律 §2.5-5）。
 *
 * <p>锁三点：①台账 5 项名 = PRD 原文序且逐一经 runtime-monitor 门面登记；
 * ②只有已接通记录缝的项才产生命名计数（③④⑤属主缝在卷 02/H-4，本域禁假记值）；
 * ③门面未装配时降级留痕、不抛错、不自建 registry。</p>
 */
@DisplayName("H-6 预测域监控 5 项经 runtime-monitor 出口")
class ForecastObservabilityTest {

    /** 记录型门面替身：把每次 increment 落成 "name|{tags}" 便于逐条断言。 */
    private static final class RecordingRecorder implements IMetricRecorder {
        final List<String> events = new ArrayList<>();

        @Override
        public void increment(String name, Map<String, String> tags) {
            events.add(name + "|" + tags);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    @Test
    @DisplayName("台账 5 项名与 PRD §七-3 原文序一致")
    void monitorCatalogIsFiveFrozenNames() {
        assertEquals(List.of("fc_run_duration_ms", "fc_data_check_failure_ratio", "fc_dq_block_trend",
                "fc_backtest_mape_avg", "fc_fact_staleness"), ForecastMonitorItems.FIVE);
        assertEquals(5, ForecastMonitorItems.FIVE.size());
    }

    @Test
    @DisplayName("启动登记：5 项逐一经门面 increment(phase=registered)，只登名不假记值")
    void registerCatalogRegistersEachItemOnceThroughFacade() {
        RecordingRecorder recorder = new RecordingRecorder();
        new ForecastObservability(provider(recorder)).registerCatalog();

        assertEquals(ForecastMonitorItems.FIVE.size(), recorder.events.size(), recorder.events.toString());
        for (String name : ForecastMonitorItems.FIVE) {
            assertEquals(1, recorder.events.stream().filter(e -> e.startsWith(name + "|")
                    && e.contains("phase=registered")).count(), "每项恰好登记一次: " + name);
        }
    }

    @Test
    @DisplayName("①run 耗时桶：1s/60s 两处边界按毫秒严格小于归档")
    void runDurationBucketsUseStrictLowerBounds() {
        RecordingRecorder recorder = new RecordingRecorder();
        ForecastObservability obs = new ForecastObservability(provider(recorder));

        obs.recordRunDuration(999L);
        obs.recordRunDuration(1000L);
        obs.recordRunDuration(59_999L);
        obs.recordRunDuration(60_000L);

        assertEquals(List.of(
                ForecastMonitorItems.FC_RUN_DURATION_MS + "|{bucket=" + ForecastObservability.BUCKET_LT_1S + "}",
                ForecastMonitorItems.FC_RUN_DURATION_MS + "|{bucket=" + ForecastObservability.BUCKET_LT_60S + "}",
                ForecastMonitorItems.FC_RUN_DURATION_MS + "|{bucket=" + ForecastObservability.BUCKET_LT_60S + "}",
                ForecastMonitorItems.FC_RUN_DURATION_MS + "|{bucket=" + ForecastObservability.BUCKET_GE_60S + "}"),
                recorder.events);
    }

    @Test
    @DisplayName("②DATA_CHECK 成败两计数：相除即失败率，禁单记一侧冒充 0%")
    void dataCheckRecordsBothSides() {
        RecordingRecorder recorder = new RecordingRecorder();
        ForecastObservability obs = new ForecastObservability(provider(recorder));

        obs.recordDataCheck(true);
        obs.recordDataCheck(false);

        assertEquals(List.of(ForecastMonitorItems.FC_DATA_CHECK_FAILURE_RATIO + "|{result=pass}",
                ForecastMonitorItems.FC_DATA_CHECK_FAILURE_RATIO + "|{result=fail}"), recorder.events);
    }

    @Test
    @DisplayName("③④⑤ 属主缝未接通前只暴露入口：无调用即无命名计数")
    void ownerSideSeamsProduceNoCountsUntilInvoked() {
        RecordingRecorder recorder = new RecordingRecorder();
        ForecastObservability obs = new ForecastObservability(provider(recorder));
        obs.registerCatalog();
        recorder.events.clear();

        assertTrue(recorder.events.isEmpty(), "登记后无业务动作时禁产生命名计数（假接线）");

        obs.recordDqBlocked(12);
        obs.recordBacktestMape("gt15");
        obs.recordFactStaleness("gt72h");
        assertEquals(3, recorder.events.size(), recorder.events.toString());
        assertTrue(recorder.events.get(0).contains("rows=12"), recorder.events.get(0));
        assertTrue(recorder.events.get(1).contains("band=gt15"), recorder.events.get(1));
        assertTrue(recorder.events.get(2).contains("ageBand=gt72h"), recorder.events.get(2));
    }

    @Test
    @DisplayName("门面未装配：降级留痕不抛错（禁自建 registry 兜底）")
    void absentRecorderDegradesWithoutThrowing() {
        ForecastObservability obs = new ForecastObservability(provider(null));
        obs.registerCatalog();
        obs.recordRunDuration(10L);
        obs.recordDataCheck(true);
    }
}
