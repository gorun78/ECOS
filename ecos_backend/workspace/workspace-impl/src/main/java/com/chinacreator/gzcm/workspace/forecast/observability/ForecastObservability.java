package com.chinacreator.gzcm.workspace.forecast.observability;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.runtime.core.monitor.metrics.IMetricRecorder;

import jakarta.annotation.PostConstruct;

/**
 * 预测域可观测性门面（H-6，C219）—— 监控 5 项经 runtime-monitor {@link IMetricRecorder}
 * 唯一出口登记（铁律 §2.5-5 禁自建注册表），形态对齐认知域 {@code CognitiveObservability} 先例：
 * 门面未装配（底座 Bean 缺席/测试切片）⇒ 降级 DEBUG 日志、不静默抛错、不另起 registry。
 *
 * <p>{@link #registerCatalog()} 把 {@link ForecastMonitorItems#FIVE} 5 项逐一登记
 * （tag {@code phase=registered}），使 {@code MetricRecorderServiceImpl#snapshot()} 可见
 * 完整台账；此后仅<b>已接通记录缝</b>的项才产生命名计数（属主缝未上线的项禁假记值，
 * 见台账 Javadoc）。</p>
 */
@Component
public class ForecastObservability {

    private static final Logger log = LoggerFactory.getLogger(ForecastObservability.class);

    /** run 耗时桶（底座为计数器，P95 语义由宿主 MeterRegistry 版实现承接，台账 Javadoc 已标注）。 */
    static final String BUCKET_LT_1S = "lt1s";
    static final String BUCKET_LT_60S = "lt60s";
    static final String BUCKET_GE_60S = "ge60s";

    private final ObjectProvider<IMetricRecorder> recorderProvider;

    public ForecastObservability(ObjectProvider<IMetricRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    /** 启动登记：5 项台账逐一经门面 increment（phase=registered）。 */
    @PostConstruct
    public void registerCatalog() {
        for (String metric : ForecastMonitorItems.FIVE) {
            increment(metric, tags("phase", "registered"));
        }
        log.info("预测域监控 5 项已经 runtime-monitor 门面登记: {}", ForecastMonitorItems.FIVE);
    }

    /** ① run 耗时：按桶计数（缝=异步执行完成点，H-6 接通）。 */
    public void recordRunDuration(long millis) {
        increment(ForecastMonitorItems.FC_RUN_DURATION_MS,
                tags("bucket", bucketOf(millis)));
    }

    /** ② DATA_CHECK：成败两计数（相除即失败率；缝=run 终态回写，H-6 接通）。 */
    public void recordDataCheck(boolean passed) {
        increment(ForecastMonitorItems.FC_DATA_CHECK_FAILURE_RATIO,
                tags("result", passed ? "pass" : "fail"));
    }

    /** ③ DQ BLOCK：导入门禁拒收计数（属主缝=卷 02；本方仅暴露记录入口，未接通前不产生命名计数）。 */
    public void recordDqBlocked(int rowCount) {
        increment(ForecastMonitorItems.FC_DQ_BLOCK_TREND, tags("rows", String.valueOf(rowCount)));
    }

    /** ④ 回测 MAPE（属主缝=H-4/卷 05；同上，禁假接线）。 */
    public void recordBacktestMape(String band) {
        increment(ForecastMonitorItems.FC_BACKTEST_MAPE_AVG, tags("band", band));
    }

    /** ⑤ 事实数据时效（属主缝=卷 02 快照比对；同上，禁假接线）。 */
    public void recordFactStaleness(String ageBand) {
        increment(ForecastMonitorItems.FC_FACT_STALENESS, tags("ageBand", ageBand));
    }

    private void increment(String name, Map<String, String> tags) {
        IMetricRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            // 底座未装配：DEBUG 降级留痕（对齐 CognitiveObservability；禁自建 registry 兜底）
            log.debug("IMetricRecorder 未装配，预测域计数降级为日志: name={} tags={}", name, tags);
            return;
        }
        recorder.increment(name, tags);
    }

    private static String bucketOf(long millis) {
        if (millis < 1000L) {
            return BUCKET_LT_1S;
        }
        return millis < 60_000L ? BUCKET_LT_60S : BUCKET_GE_60S;
    }

    private static Map<String, String> tags(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }
}
