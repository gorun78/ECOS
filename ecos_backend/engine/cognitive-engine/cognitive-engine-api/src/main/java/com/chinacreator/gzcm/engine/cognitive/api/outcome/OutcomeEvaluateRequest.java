package com.chinacreator.gzcm.engine.cognitive.api.outcome;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * W→D 结果评估请求（《认知域与智能域补充详细设计 v1.0》§9.3
 * {@code POST /api/v1/cognitive/outcomes/evaluate} 入参）。
 *
 * <p>§9.3 文档字段（scenarioId/blueprintId/blueprintVersion/runId/forecastRunId/
 * actualRef/metricRefs/evaluationWindow）逐字段保留；另按 §9.3 末段铁律
 * <b>增补确定性计算产物引用与 §9.4 门禁声明</b> —— 没有这两组输入，计算器无输入、
 * 七项防错误学习门禁无从判定，端点只能返回伪数据（附录 B 禁捷径）。</p>
 *
 * <h3>§9.4 门禁声明的 fail-loud 口径</h3>
 * 所有 {@code Boolean} 声明字段 <b>null 一律按"未满足"处理</b>（禁乐观默认）：
 * 未声明已关账 = 未关账；未声明 DQ 门禁通过 = 未通过。这样缺输入时评估落
 * {@link OutcomeEvaluationStatus#NEEDS_RECONCILE} 而非假装 EVALUATED。
 */
@Data
public class OutcomeEvaluateRequest {

    // ══════════ §9.3 文档字段 ══════════

    /** 场景 ID（必填；逻辑引用 workspace 场景，ST09 禁跨 schema FK）。 */
    private String scenarioId;

    /** 蓝图 ID（可空：非蓝图运行也可评估，V267 列可空）。 */
    private String blueprintId;

    /** 蓝图版本（与 blueprintId 成对；单给版本号无意义）。 */
    private String blueprintVersion;

    /** 本次业务运行 ID（§9.1「任务执行完了」的那一次）。 */
    private String runId;

    /** 预测运行快照引用（§9.2 步骤 1 输入快照引用）。 */
    private String forecastRunId;

    /** 实际值权威引用（必填；三要素缺一即 400）。 */
    private ActualRef actualRef;

    /** 待评估指标引用清单（必填非空；如 M_FC_PROFIT）。 */
    private List<String> metricRefs = new ArrayList<>();

    /** 评估窗口（必填；倒置窗口不得静默纠正）。 */
    private EvaluationWindow evaluationWindow;

    // ══════════ 增补：确定性计算产物引用（§9.3 末段铁律） ══════════

    /**
     * 逐指标的计算产物绑定（必填非空；数值只从这里指向的确定性产物取，LLM 零参与）。
     *
     * <p>不用两个平行列表按下标配对 —— 顺序漂移会算出语法合法、业务全错的误差且无法反查
     * （详见 {@link MetricCalculationBinding} 类注）。</p>
     */
    private List<MetricCalculationBinding> metricCalculations = new ArrayList<>();

    /** 预测口径 ID（§9.2 步骤 2；与 {@code actualRef.caliberId} 比对）。 */
    private String caliberId;

    // ══════════ 增补：§9.4 七项防错误学习门禁输入（null = 未满足） ══════════

    /** 成熟观察窗口是否已达到（与 {@code actualRef.closed} 共同判 ACTUALS_NOT_CLOSED）。 */
    private Boolean maturityWindowReached;

    /** 关键 DQ 门禁是否通过（false/null ⇒ DQ_GATE_FAILED）。 */
    private Boolean dqGatePassed;

    /** DQ 门禁记录引用（可追溯锚点；未通过时建议携带失败明细引用）。 */
    private String dqGateRef;

    /** 证据引用（仅 ID，禁写敏感正文；空 ⇒ EVIDENCE_INSUFFICIENT_OR_CONFLICTED）。 */
    private List<String> evidenceRefs = new ArrayList<>();

    /** 来源冲突项（非空 ⇒ EVIDENCE_INSUFFICIENT_OR_CONFLICTED，§13.1 422 语义）。 */
    private List<String> sourceConflicts = new ArrayList<>();

    /** 一次性外部事件引用（非空 ⇒ ONE_OFF_EXTERNAL_EVENT，误差不可归因模型）。 */
    private String oneOffExternalEventRef;

    /** 基准集回归记录引用（空 ⇒ BASELINE_REGRESSION_INCOMPLETE，§12.3 基准集须固定可追溯）。 */
    private String baselineRegressionRef;

    /** 高风险业务规则审批引用（涉高风险规则时必填；空 ⇒ HIGH_RISK_RULE_UNAPPROVED）。 */
    private String highRiskRuleApprovalRef;

    /** 本次变更是否触及高风险业务规则（null/false 按不触及处理，但触及却无审批即拒）。 */
    private Boolean affectsHighRiskRule;

    // ══════════ 幂等 / 追踪（§7.5 / §12.2） ══════════

    /** 幂等键（同键重试不得重复建评估记录）。 */
    private String idempotencyKey;

    /** 追踪 ID（缺省由服务端从 MDC/请求头补，§12.2）。 */
    private String traceId;

    /** 租户（缺省取登录态）。 */
    private String tenantId;

    /** 组织（按安全规范传递，§12.2）。 */
    private String orgId;

    /**
     * 契约校验：返回问题列表（空 = 合法）。
     * 只做<b>结构性</b>校验（必填/格式/引用完整）；口径一致性与 §9.4 门禁判定
     * 在服务层做，因为那需要产出 warnings 而非直接 400（§9.2 步骤 3 明确原因）。
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (scenarioId == null || scenarioId.isBlank()) {
            problems.add("scenarioId");
        }
        if (metricRefs == null || metricRefs.isEmpty()) {
            problems.add("metricRefs (至少一个指标)");
        }
        if (actualRef == null) {
            problems.add("actualRef");
        } else {
            actualRef.validate().forEach(f -> problems.add("actualRef." + f));
        }
        if (evaluationWindow == null) {
            problems.add("evaluationWindow");
        } else {
            evaluationWindow.validate().forEach(f -> problems.add("evaluationWindow." + f));
        }
        if ((blueprintId == null || blueprintId.isBlank())
                && blueprintVersion != null && !blueprintVersion.isBlank()) {
            problems.add("blueprintId (给了 blueprintVersion 必须同时给 blueprintId)");
        }
        if (metricCalculations == null || metricCalculations.isEmpty()) {
            problems.add("metricCalculations (至少一条指标计算产物绑定)");
        } else {
            for (int i = 0; i < metricCalculations.size(); i++) {
                MetricCalculationBinding b = metricCalculations.get(i);
                if (b == null) {
                    problems.add("metricCalculations[" + i + "] is null");
                    continue;
                }
                final int idx = i;
                b.validate().forEach(f -> problems.add("metricCalculations[" + idx + "]." + f));
            }
        }
        return problems;
    }
}
