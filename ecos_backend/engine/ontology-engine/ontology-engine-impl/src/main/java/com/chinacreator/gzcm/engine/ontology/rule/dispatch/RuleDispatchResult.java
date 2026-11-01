package com.chinacreator.gzcm.engine.ontology.rule.dispatch;

/**
 * 规则分发回执（§6.2.6②/⑧；#147G）。
 *
 * <p>四态分得比 boolean 细，是为了让「没执行」与「执行失败」与「不适用」不可互换：</p>
 * <ul>
 *   <li>{@link Status#ROUTED} — 已路由到目标执行器并有该执行面的结论（校验通过/元模型可承载）；</li>
 *   <li>{@link Status#REJECTED} — 规则本身不满足该层执行前置（缺表达式/缺 Action/语法非法）；</li>
 *   <li>{@link Status#NOT_APPLICABLE} — 该规则在本次不含该层语义（如 BUSINESS 无 action）；</li>
 *   <li>{@link Status#NOT_AVAILABLE} — 目标执行器未接入（目标态缺口），<b>禁按已验证理解</b>。</li>
 * </ul>
 *
 * @param status 四态
 * @param target 执行目标（未知层时为 {@code null}）
 * @param reason 人读原因（非 ROUTED 必填）
 * @author ECOS Ontology #147G
 */
public record RuleDispatchResult(Status status, RuleLayerTarget target, String reason) {

    public enum Status {
        ROUTED,
        REJECTED,
        NOT_APPLICABLE,
        NOT_AVAILABLE
    }

    public static RuleDispatchResult routed(RuleLayerTarget target, String reason) {
        return new RuleDispatchResult(Status.ROUTED, target, reason);
    }

    public static RuleDispatchResult rejected(RuleLayerTarget target, String reason) {
        return new RuleDispatchResult(Status.REJECTED, target, reason);
    }

    public static RuleDispatchResult notApplicable(RuleLayerTarget target, String reason) {
        return new RuleDispatchResult(Status.NOT_APPLICABLE, target, reason);
    }

    public static RuleDispatchResult notAvailable(RuleLayerTarget target, String reason) {
        return new RuleDispatchResult(Status.NOT_AVAILABLE, target, reason);
    }

    /** 只有 ROUTED 表示"该层实际得到了执行结论"；其余三态都不得向上报成功。 */
    public boolean executed() {
        return status == Status.ROUTED;
    }
}
