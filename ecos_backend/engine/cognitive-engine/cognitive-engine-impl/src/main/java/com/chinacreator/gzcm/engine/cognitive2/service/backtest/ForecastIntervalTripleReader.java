package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 区间三值（P10/P50/P90）只读接缝（详设-09 v1.2 §十一 H-4 要点 1「区间覆盖率」的数据来源端口）。
 *
 * <h3>为什么是端口而不是直连</h3>
 * <ul>
 *   <li>ST08：认知域禁直读 {@code ecos_dw}（{@code ecos_fc_result_detail} 的
 *       {@code amount_p10/p50/p90} 物理列属 data-engine 写通道独占）；</li>
 *   <li>铁律 §2.5 底座收口 + 引擎边界（{@code ArchitectureTest#shouldNotDependOnOtherEngines}）
 *       ⇒ 认知侧只声明读缝，实现由装配层以 REST 适配器接入（同
 *       {@code OutcomeMetricValueReader} / {@code ForecastRunSnapshotReader} 纪律）。</li>
 * </ul>
 *
 * <h3>R-62 ① 红线（三值全存全展，禁插值）</h3>
 * 实现必须<b>原样透传</b> run 明细已存的三值：不得由 P50 反推、不得跨期/跨项目插值、
 * 不得用置信度合成。缺三值的样本只能"不供给"，由调用方按覆盖率排除集如实登记
 * （{@code BacktestMetricCalculator} 的 {@code intervalExcludedCount}）。
 *
 * <h3>未接线语义（DG-6 / X-44）</h3>
 * 无实现 bean ⇒ 区间覆盖率 {@code null} + 定位串，禁返回空集合冒充"覆盖率为 0"。
 */
public interface ForecastIntervalTripleReader {

    /** 该实现是否能承接本次 run（按来源系统/运行 ID 前缀自证；不接受 ⇒ 返回 false 交回上层）。 */
    boolean supports(String forecastRunId);

    /**
     * 读取区间三值。
     *
     * @throws IntervalReadException 依赖故障（<b>禁</b>吞异常返回空列表，SUP-CTX-03 同族）
     */
    List<IntervalTriple> read(IntervalQuery query);

    /**
     * @param metricRef  指标引用（与评估面行对齐的锚点）
     * @param p10        区间下界（run 明细原值，禁合成）
     * @param p50        中位数原值（本指标不使用，仅随源透传以便复算核验）
     * @param p90        区间上界
     * @param sourceRef  来源定位（run 明细行引用 / 端点 URI，进 {@code intervalValueSource}）
     */
    record IntervalTriple(String metricRef,
                          BigDecimal p10,
                          BigDecimal p50,
                          BigDecimal p90,
                          String sourceRef) {
    }

    /** @param metricRefs 本次评估声明的去重指标引用集（禁全表扫描 run 明细） */
    record IntervalQuery(String forecastRunId,
                         String scenarioId,
                         String caliberId,
                         String windowFrom,
                         String windowTo,
                         List<String> metricRefs) {
    }

    /** 读缝故障（与"源侧确实没有三值"严格可分：后者 ⇒ 返回空列表并允许调用方逐 metricRef 判定）。 */
    class IntervalReadException extends RuntimeException {
        public IntervalReadException(String message, Throwable cause) {
            super(message, cause);
        }

        public IntervalReadException(String message) {
            super(message);
        }
    }
}
