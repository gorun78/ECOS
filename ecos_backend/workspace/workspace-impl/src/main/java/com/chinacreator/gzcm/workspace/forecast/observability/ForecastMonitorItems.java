package com.chinacreator.gzcm.workspace.forecast.observability;

import java.util.List;

/**
 * 预测域监控 5 项台账 —— 详细设计-09 v1.2 §十一 H-6（PRD-09 §七-3，C219）。
 *
 * <p><b>底座收口（铁律 §2.5-5）</b>：5 项指标一律经 runtime-monitor
 * {@code IMetricRecorder} 门面登记，<b>禁自建注册表</b>。指标名为冻结字面量（本类单源），
 * 调用方禁拼字符串（门面 Javadoc 同款纪律）。</p>
 *
 * <p><b>记录缝属主（诚实标注，禁假接线）</b>：门面为纯计数器，
 * P95/均值/趋势语义由宿主替换 MeterRegistry 版实现后自然成立（
 * {@code MetricRecorderServiceImpl} 仅底座兜底计数）；属主缝未上线的项
 * 只登记名不假记值：</p>
 * <ul>
 *   <li>{@link #FC_RUN_DURATION_MS} — run 耗时 P95：缝=workspace 异步执行完成点（H-6 已接，桶计数）</li>
 *   <li>{@link #FC_DATA_CHECK_FAILURE_RATIO} — DATA_CHECK 失败率：缝=run 终态回写（H-6 已接，成败计数）</li>
 *   <li>{@link #FC_DQ_BLOCK_TREND} — DQ BLOCK 趋势：缝=data-engine 事实导入 DQ 门禁（卷 02 属主）</li>
 *   <li>{@link #FC_BACKTEST_MAPE_AVG} — 回测 MAPE 均值：缝=回测五指标计算（H-4/卷 05 属主）</li>
 *   <li>{@link #FC_FACT_STALENESS} — 事实数据时效：缝=data-engine 快照 checksum/asOf 比对（卷 02 属主）</li>
 * </ul>
 */
public final class ForecastMonitorItems {

    /** ① run 耗时（P95 语义经宿主 Histogram；底座桶计数 fc_run_duration_ms,bucket=…）。 */
    public static final String FC_RUN_DURATION_MS = "fc_run_duration_ms";
    /** ② DATA_CHECK 失败率（result=pass/fail 两计数相除即失败率）。 */
    public static final String FC_DATA_CHECK_FAILURE_RATIO = "fc_data_check_failure_ratio";
    /** ③ DQ BLOCK 趋势（属主缝=卷 02 导入门禁）。 */
    public static final String FC_DQ_BLOCK_TREND = "fc_dq_block_trend";
    /** ④ 回测 MAPE 均值（属主缝=H-4/卷 05 回测面）。 */
    public static final String FC_BACKTEST_MAPE_AVG = "fc_backtest_mape_avg";
    /** ⑤ 事实数据时效（属主缝=卷 02 快照时效比对）。 */
    public static final String FC_FACT_STALENESS = "fc_fact_staleness";

    /** 冻结 5 项清单（顺序 = PRD-09 §七-3 原文序）。 */
    public static final List<String> FIVE = List.of(
            FC_RUN_DURATION_MS,
            FC_DATA_CHECK_FAILURE_RATIO,
            FC_DQ_BLOCK_TREND,
            FC_BACKTEST_MAPE_AVG,
            FC_FACT_STALENESS);

    private ForecastMonitorItems() {
    }
}
