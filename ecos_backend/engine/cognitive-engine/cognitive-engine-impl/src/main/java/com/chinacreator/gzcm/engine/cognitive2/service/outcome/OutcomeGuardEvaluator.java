package com.chinacreator.gzcm.engine.cognitive2.service.outcome;

import com.chinacreator.gzcm.engine.cognitive.api.context.CalculationResultRef;
import com.chinacreator.gzcm.engine.cognitive.api.context.EvidenceRef;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.ActualRef;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.MetricCalculationBinding;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateRequest;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluationBlocker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * §9.4「防止错误学习」七项门禁裁决器（纯判定，无 I/O、无 LLM、无 DB）。
 *
 * <p>fail-loud 口径：所有 {@code Boolean} 声明 <b>null 一律按"未满足"</b>处理，
 * 引用类字段 <b>空白一律按"缺失"</b>处理。禁乐观默认 —— 缺输入时评估必须落
 * {@code NEEDS_RECONCILE} 并把命中项如实写进 warnings，而不是假装 EVALUATED。</p>
 *
 * <p>红线：{@link Verdict#blocked()} 为 false 也<b>不等于</b>可以自动更新生产资产，
 * 只代表允许生成候选；正式更新仍须走 §9.2 步骤 8/9（回归门禁 + security-engine 审批
 * + 新版本不覆盖旧版本）。</p>
 */
@Component
public class OutcomeGuardEvaluator {

    /** 门禁裁决结论：命中项 + 逐项原因（warnings 由调用方并入响应体）。 */
    public record Verdict(List<OutcomeEvaluationBlocker> blockers, List<String> warnings) {

        public boolean blocked() {
            return blockers != null && !blockers.isEmpty();
        }
    }

    /**
     * 逐条裁决 §9.4 七情形。命中即登记 blocker + 一条可追溯 warning（带引用值，禁只写枚举名）。
     */
    public Verdict evaluate(OutcomeEvaluateRequest req) {
        List<OutcomeEvaluationBlocker> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (req == null) {
            blockers.add(OutcomeEvaluationBlocker.EVIDENCE_INSUFFICIENT_OR_CONFLICTED);
            warnings.add("request is null: no input to evaluate");
            return new Verdict(blockers, warnings);
        }

        guardActualsClosed(req, blockers, warnings);
        guardCaliberAligned(req, blockers, warnings);
        guardDqGate(req, blockers, warnings);
        guardEvidence(req, blockers, warnings);
        guardOneOffEvent(req, blockers, warnings);
        guardBaselineRegression(req, blockers, warnings);
        guardHighRiskRule(req, blockers, warnings);
        guardCalculationInputs(req, warnings);

        return new Verdict(blockers, warnings);
    }

    /** 情形一：实际数据未关账，或尚未达到成熟观察窗口。 */
    private void guardActualsClosed(OutcomeEvaluateRequest req,
                                    List<OutcomeEvaluationBlocker> blockers,
                                    List<String> warnings) {
        ActualRef actual = req.getActualRef();
        boolean closed = actual != null && actual.isDeclaredClosed();
        boolean matured = Boolean.TRUE.equals(req.getMaturityWindowReached());
        if (closed && matured) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.ACTUALS_NOT_CLOSED);
        StringBuilder sb = new StringBuilder("ACTUALS_NOT_CLOSED: closed=")
                .append(actual == null ? "no-actual-ref" : String.valueOf(actual.getClosed()))
                .append(" maturityWindowReached=").append(req.getMaturityWindowReached());
        if (actual != null) {
            sb.append(" assetId=").append(nvl(actual.getAssetId()))
                    .append(" version=").append(nvl(actual.getVersion()));
        }
        warnings.add(sb.toString());
    }

    /**
     * 情形二：预测与实际口径不一致。
     *
     * <p>任一侧口径缺失即视为<b>无法确认对齐</b> ⇒ 按不一致处理（fail-loud）。
     * §9.2 步骤 2 要求指标定义/时间范围/组织范围/币种单位四维同口径，本层只能核对
     * caliberId 是否同值；四维明细比对属确定性计算服务职责，不在此伪造结论。</p>
     */
    private void guardCaliberAligned(OutcomeEvaluateRequest req,
                                     List<OutcomeEvaluationBlocker> blockers,
                                     List<String> warnings) {
        ActualRef actual = req.getActualRef();
        String forecastCaliber = req.getCaliberId();
        String actualCaliber = actual == null ? null : actual.getCaliberId();
        if (!isBlank(forecastCaliber) && !isBlank(actualCaliber)
                && forecastCaliber.trim().equals(actualCaliber.trim())) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.CALIBER_MISMATCH);
        warnings.add("CALIBER_MISMATCH: forecastCaliberId=" + nvl(forecastCaliber)
                + " actualCaliberId=" + nvl(actualCaliber)
                + " (blank caliber = alignment unconfirmed, treated as mismatch)");
    }

    /** 情形三：关键数据质量门禁失败（未声明通过即失败）。 */
    private void guardDqGate(OutcomeEvaluateRequest req,
                             List<OutcomeEvaluationBlocker> blockers,
                             List<String> warnings) {
        if (Boolean.TRUE.equals(req.getDqGatePassed())) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.DQ_GATE_FAILED);
        warnings.add("DQ_GATE_FAILED: dqGatePassed=" + req.getDqGatePassed()
                + " dqGateRef=" + nvl(req.getDqGateRef()));
    }

    /** 情形四：证据不足或来源冲突。 */
    private void guardEvidence(OutcomeEvaluateRequest req,
                               List<OutcomeEvaluationBlocker> blockers,
                               List<String> warnings) {
        List<String> conflicts = req.getSourceConflicts();
        int declared = req.getEvidenceRefs() == null ? 0 : req.getEvidenceRefs().size();
        int fromCalculations = countBoundEvidence(req);
        boolean insufficient = declared + fromCalculations == 0;
        boolean conflicted = conflicts != null && !conflicts.isEmpty();
        if (!insufficient && !conflicted) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.EVIDENCE_INSUFFICIENT_OR_CONFLICTED);
        warnings.add("EVIDENCE_INSUFFICIENT_OR_CONFLICTED: evidenceRefs=" + declared
                + " evidenceFromCalculationRefs=" + fromCalculations
                + " sourceConflicts=" + (conflicts == null ? 0 : conflicts.size())
                + (conflicted ? " conflictRefs=" + String.join(",", conflicts) : ""));
    }

    /** 计算产物引用自带证据（{@code CalculationResultRef.evidenceRefs}）也计入证据面，避免重复登记。 */
    private int countBoundEvidence(OutcomeEvaluateRequest req) {
        if (req.getMetricCalculations() == null) {
            return 0;
        }
        int n = 0;
        for (MetricCalculationBinding b : req.getMetricCalculations()) {
            if (b == null) {
                continue;
            }
            n += evidenceSize(b.getForecastRef());
            n += evidenceSize(b.getActualRef());
        }
        return n;
    }

    private int evidenceSize(CalculationResultRef ref) {
        if (ref == null || ref.getEvidenceRefs() == null) {
            return 0;
        }
        int n = 0;
        for (EvidenceRef e : ref.getEvidenceRefs()) {
            if (e != null && !isBlank(e.getSourceId())) {
                n++;
            }
        }
        return n;
    }

    /** 情形五：误差可能由一次性外部事件造成（有引用即视为存在该可能，禁自行排除）。 */
    private void guardOneOffEvent(OutcomeEvaluateRequest req,
                                  List<OutcomeEvaluationBlocker> blockers,
                                  List<String> warnings) {
        if (isBlank(req.getOneOffExternalEventRef())) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.ONE_OFF_EXTERNAL_EVENT);
        warnings.add("ONE_OFF_EXTERNAL_EVENT: eventRef=" + req.getOneOffExternalEventRef()
                + " (error not attributable to model/hypothesis)");
    }

    /** 情形六：新旧模型未在同一基准集上完成回归（§12.3 基准集须固定可追溯）。 */
    private void guardBaselineRegression(OutcomeEvaluateRequest req,
                                         List<OutcomeEvaluationBlocker> blockers,
                                         List<String> warnings) {
        if (!isBlank(req.getBaselineRegressionRef())) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.BASELINE_REGRESSION_INCOMPLETE);
        warnings.add("BASELINE_REGRESSION_INCOMPLETE: baselineRegressionRef is blank"
                + " (no frozen benchmark regression record)");
    }

    /** 情形七：变更影响高风险业务规则但未获审批（不触及高风险规则时本项放行）。 */
    private void guardHighRiskRule(OutcomeEvaluateRequest req,
                                   List<OutcomeEvaluationBlocker> blockers,
                                   List<String> warnings) {
        if (!Boolean.TRUE.equals(req.getAffectsHighRiskRule())) {
            return;
        }
        if (!isBlank(req.getHighRiskRuleApprovalRef())) {
            return;
        }
        blockers.add(OutcomeEvaluationBlocker.HIGH_RISK_RULE_UNAPPROVED);
        warnings.add("HIGH_RISK_RULE_UNAPPROVED: affectsHighRiskRule=true"
                + " highRiskRuleApprovalRef is blank");
    }

    /**
     * 非 §9.4 项的输入完备性告警：缺确定性计算产物引用 ⇒ 无输入可算。
     *
     * <p>不额外追加 blocker（避免与 ACTUALS_NOT_CLOSED 重复归因），但必须显式登记，
     * 由服务层据此把状态压到 NEEDS_RECONCILE —— 禁"没有输入也返回 EVALUATED"。</p>
     */
    private void guardCalculationInputs(OutcomeEvaluateRequest req, List<String> warnings) {
        List<MetricCalculationBinding> bindings = req.getMetricCalculations();
        int total = bindings == null ? 0 : bindings.size();
        int complete = 0;
        if (bindings != null) {
            for (MetricCalculationBinding b : bindings) {
                if (b != null && b.getForecastRef() != null && b.getActualRef() != null) {
                    complete++;
                }
            }
        }
        if (total > 0 && complete == total) {
            return;
        }
        warnings.add("CALCULATION_INPUT_MISSING: bindings=" + total + " bothSidesBound=" + complete
                + " (numeric values must come from deterministic calculation products, LLM never emits them)");
    }

    /** 计算输入是否完备（服务层据此决定能否进 §9.2 步骤 3/4）。 */
    public boolean hasCalculationInputs(OutcomeEvaluateRequest req) {
        if (req == null || req.getMetricCalculations() == null || req.getMetricCalculations().isEmpty()) {
            return false;
        }
        for (MetricCalculationBinding b : req.getMetricCalculations()) {
            if (b == null || b.getForecastRef() == null || b.getActualRef() == null) {
                return false;
            }
        }
        return true;
    }

    private static String nvl(String s) {
        return isBlank(s) ? "-" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
