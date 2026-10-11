package com.chinacreator.gzcm.engine.cognitive.api.outcome;

/**
 * §9.4「防止错误学习」七种禁触发自动更新的情形（词表单源 = 本枚举）。
 *
 * <p>命中任一情形 ⇒ <b>不得</b>触发自动更新生产 Belief/Rule/Model；评估转
 * {@link OutcomeEvaluationStatus#NEEDS_RECONCILE}，只产出候选/告警引用，
 * 并在 {@code warnings[]} 内如实登记命中项与原因（fail-loud，禁静默通过）。</p>
 *
 * <p>红线：本枚举只描述"为什么不能自动学"，不构成"可以自动学"的白名单 ——
 * 七项全不命中也只代表<b>允许生成候选</b>，正式更新仍须走 §9.2 步骤 8/9
 * （回归门禁 + security-engine 审批 + 新版本不覆盖旧版本）。</p>
 */
public enum OutcomeEvaluationBlocker {

    /** 实际数据未关账，或尚未达到成熟观察窗口。 */
    ACTUALS_NOT_CLOSED("actuals not closed or maturity window not reached"),

    /** 预测与实际口径不一致（指标定义/时间范围/组织范围/币种单位任一不对齐）。 */
    CALIBER_MISMATCH("forecast and actual caliber mismatch"),

    /** 关键数据质量门禁失败。 */
    DQ_GATE_FAILED("critical data quality gate failed"),

    /** 证据不足或来源冲突。 */
    EVIDENCE_INSUFFICIENT_OR_CONFLICTED("evidence insufficient or source conflicted"),

    /** 误差可能由一次性外部事件造成（不可归因于模型/假设）。 */
    ONE_OFF_EXTERNAL_EVENT("error may stem from one-off external event"),

    /** 新旧模型未在同一基准集上完成回归（§12.3 基准集版本须固定可追溯）。 */
    BASELINE_REGRESSION_INCOMPLETE("baseline regression not completed on frozen benchmark set"),

    /** 变更影响高风险业务规则但未获审批。 */
    HIGH_RISK_RULE_UNAPPROVED("change affects high-risk business rule without approval");

    private final String reason;

    OutcomeEvaluationBlocker(String reason) {
        this.reason = reason;
    }

    /** 机器可读原因（英文冻结字面量，禁改写；对外文案由前端 i18n 承载）。 */
    public String reason() {
        return reason;
    }
}
