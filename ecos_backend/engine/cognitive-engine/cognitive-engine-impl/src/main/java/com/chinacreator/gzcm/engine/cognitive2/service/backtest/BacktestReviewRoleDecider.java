package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import java.util.List;
import java.util.Map;

/**
 * 复审角色裁决端口（详设-09 v1.2 §十一 H-4 要点 4「复审角色路由」）。
 *
 * <h3>红线：认知侧零"角色名→权限"映射</h3>
 * 本端口存在的唯一理由是把"谁该复审"这件事交给 security-engine 裁决，认知域只搬运结论：
 * {@link ReviewRoleDecision#reviewerRole()} 必须是<b>裁决透出的原值</b>；
 * 裁决没给角色 ⇒ 不建任务（{@code ROLE_DECIDER_RETURNED_NO_ROLE}），
 * <b>禁</b>在 cognitive 里维护 {@code Map<偏差类型, 角色>} 之类的兜底表。
 *
 * <h3>fail-closed（铁律 §2.4）</h3>
 * 端口<b>未装配</b>（无 bean）与装配了但<b>裁决链路不可用</b>都走
 * {@link ReviewRoleDecision#unavailable} ⇒ 上层不建任务并回 503，禁默认放行、禁伪 200。
 *
 * <p>late-binding：宿主装配 REST 适配器（{@code SecurityPolicyReviewRoleDecider}）即可接线，
 * 与 {@code ContextSecurityTrimmer} 同纪律。</p>
 */
public interface BacktestReviewRoleDecider {

    /** 本次裁决能否给出可信复审角色。 */
    ReviewRoleDecision decide(ReviewRoleQuery query);

    /**
     * @param action        裁决动作名（进 security-engine {@code input.action}；不是权限白名单，
     *                      认知侧不据此推断任何角色）
     * @param breachedItems 越限项清单（必带，供裁决与任务参数同源）
     * @param measuredValues 实测值摘要（与 {@code breachedItems} 一一对应，禁两处各算一遍）
     */
    record ReviewRoleQuery(String action,
                           String evaluationId,
                           String runId,
                           String scenarioId,
                           String tenantId,
                           String orgId,
                           List<String> breachedItems,
                           List<String> measuredValues,
                           Map<String, String> thresholdSources) {
    }

    /**
     * @param allowed     裁决是否放行本次复审指派
     * @param unavailable 裁决链路是否不可用（true ⇒ 上层 503，与 policy DENY 的 403 严格可分，DG-6）
     * @param reviewerRole 裁决透出的复审角色（禁本地推断）
     * @param decisionRef 裁决定位（{@code policyRef} + {@code inputDigest} 等，可复算锚点）
     * @param reason      结论原因（fail-loud，禁空串）
     */
    record ReviewRoleDecision(boolean allowed,
                              boolean unavailable,
                              String reviewerRole,
                              String decisionRef,
                              String reason) {

        public static ReviewRoleDecision allow(String role, String decisionRef, String reason) {
            return new ReviewRoleDecision(true, false, role, decisionRef, reason);
        }

        public static ReviewRoleDecision deny(String decisionRef, String reason) {
            return new ReviewRoleDecision(false, false, null, decisionRef, reason);
        }

        public static ReviewRoleDecision unavailable(String decisionRef, String reason) {
            return new ReviewRoleDecision(false, true, null, decisionRef, reason);
        }
    }
}
