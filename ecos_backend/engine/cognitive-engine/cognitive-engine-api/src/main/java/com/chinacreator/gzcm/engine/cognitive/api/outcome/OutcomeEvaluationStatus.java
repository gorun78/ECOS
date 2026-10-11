package com.chinacreator.gzcm.engine.cognitive.api.outcome;

/**
 * W→D 结果评估状态词表（《认知域与智能域补充详细设计 v1.0》§9 / DDL V267 {@code status} 列）。
 *
 * <p>词表单源 = 本枚举（禁在各 Service/Controller 内散落字面量）。四态语义：</p>
 * <ul>
 *   <li>{@link #PENDING}：评估记录已建，但确定性计算/口径对齐尚未完成（不得对外声称已评估）；</li>
 *   <li>{@link #EVALUATED}：口径对齐通过 + 误差已由确定性计算器产出 + 诊断/候选已生成；</li>
 *   <li>{@link #NEEDS_RECONCILE}：口径未对齐或触发 §9.4 禁自更新情形，转人工对账（<b>不是成功态</b>）；</li>
 *   <li>{@link #REJECTED}：输入不合法（缺权威实际值引用/窗口倒置等），评估不成立。</li>
 * </ul>
 *
 * <p>红线（§9.1）：Run 成功 != 业务结果验证过。本词表只描述"评估"这一动作的结局，
 * 任何状态都<b>不</b>代表预测正确或模型可更新。</p>
 */
public enum OutcomeEvaluationStatus {

    /** 已受理，未完成计算/对齐（禁对外当"已评估"）。 */
    PENDING,

    /** 评估完成：口径对齐 + 确定性误差 + 诊断 + 候选齐备。 */
    EVALUATED,

    /** 需人工对账：口径不一致或命中 §9.4 禁自更新情形。 */
    NEEDS_RECONCILE,

    /** 输入不合法，评估不成立。 */
    REJECTED;

    /**
     * 宽松解析：未知/空值一律 {@link #PENDING}（禁抛异常中断读路径；
     * 存量 NULL 行按 PENDING 回落，与 V268 置信列存量回落同纪律）。
     */
    public static OutcomeEvaluationStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }

    /** 是否终态（EVALUATED / REJECTED 不再自动推进；NEEDS_RECONCILE 待人工裁决）。 */
    public boolean isTerminal() {
        return this == EVALUATED || this == REJECTED;
    }
}
