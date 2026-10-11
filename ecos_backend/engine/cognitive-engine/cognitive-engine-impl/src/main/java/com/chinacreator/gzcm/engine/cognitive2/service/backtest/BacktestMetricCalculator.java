package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestMetricSummary;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.DeviationDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测三指标 + {@code LOW_SAMPLE} 守卫的<b>纯确定性</b>计算器（详设-09 v1.2 §十一 H-4 / W225 / U-7）。
 *
 * <h3>指标定义取自 PRD-09 §5.2「公式与守卫原文不变」，本类不重定义</h3>
 * <table border="1">
 *   <caption>H-4 三新增指标与既有两指标的边界</caption>
 *   <tr><th>指标</th><th>PRD 公式</th><th>本类职责</th></tr>
 *   <tr><td>偏差方向</td><td>{@code Σ(预测−实际) 符号 → OVER_FORECAST/UNDER_FORECAST/BALANCED}</td>
 *       <td>实现者（聚合方向 + 逐行方向计数）</td></tr>
 *   <tr><td>区间覆盖率</td><td>{@code 实际 ∈ [P10,P90] 格子数 / n}，守卫 {@code n<5 ⇒ LOW_SAMPLE 不参与告警}</td>
 *       <td>实现者（分子分母排除数全回显）</td></tr>
 *   <tr><td>数据覆盖率</td><td>{@code 该期 ACTUAL 格子数 / 应预测格子数}</td><td>实现者</td></tr>
 *   <tr><td>MAE / MAPE</td><td>既有</td>
 *       <td><b>不重算</b>：MAPE 作为越限判据由调用方从同一评估面（{@code DeterministicErrorCalculator}
 *       的聚合值）传入，本类只比较；{@code zero_actual_count} 披露项按同一定义计数</td></tr>
 * </table>
 *
 * <h3>纪律</h3>
 * <ul>
 *   <li><b>零 I/O、零 LLM、零配置读取</b>：阈值由 {@link Thresholds} 一次性传入（来源必须是
 *       sysman {@code config_group} 门面，见 {@code BacktestReviewThresholdReader}），
 *       本类不含任何硬编码缺省阈值（R-66 ①「禁前端传阈值」的对偶面 = 禁代码传常量）；</li>
 *   <li><b>缺值不伪装</b>：不可得的比率一律 {@code null} + 原因，禁写 0 或 100%
 *       （DG-6 空集与故障严格可分 / X-44 禁伪 200）；</li>
 *   <li><b>区间三值禁插值</b>：P10/P90 只准来自既有 run 明细（R-62 ①），缺三值的样本进
 *       {@code intervalExcludedCount}，绝不由 P50 或邻值合成；</li>
 *   <li>精度与 {@code DeterministicErrorCalculator} 同纪律：比率 scale=4、HALF_UP，金额原样不动（MC02）。</li>
 * </ul>
 */
public final class BacktestMetricCalculator {

    /** 比率精度（与 {@code DeterministicErrorCalculator.ERROR_SCALE} 同口径，只读取不自算误差）。 */
    public static final int RATIO_SCALE = 4;

    /** PRD 死区未配置时的判定说明（进 {@code numericCaliber}，禁读者把纯符号误当死区判定）。 */
    public static final String SIGN_ONLY_CALIBER = "on_target_band unset: direction = sign of "
            + "Σ(predicted-actual) per PRD-09 §5.2 (BALANCED only on exact zero)";

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private BacktestMetricCalculator() {
    }

    /**
     * 单样本输入：一行 = 一个 metricRef 的预测/实际/区间三值（全部原值透传，禁本类改写）。
     *
     * @param metricRef          指标引用
     * @param predicted          预测值（run 侧原值，可空）
     * @param actual             实际值（可空；null=未回灌，0=实测为零，两态严格可分）
     * @param predictedP10       区间下界（run 明细原值；缺 ⇒ 该样本进排除集）
     * @param predictedP90       区间上界（同上）
     * @param intervalSourceRef  三值来源定位（run 明细引用；未接线 ⇒ null，由调用方记 seam-absent）
     * @param bothSidesResolved  预测与实际是否双侧齐备（{@code sample_count} 有效样本判据）
     */
    public record Sample(String metricRef,
                         BigDecimal predicted,
                         BigDecimal actual,
                         BigDecimal predictedP10,
                         BigDecimal predictedP90,
                         String intervalSourceRef,
                         boolean bothSidesResolved) {
    }

    /**
     * 阈值集（全部可空；空 = 未配置 ⇒ 对应判定不成立，不得取代码缺省）。
     *
     * @param onTargetBandRatio 偏差方向死区（相对 {@code |actual|}）；null ⇒ 按 PRD 原文取纯符号
     * @param mapeRatio         MAPE 越限线（PRD/R-66 原文 {@code MAPE>15%}）
     * @param minSampleCount    {@code LOW_SAMPLE} 下限（PRD 守卫原文 {@code n<5}）
     * @param minIntervalCoverage 区间覆盖率下限（R-66 原文 {@code 覆盖率<80%} 的区间面）
     * @param minDataCoverage     数据覆盖率下限（R-66 原文 {@code 覆盖率<80%} 的数据面）
     * @param sources           逐枚阈值的生效来源标注（键名 + UNSET/INVALID 形态）
     */
    public record Thresholds(BigDecimal onTargetBandRatio,
                             BigDecimal mapeRatio,
                             Integer minSampleCount,
                             BigDecimal minIntervalCoverage,
                             BigDecimal minDataCoverage,
                             Map<String, String> sources) {

        public Map<String, String> sourcesOrEmpty() {
            return sources == null ? Map.of() : sources;
        }
    }

    /** 计算产物：摘要 + 越限项 + 告警（告警由调用方并入响应 {@code warnings[]}，fail-loud）。 */
    public record Result(BacktestMetricSummary summary, List<String> breaches, List<String> warnings) {
    }

    /**
     * 主入口。
     *
     * @param samples             逐样本（来自既有评估面的行，禁另算一套数值）
     * @param expectedSampleCount 应预测格子数（= 库内评估行数，非前端传入的覆盖率）
     * @param threshold           配置单源阈值
     * @param mape                既有确定性口径的 MAPE（同一评估面 {@code OutcomeEvaluateResult#getMape()}
     *                            原值；不可得 ⇒ null ⇒ MAPE 闸不评估，<b>禁本类重算</b>）
     */
    public static Result compute(List<Sample> samples, int expectedSampleCount,
                                 Thresholds threshold, BigDecimal mape) {
        List<Sample> rows = samples == null ? List.of() : samples;
        Thresholds t = threshold == null
                ? new Thresholds(null, null, null, null, null, Map.of()) : threshold;
        List<String> warnings = new ArrayList<>();
        BacktestMetricSummary s = new BacktestMetricSummary();
        s.setThresholdSources(new LinkedHashMap<>(t.sourcesOrEmpty()));
        s.setNumericCaliber("ratios scale=" + RATIO_SCALE + " HALF_UP; amounts as supplied (MC02 BigDecimal); "
                + (t.onTargetBandRatio() == null ? SIGN_ONLY_CALIBER : "direction band = |actual| x on_target_band"));

        Map<String, Integer> directionCounts = new LinkedHashMap<>();
        for (DeviationDirection d : DeviationDirection.values()) {
            directionCounts.put(d.name(), 0);
        }

        int assessed = 0;
        int bothSides = 0;
        int actualCells = 0;
        int zeroActual = 0;
        int intervalCovered = 0;
        int intervalAssessable = 0;
        int intervalExcluded = 0;
        BigDecimal netSignedDelta = null;
        BigDecimal bandBase = null;
        List<String> intervalExcludeReasons = new ArrayList<>();
        List<String> missingRefs = new ArrayList<>();
        List<String> breachCandidates = new ArrayList<>();

        for (Sample row : rows) {
            if (row == null || row.metricRef() == null || row.metricRef().isBlank()) {
                warnings.add("BACKTEST_ROW_SKIPPED: sample without metricRef cannot be attributed");
                continue;
            }

            // ── 数据覆盖率分子（PRD 第 ⑤ 行：该期 ACTUAL 格子数）+ zero_actual_count 披露 ──
            if (row.actual() != null) {
                actualCells++;
                if (row.actual().compareTo(ZERO) == 0) {
                    zeroActual++;
                }
            } else {
                if (!missingRefs.contains(row.metricRef())) {
                    missingRefs.add(row.metricRef());
                }
                warnings.add("BACKTEST_SAMPLE_INCOMPLETE: metricRef=" + row.metricRef() + " actual=null (zeroActual"
                        + " and null-actual are distinct states: null means unresolved, 0 means measured zero)");
            }

            // ── 偏差方向：逐行 + 聚合（Σ 符号，PRD 第 ③ 行） ──
            DeviationDirection direction = deviationDirection(row, t.onTargetBandRatio());
            directionCounts.merge(direction.name(), 1, Integer::sum);
            assessed++;
            if (row.bothSidesResolved() && row.predicted() != null && row.actual() != null) {
                bothSides++;
                BigDecimal delta = row.predicted().subtract(row.actual());
                netSignedDelta = netSignedDelta == null ? delta : netSignedDelta.add(delta);
                bandBase = bandBase == null ? row.actual().abs() : bandBase.add(row.actual().abs());
            }

            // ── 区间覆盖（缺三值 ⇒ 排除集，禁插值） ──
            IntervalVerdict iv = intervalVerdict(row);
            if (iv.assessable()) {
                intervalAssessable++;
                if (iv.covered()) {
                    intervalCovered++;
                }
            } else {
                intervalExcluded++;
                intervalExcludeReasons.add(row.metricRef() + ":" + iv.reason());
            }
        }

        s.setDeviationAssessedSampleCount(assessed);
        s.setDeviationDirectionCounts(directionCounts);
        s.setOnTargetBand(t.onTargetBandRatio());
        s.setZeroActualCount(zeroActual);
        s.setNetSignedDelta(netSignedDelta);
        s.setAggregateDeviationDirection(
                aggregateDirection(netSignedDelta, bandBase, t.onTargetBandRatio(), warnings).name());

        // ── 指标二：区间覆盖率 ──
        s.setIntervalCoveredCount(intervalCovered);
        s.setIntervalAssessableSampleCount(intervalAssessable);
        s.setIntervalExcludedCount(intervalExcluded);
        s.setIntervalValueSource(intervalSourceOf(rows));
        if (intervalAssessable > 0) {
            s.setIntervalCoverage(ratio(intervalCovered, intervalAssessable));
            if (intervalExcluded > 0) {
                s.setIntervalExclusionReason("excluded=" + intervalExcluded + " "
                        + String.join(",", intervalExcludeReasons));
            }
        } else {
            s.setIntervalCoverage(null);
            s.setIntervalExclusionReason(intervalExcluded > 0
                    ? "no assessable sample: every row lacks P10/P90 or actual (excluded=" + intervalExcluded
                            + " " + String.join(",", intervalExcludeReasons) + ")"
                    : "no sample supplied to interval seam");
            warnings.add("INTERVAL_COVERAGE_UNAVAILABLE: " + s.getIntervalExclusionReason()
                    + " (never reported as 0; interpolation of P10/P90 is forbidden by R-62 ①)");
        }
        if (intervalAssessable > 0 && t.minIntervalCoverage() != null
                && s.getIntervalCoverage().compareTo(t.minIntervalCoverage()) < 0) {
            breachCandidates.add("MIN_INTERVAL_COVERAGE measured=" + plain(s.getIntervalCoverage())
                    + " threshold=" + plain(t.minIntervalCoverage())
                    + " (covered=" + intervalCovered + "/" + intervalAssessable + ")");
        } else if (intervalAssessable == 0 && t.minIntervalCoverage() != null) {
            warnings.add("INTERVAL_COVERAGE_GATE_UNEVALUABLE: min-coverage threshold configured ("
                    + plain(t.minIntervalCoverage()) + ") but no assessable sample, gate neither passed nor failed");
        }

        // ── 指标三：数据覆盖率 ──
        s.setDataCoverageExpectedCount(expectedSampleCount);
        s.setDataCoverageAvailableCount(actualCells);
        s.setDataCoverageMissingRefs(missingRefs);
        if (expectedSampleCount > 0) {
            s.setDataCoverage(ratio(actualCells, expectedSampleCount));
        } else {
            s.setDataCoverage(null);
            warnings.add("DATA_COVERAGE_UNAVAILABLE: expectedSampleCount<=0 (no declared metricRef to divide by,"
                    + " never reported as 100%)");
        }
        if (expectedSampleCount > 0 && t.minDataCoverage() != null
                && s.getDataCoverage().compareTo(t.minDataCoverage()) < 0) {
            breachCandidates.add("MIN_DATA_COVERAGE measured=" + plain(s.getDataCoverage())
                    + " threshold=" + plain(t.minDataCoverage())
                    + " (actualCells=" + actualCells + "/" + expectedSampleCount + ")");
        }

        // ── MAPE 越限闸（PRD/R-66 原文 MAPE>15%；数值沿用既有聚合，禁本类重算） ──
        if (t.mapeRatio() != null) {
            if (mape == null) {
                warnings.add("MAPE_GATE_UNEVALUABLE: mape_ratio configured (" + plain(t.mapeRatio())
                        + ") but the existing evaluation surface produced no MAPE"
                        + " (zero-actual rows excluded per PRD §5.2, never substituted with 0 or 1)");
            } else if (mape.compareTo(t.mapeRatio()) > 0) {
                breachCandidates.add("MAX_MAPE measured=" + plain(mape) + " threshold=" + plain(t.mapeRatio())
                        + " (zeroActualCount=" + zeroActual + " excluded from the ratio)");
            }
        }

        // ── LOW_SAMPLE 守卫（H-4 要点 2：样本不足 ⇒ 不给误导性结论，fail-loud 带实测数）──
        // 实测样本数 n = 有效样本（预测与实际双侧齐备），不是请求声明数：
        // 拿"应有的行"充样本数会让守卫形同虚设（DG-6：缺值行不得计入可用面）。
        int measured = bothSides;
        s.setMeasuredSampleCount(measured);
        s.setMinSampleCount(t.minSampleCount());
        if (t.minSampleCount() == null) {
            s.setLowSample(true);
            s.setLowSampleReason("LOW_SAMPLE: min-sample threshold unset in "
                    + keyOrPlaceholder(t, "min_samples")
                    + " (fail-closed: no conclusion without a configured floor, measured=" + measured + ")");
            warnings.add(s.getLowSampleReason());
        } else if (measured < t.minSampleCount()) {
            s.setLowSample(true);
            s.setLowSampleReason("LOW_SAMPLE: measured=" + measured + " < min_samples=" + t.minSampleCount()
                    + " (source=" + keyOrPlaceholder(t, "min_samples") + ")");
            warnings.add(s.getLowSampleReason());
        }
        if (s.isLowSample()) {
            s.setSuppressedAssertions(new ArrayList<>(breachCandidates));
            s.setBreachedAssertions(List.of());
            if (!breachCandidates.isEmpty()) {
                warnings.add("LOW_SAMPLE_SUPPRESSED: " + breachCandidates.size()
                        + " breached assertion(s) withheld from conclusion (never asserted as findings,"
                        + " PRD-09 §5.2 guard: n<5 不参与告警)");
            }
        } else {
            s.setBreachedAssertions(new ArrayList<>(breachCandidates));
            s.setSuppressedAssertions(List.of());
        }
        return new Result(s, new ArrayList<>(breachCandidates), warnings);
    }

    /**
     * 逐行偏差方向（PRD-09 §5.2 第 ③ 行：{@code Σ(预测−实际) 符号}；
     * 配置了 {@code on_target_band} 才用死区收窄 BALANCED，禁代码缺省）。
     */
    public static DeviationDirection deviationDirection(Sample row, BigDecimal bandRatio) {
        if (row == null || row.predicted() == null || row.actual() == null) {
            return DeviationDirection.NOT_DETERMINABLE;   // 差额无定义 ⇒ 不猜（DG-6）
        }
        BigDecimal signedDelta = row.predicted().subtract(row.actual());
        if (bandRatio != null && row.actual().compareTo(ZERO) != 0
                && signedDelta.abs().compareTo(row.actual().abs().multiply(bandRatio)) <= 0) {
            return DeviationDirection.BALANCED;
        }
        if (signedDelta.signum() == 0) {
            return DeviationDirection.BALANCED;
        }
        return signedDelta.signum() > 0 ? DeviationDirection.OVER_FORECAST : DeviationDirection.UNDER_FORECAST;
    }

    /**
     * 聚合偏差方向：{@code Σ(预测−实际)} 的符号（PRD 原文口径，零可加样本 ⇒ {@code NOT_DETERMINABLE}）。
     * <p>死区按 {@code Σ|actual| × on_target_band} 折算，与逐行同一把尺，禁两处各设一套。</p>
     */
    static DeviationDirection aggregateDirection(BigDecimal netSignedDelta, BigDecimal bandBase,
                                                 BigDecimal bandRatio, List<String> warnings) {
        if (netSignedDelta == null) {
            if (warnings != null) {
                warnings.add("AGGREGATE_DIRECTION_NOT_DETERMINABLE: no sample with both predicted and actual"
                        + " (measured=0); reported as NOT_DETERMINABLE, never as BALANCED");
            }
            return DeviationDirection.NOT_DETERMINABLE;
        }
        if (bandRatio != null && bandBase != null
                && netSignedDelta.abs().compareTo(bandBase.multiply(bandRatio)) <= 0) {
            return DeviationDirection.BALANCED;
        }
        if (netSignedDelta.signum() == 0) {
            return DeviationDirection.BALANCED;
        }
        return netSignedDelta.signum() > 0 ? DeviationDirection.OVER_FORECAST : DeviationDirection.UNDER_FORECAST;
    }

    /** 区间判定三态载体：不可判 ⇒ {@code assessable=false} 且必带原因（禁静默丢行）。 */
    public record IntervalVerdict(boolean assessable, boolean covered, String reason) {

        static IntervalVerdict excluded(String reason) {
            return new IntervalVerdict(false, false, reason);
        }
    }

    /** 区间覆盖判定（三值齐备才可判；P10&gt;P90 = 数据缺陷，进排除集不猜）。 */
    private static IntervalVerdict intervalVerdict(Sample row) {
        if (row.predictedP10() == null || row.predictedP90() == null || row.actual() == null) {
            return IntervalVerdict.excluded("missing p10/p90/actual triple"
                    + " (interval seam not supplied; synthesis forbidden by R-62 ①)");
        }
        if (row.predictedP10().compareTo(row.predictedP90()) > 0) {
            return IntervalVerdict.excluded("inverted-quantile-pair p10>p90");
        }
        boolean covered = row.actual().compareTo(row.predictedP10()) >= 0
                && row.actual().compareTo(row.predictedP90()) <= 0;
        return new IntervalVerdict(true, covered, covered ? null : "actual outside [p10,p90]");
    }

    /** 三值来源回显（首样本来源引用；全部为空 ⇒ 明示接缝未接线）。 */
    private static String intervalSourceOf(List<Sample> rows) {
        for (Sample r : rows) {
            if (r != null && r.intervalSourceRef() != null && !r.intervalSourceRef().isBlank()) {
                return r.intervalSourceRef();
            }
        }
        return "interval-triple-seam-not-wired(ForecastIntervalTripleReader bean absent):"
                + " P10/P90 must come from existing run detail, interpolation forbidden (R-62 ①)";
    }

    private static String keyOrPlaceholder(Thresholds t, String leaf) {
        for (Map.Entry<String, String> e : t.sourcesOrEmpty().entrySet()) {
            if (e.getKey() != null && e.getKey().endsWith(leaf)) {
                return e.getValue() == null ? e.getKey() : e.getKey() + "=" + e.getValue();
            }
        }
        return "ecos.fc.backtest.review_threshold." + leaf + "(UNSET)";
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "null" : v.toPlainString();
    }
}
