package com.chinacreator.gzcm.engine.cognitive.api.backtest;

/**
 * 复审任务路由结局词表（详设-09 v1.2 §十一 H-4 / W226「阈值键与复审任务 0 ⇒ 配置单源 + 复审角色路由」）。
 *
 * <p><b>词表单源 = 本枚举</b>。每个结局都对应一条可解释语义，禁把"没做成"包装成成功：</p>
 * <ul>
 *   <li>{@link #SUBMITTED} 是唯一"已建任务"态，且必带 runtime-task 返回的真实 {@code taskId}；</li>
 *   <li>{@link #ROLE_DENIED_BY_POLICY} ⇒ HTTP 403（security-engine 裁决 DENY，不建任务）；</li>
 *   <li>{@link #ROLE_DECIDER_UNAVAILABLE} ⇒ HTTP 503（裁决链路未接入/不可达，fail-closed 不建任务）；</li>
 *   <li>其余为业务结论态（HTTP 200 + {@code routed=false}），与 §9.4 门禁命中同纪律。</li>
 * </ul>
 */
public enum BacktestReviewRouteOutcome {

    /** 确有越限 + 裁决 ALLOW 且给出复审角色 + runtime-task 已受理（返回真实 taskId）。 */
    SUBMITTED("review task submitted to runtime-task"),

    /** 本次评估无越限项 ⇒ 不建任务（不是故障）。 */
    NO_BREACH("no breached assertion, no review task created"),

    /** {@code LOW_SAMPLE} 守卫抑制 ⇒ 越限断言不成立，禁据此建复审任务。 */
    SUPPRESSED_BY_LOW_SAMPLE("sample insufficient, breached assertions suppressed, no review task"),

    /** security-engine 裁决 DENY ⇒ fail-closed 不建任务（403）。 */
    ROLE_DENIED_BY_POLICY("security-engine policy decision denied review assignment, no task created"),

    /** 裁决链路未接线或不可达 ⇒ fail-closed 不建任务（503，禁默认放行、禁自造角色）。 */
    ROLE_DECIDER_UNAVAILABLE("security-engine review-role decision unavailable, fail-closed no task"),

    /** 裁决 ALLOW 但未透出复审角色 ⇒ 缺省不建任务（禁代码内"角色名→权限"硬编码兜底，403）。 */
    ROLE_DECIDER_RETURNED_NO_ROLE("policy allowed but no reviewer role returned, no task created"),

    /** runtime-task 受理失败 ⇒ 如实透传故障（禁谎称已建）。 */
    TASK_SUBMISSION_FAILED("runtime-task submitTask failed, no task id available");

    private final String meaning;

    BacktestReviewRouteOutcome(String meaning) {
        this.meaning = meaning;
    }

    public String meaning() {
        return meaning;
    }

    /** 是否"任务已存在"（仅 {@link #SUBMITTED}）。 */
    public boolean submitted() {
        return this == SUBMITTED;
    }
}
