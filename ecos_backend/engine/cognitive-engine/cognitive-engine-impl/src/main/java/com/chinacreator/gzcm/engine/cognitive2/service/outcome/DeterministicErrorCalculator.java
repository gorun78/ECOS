package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性误差计算器（《认知域与智能域补充详细设计 v1.0》§9.2 步骤 3/4 + §9.3 末段铁律）。
 *
 * <h3>为什么必须是本类而不是 LLM</h3>
 * §9.3 末段：「该接口的数值字段必须由确定性业务计算服务产生，认知引擎只负责组织引用、
 * 诊断和候选建议」。§0.3 红线 6：LLM 零参与数值。故 predicted/actual 由外部确定性计算
 * 产物（{@code CalculationResultRef}）供入，本类只做<b>纯函数</b>误差运算与偏差分解校验，
 * 全程 {@link BigDecimal}（MC02 禁 double 承载金额），无 I/O、无 Spring、无状态。
 *
 * <h3>可复算（SUP-OUT-02）</h3>
 * 同输入必得同输出（无随机、无时钟、无 locale 依赖）；{@code relativeError} 统一
 * {@link #ERROR_SCALE} 位 HALF_UP。偏差分解必须满足「贡献合计与总差额在容差内一致」，
 * 不一致时<b>如实返回残差与 {@code consistent=false}</b>，禁自动摊平差额造假账
 * （附录 B 禁捷径）。
 *
 * <h3>样本不适用（§9.2 步骤 3）</h3>
 * 缺预测/缺实际/实际为 0（相对误差无定义）⇒ {@code applicable=false} + 明确原因，
 * <b>不得</b>以 0 误差伪装"预测准确"。
 */
public final class DeterministicErrorCalculator {

    /** 比率列统一精度（4 位小数，与 V267 metric_results_text 内 relativeError 口径一致）。 */
    public static final int ERROR_SCALE = 4;

    /** 偏差分解默认容差（贡献合计 vs 总差额）。 */
    public static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.0001");

    /** 除法运算上下文（避免无限小数抛 ArithmeticException；比率仍按 ERROR_SCALE 收口）。 */
    private static final MathContext MC = MathContext.DECIMAL64;

    private DeterministicErrorCalculator() {}

    // ═══════════════ 单指标误差（§9.2 步骤 3） ═══════════════

    /**
     * 单指标误差：绝对误差 + 相对误差。
     *
     * @param metricRef      指标引用（原样回带，便于调用方对齐）
     * @param predicted      预测值（来自确定性计算产物，可为 null = 无预测）
     * @param actual         实际值（来自确定性计算产物，可为 null = 未关账/无实际）
     * @param calculationRef 确定性计算产物引用（可复算锚点）
     * @return 计算结果；样本不适用时 {@code applicable=false} 且带原因
     */
    public static MetricError compute(String metricRef, BigDecimal predicted,
                                      BigDecimal actual, String calculationRef) {
        if (predicted == null) {
            return notApplicable(metricRef, calculationRef,
                    "predicted missing (no deterministic forecast value)");
        }
        if (actual == null) {
            return notApplicable(metricRef, calculationRef,
                    "actual missing (actuals not available / not closed)");
        }
        BigDecimal absoluteError = predicted.subtract(actual).abs();
        if (actual.signum() == 0) {
            // 相对误差无定义：保留绝对误差，比率标不适用（禁以 0 或 MAX 伪造）
            return new MetricError(metricRef, calculationRef, false, absoluteError, null,
                    "relative error undefined: actual = 0");
        }
        BigDecimal relativeError = absoluteError.divide(actual.abs(), MC)
                .setScale(ERROR_SCALE, RoundingMode.HALF_UP);
        return new MetricError(metricRef, calculationRef, true, absoluteError, relativeError, null);
    }

    /**
     * 批量计算：逐指标产出，不适用项保留在结果内（带原因），<b>不静默丢弃</b>
     * —— 丢弃会让"部分指标算不出"看起来像"全部算成功"（DG-6 空集/故障严格可分）。
     */
    public static List<MetricError> computeAll(List<MetricInput> inputs) {
        List<MetricError> out = new ArrayList<>();
        if (inputs == null) {
            return out;
        }
        for (MetricInput in : inputs) {
            if (in == null) {
                continue;
            }
            out.add(compute(in.metricRef(), in.predicted(), in.actual(), in.calculationRef()));
        }
        return out;
    }

    // ═══════════════ 聚合指标 MAE / MAPE（§9.2 步骤 3） ═══════════════

    /**
     * 聚合：MAE（平均绝对误差）与 MAPE（平均绝对百分比误差）。
     *
     * <p>只对 {@code applicable=true} 的样本求均值；MAPE 另需 relativeError 非空。
     * 可用样本数为 0 时返回 {@code sampleCount=0} 且两个均值为 {@code null}
     * （禁返回 0 伪装"零误差"）。</p>
     */
    public static Aggregate aggregate(List<MetricError> errors) {
        int total = errors == null ? 0 : errors.size();
        List<BigDecimal> absList = new ArrayList<>();
        List<BigDecimal> relList = new ArrayList<>();
        if (errors != null) {
            for (MetricError e : errors) {
                if (e == null || !e.applicable()) {
                    continue;
                }
                if (e.absoluteError() != null) {
                    absList.add(e.absoluteError());
                }
                if (e.relativeError() != null) {
                    relList.add(e.relativeError());
                }
            }
        }
        BigDecimal mae = mean(absList);
        BigDecimal mape = mean(relList);
        return new Aggregate(total, absList.size(), relList.size(), mae, mape);
    }

    // ═══════════════ 偏差分解一致性（§9.2 步骤 4） ═══════════════

    /**
     * 偏差分解校验：按维度（项目/部门/期间/因素）拆分总差额，校验贡献合计与总差额
     * 在容差内一致（SUP-OUT-02 通过标准：「贡献合计与总差额容差内一致」）。
     *
     * <p>不一致时返回 {@code consistent=false} + 残差，由调用方登记 warning 并转
     * {@code NEEDS_RECONCILE}；<b>本方法绝不修改 contributions 去凑平</b>。</p>
     *
     * @param totalDelta    总差额（= 预测 - 实际，带符号）
     * @param contributions 各维度贡献（带符号；key = 维度成员标识）
     * @param tolerance     容差（null ⇒ {@link #DEFAULT_TOLERANCE}）
     */
    public static Decomposition decompose(BigDecimal totalDelta,
                                          Map<String, BigDecimal> contributions,
                                          BigDecimal tolerance) {
        BigDecimal tol = tolerance == null ? DEFAULT_TOLERANCE : tolerance.abs();
        if (totalDelta == null) {
            return new Decomposition(false, null, null, tol,
                    "totalDelta missing (cannot verify contribution sum)");
        }
        BigDecimal sum = BigDecimal.ZERO;
        int members = 0;
        if (contributions != null) {
            for (Map.Entry<String, BigDecimal> en : contributions.entrySet()) {
                if (en.getValue() == null) {
                    continue;
                }
                sum = sum.add(en.getValue());
                members++;
            }
        }
        if (members == 0) {
            return new Decomposition(false, sum, totalDelta.subtract(sum), tol,
                    "no contribution members (decomposition unavailable)");
        }
        BigDecimal residual = totalDelta.subtract(sum);
        boolean consistent = residual.abs().compareTo(tol) <= 0;
        return new Decomposition(consistent, sum, residual, tol,
                consistent ? null
                        : "contribution sum " + sum.toPlainString()
                          + " differs from total delta " + totalDelta.toPlainString()
                          + " by " + residual.toPlainString() + " (tolerance " + tol.toPlainString() + ")");
    }

    // ═══════════════ 内部 ═══════════════

    private static MetricError notApplicable(String metricRef, String calculationRef, String reason) {
        return new MetricError(metricRef, calculationRef, false, null, null, reason);
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), MC)
                .setScale(ERROR_SCALE, RoundingMode.HALF_UP);
    }

    /** 计算入参（一条指标）。 */
    public record MetricInput(String metricRef, BigDecimal predicted, BigDecimal actual,
                              String calculationRef) {}

    /**
     * 单指标误差结果。
     *
     * @param applicable            样本是否适用（false ⇒ 必有 notApplicableReason）
     * @param absoluteError         绝对误差 |predicted - actual|
     * @param relativeError         相对误差（actual=0 时为 null）
     * @param notApplicableReason   不适用原因（§9.2 步骤 3 明确原因，禁空串）
     */
    public record MetricError(String metricRef, String calculationRef, boolean applicable,
                              BigDecimal absoluteError, BigDecimal relativeError,
                              String notApplicableReason) {

        /** 转 §9.3 metrics[] 项（供 Service 组装响应）。 */
        public Map<String, Object> toContractMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("metricRef", metricRef);
            m.put("absoluteError", absoluteError);
            m.put("relativeError", relativeError);
            m.put("calculationRef", calculationRef);
            m.put("applicable", applicable);
            if (!applicable) {
                m.put("notApplicableReason", notApplicableReason);
            }
            return m;
        }
    }

    /**
     * 聚合结果。
     *
     * @param sampleCount        入参总数
     * @param maeSampleCount     参与 MAE 的样本数
     * @param mapeSampleCount    参与 MAPE 的样本数
     * @param mae                平均绝对误差（无可用样本 ⇒ null）
     * @param mape               平均绝对百分比误差（比率形态，无可用样本 ⇒ null）
     */
    public record Aggregate(int sampleCount, int maeSampleCount, int mapeSampleCount,
                            BigDecimal mae, BigDecimal mape) {}

    /**
     * 偏差分解校验结果。
     *
     * @param consistent 贡献合计是否与总差额在容差内一致
     * @param sum        贡献合计
     * @param residual   残差（totalDelta - sum）
     * @param tolerance  使用的容差
     * @param reason     不一致/不可分解原因（consistent=true 时为 null）
     */
    public record Decomposition(boolean consistent, BigDecimal sum, BigDecimal residual,
                                BigDecimal tolerance, String reason) {}
}
