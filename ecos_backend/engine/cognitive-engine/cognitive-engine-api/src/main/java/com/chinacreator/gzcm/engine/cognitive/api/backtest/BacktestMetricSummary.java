package com.chinacreator.gzcm.engine.cognitive.api.backtest;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测指标面（详设-09 v1.2 §十一 H-4 / W225：偏差方向 + 区间覆盖率 + 数据覆盖率 + {@code LOW_SAMPLE} 守卫）。
 *
 * <p>与既有 MAE/MAPE（{@code DeterministicErrorCalculator#aggregate}）同处一个评估面，
 * 通过 {@code OutcomeEvaluateResult#backtest} <b>新增字段</b>承载 —— 既有字段名与语义零改动。</p>
 *
 * <h3>诚实语义（DG-6 / DG-9 / X-44）</h3>
 * <ul>
 *   <li>{@code null} 比率 = 该指标<b>不可得</b>（区间三值接缝未部署、无可用样本、阈值未配置），
 *       禁写 0 冒充"覆盖率为零/百分之百"；</li>
 *   <li>分子/分母/排除数一律回显，覆盖率的每个分母都可复算；</li>
 *   <li>{@code lowSample=true} ⇒ 本次只出实测样本数与"样本不足"标记，
 *       越限结论（{@link #breachedAssertions}）不得对外声称（H-4 要点 2）。</li>
 * </ul>
 *
 * <h3>阈值只来自配置单源</h3>
 * 全部阈值经 sysman {@code config_group} 门面读取（键名 {@code ecos.fc.backtest.review_threshold.*}，
 * U-6），生效来源逐枚记在 {@link #thresholdSources}；<b>严禁前端传阈值</b>（R-66 ①）。
 *
 * <h3>区间三值禁插值（R-62 ①）</h3>
 * P10/P90 只准取自既有 run 明细透出的三值；缺三值的样本进 {@link #intervalExcludedCount}
 * 并记 {@link #intervalExclusionReason}，禁由 P50 或前后样本插值合成。
 */
@Data
public class BacktestMetricSummary {

    // ── 指标一：偏差方向（确定性判定，词表 = DeviationDirection，PRD-09 §5.2 第 ③ 行原文）──

    /** 各偏差方向的样本计数（键 = {@link DeviationDirection} 枚举名，恒四键齐备，缺项计 0）。 */
    private Map<String, Integer> deviationDirectionCounts = defaultDirectionCounts();

    /** 参与偏差方向判定的样本数（{@code NOT_DETERMINABLE} 也计数，禁只报命中项造成分母漂移）。 */
    private int deviationAssessedSampleCount;

    /**
     * 聚合偏差方向（PRD-09 §5.2 原文口径：{@code Σ(预测−实际)} 的符号）。
     * <p>与逐行方向计数同时给出，前者是 PRD 冻结判据、后者是定位明细；两者由同一次扫描产出，
     * 禁前端用逐行计数自行"凑"出聚合结论（R-62 ① 全展不许插值的另一面）。</p>
     */
    private String aggregateDeviationDirection;

    /** 聚合带符号差额 {@code Σ(predicted - actual)}（仅双侧齐备样本参与；null ⇒ 无可加样本）。 */
    private BigDecimal netSignedDelta;

    /** 偏差方向判定所用死区（{@code on_target_band}）；未配置 ⇒ null，此时按 PRD 原文取纯符号（BALANCED 仅在净差恰为 0）。 */
    private BigDecimal onTargetBand;

    /** 实际值为 0 的样本数（PRD-09 §5.2 第 ② 行 MAPE 守卫的披露项，列名同源 {@code zero_actual_count}）。 */
    private int zeroActualCount;

    // ── 指标二：区间覆盖率（P10~P90 覆盖实际值的比例，三值来自 run 明细）──

    /** 区间覆盖率 = 覆盖样本 / 可判样本；不可得 ⇒ null + 原因（禁 0）。 */
    private BigDecimal intervalCoverage;

    /** 分子：实际值落在 [P10, P90] 内的样本数。 */
    private int intervalCoveredCount;

    /** 分母：同时具备 P10/P90/实际三值的样本数（= 可判样本，禁把被排除样本计入）。 */
    private int intervalAssessableSampleCount;

    /** 因缺三值被排除的样本数（R-62 ① 禁插值 ⇒ 只能排除并如实登记）。 */
    private int intervalExcludedCount;

    /** 排除/不可得原因（含接缝定位串；空白 = 无排除，禁空串敷衍）。 */
    private String intervalExclusionReason;

    /** 区间三值来源（run 明细引用/端点定位串；未接线 ⇒ 明示 seam-absent）。 */
    private String intervalValueSource;

    // ── 指标三：数据覆盖率（PRD-09 §5.2 第 ⑤ 行原文：该期 ACTUAL 格子数 / 应预测格子数）──

    /** 数据覆盖率 = available / expected；{@code expected <= 0} ⇒ null + 原因（禁除零或写 1）。 */
    private BigDecimal dataCoverage;

    /** 应有样本数 = 应预测格子数（本次声明的 metricRefs 计数，来自库内评估行而非前端传入）。 */
    private int dataCoverageExpectedCount;

    /** 已有 ACTUAL 的格子数（实际值已回灌即计入；预测缺失只影响偏差面，不影响本分子）。 */
    private int dataCoverageAvailableCount;

    /** 缺失项清单（ACTUAL 未解析到数值的 metricRef；空 = 无缺失，禁静默跳过）。 */
    private List<String> dataCoverageMissingRefs = new ArrayList<>();

    // ── LOW_SAMPLE 守卫（H-4 要点 2 / PRD-09 §5.2 守卫原文「n<5 → LOW_SAMPLE 不参与告警」）──

    /** 样本不足标记（true ⇒ 越限结论被抑制，禁对外给误导性结论；对应列 {@code sample_flag}）。 */
    private boolean lowSample;

    /** 守卫实测样本数 {@code n} = 有效样本（预测与实际双侧齐备，对应列 {@code sample_count}），非请求声明数。 */
    private int measuredSampleCount;

    /** 生效最小样本阈值；未配置 ⇒ null（此时守卫按 fail-closed 触发）。 */
    private Integer minSampleCount;

    /** 守卫原因（fail-loud，含实测数与阈值来源；非 LOW_SAMPLE 时为 null）。 */
    private String lowSampleReason;

    /** 因 LOW_SAMPLE 被抑制的断言清单（本应报出的越限项，如实列出但不成立为结论）。 */
    private List<String> suppressedAssertions = new ArrayList<>();

    /** 本次判定越限的断言清单（LOW_SAMPLE=false 且确有越限时非空 ⇒ 复审路由输入）。 */
    private List<String> breachedAssertions = new ArrayList<>();

    /** 逐枚阈值的生效来源（形如 {@code ecos.fc.backtest.review_threshold.min_samples} 或 {@code ...(UNSET)}）。 */
    private Map<String, String> thresholdSources = new LinkedHashMap<>();

    /** 计算精度说明（scale/舍入模式；与 {@code DeterministicErrorCalculator.ERROR_SCALE} 同纪律）。 */
    private String numericCaliber;

    private static Map<String, Integer> defaultDirectionCounts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (DeviationDirection d : DeviationDirection.values()) {
            m.put(d.name(), 0);
        }
        return m;
    }

    /** 补齐四键（反序列化后调用，保证词表键面稳定，前端无键漂移）。 */
    public Map<String, Integer> directionCountsOrEmpty() {
        if (deviationDirectionCounts == null || deviationDirectionCounts.isEmpty()) {
            deviationDirectionCounts = defaultDirectionCounts();
        }
        return deviationDirectionCounts;
    }
}
