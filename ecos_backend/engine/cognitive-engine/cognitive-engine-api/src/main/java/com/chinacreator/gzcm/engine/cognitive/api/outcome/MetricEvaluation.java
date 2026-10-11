package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 单指标评估结果（《认知域与智能域补充详细设计 v1.0》§9.3 {@code metrics[]} 项）。
 *
 * <h3>数值来源铁律（§9.3 末段）</h3>
 * {@code predicted}/{@code actual}/{@code absoluteError}/{@code relativeError} 必须由
 * <b>确定性业务计算服务</b>产生，认知引擎只组织引用与诊断 —— 故 {@code calculationRef}
 * 必填且必须指向可复算产物（{@link com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef}
 * {@code deterministic=true} 三要素）。<b>LLM 零参与数值</b>（§0.3 红线 6）。
 *
 * <h3>样本不适用（§9.2 步骤 3）</h3>
 * 预测/实际不可得时不得以 0 或空值伪装"误差为零"，必须填
 * {@code notApplicableReason} 并把 {@code applicable=false}（SUP-OUT-02 复算前提）。
 *
 * <p>精度：金额/比率一律 {@link BigDecimal}（MC02 禁 double 承载金额；
 * {@code relativeError} 为比率，scale 由计算器统一取 4 位 HALF_UP）。</p>
 */
@Data
public class MetricEvaluation {

    /** 指标引用（必填；如 M_FC_PROFIT / M_REALIZATION_RATE）。 */
    private String metricRef;

    /** 预测值（由确定性计算产出；样本不适用时为 null）。 */
    private BigDecimal predicted;

    /** 实际值（由确定性计算产出；样本不适用时为 null）。 */
    private BigDecimal actual;

    /** 绝对误差 = |predicted - actual|（计算器产出，禁调用方自填）。 */
    private BigDecimal absoluteError;

    /** 相对误差 = absoluteError / |actual|（actual 为 0 时不产比率，见 applicable）。 */
    private BigDecimal relativeError;

    /** 确定性计算产物引用（必填；可复算锚点，§9.2 步骤 3/4）。 */
    private String calculationRef;

    /** 样本是否适用（false ⇒ 必须有 notApplicableReason）。 */
    private boolean applicable = true;

    /** 样本不适用原因（§9.2 步骤 3「返回明确原因」；禁空串敷衍）。 */
    private String notApplicableReason;

    /** 口径 ID（§9.2 步骤 2 对齐依据；与 ActualRef.caliberId 比对）。 */
    private String caliberId;

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>{@code metricRef} / {@code calculationRef} 必填；</li>
     *   <li>{@code applicable=true} ⇒ predicted/actual/absoluteError 必填
     *       （禁"适用但无数值"的伪成功）；</li>
     *   <li>{@code applicable=false} ⇒ {@code notApplicableReason} 必填
     *       （禁静默跳过，DG-6：空集与故障严格可分）。</li>
     * </ol>
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (isBlank(metricRef)) problems.add("metricRef");
        if (isBlank(calculationRef)) problems.add("calculationRef");
        if (applicable) {
            if (predicted == null) problems.add("predicted (applicable=true 时必填)");
            if (actual == null) problems.add("actual (applicable=true 时必填)");
            if (absoluteError == null) problems.add("absoluteError (applicable=true 时必填)");
        } else if (isBlank(notApplicableReason)) {
            problems.add("notApplicableReason (applicable=false 时必填)");
        }
        return problems;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
