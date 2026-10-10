package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PolicyEffect;

/**
 * 策略护栏接缝（详细设计-10 F10-12/§4.2/F10-22，REQ-WAG-12/22）。
 *
 * <p>本接口是编排核对 security 域的<b>唯一消费面</b>：PlanValidator 的权限检查、
 * Orchestrator 的 pre-plan/pre-step/pre-commit 钩子都从这里走。
 * <b>#131（R3-A）提供真实实现</b>（对接 security 引擎 OPA/ABAC 三通道 + fail-closed）；
 * 本任务只交付接口 + {@link WAgentPolicyGuardPlaceholder 占位实现}，
 * 编排核心经 {@code ObjectProvider} 注入本接口，#131 落地后无需改编排代码。</p>
 *
 * <p>裁决效果四值单源 = {@link PolicyEffect}（allow/allow_with_mask/require_approval/deny）；
 * 实现方禁抛裸异常穿越——不可用时的语义由实现方按 fail-closed 映射为 DENY（F10-22）。</p>
 */
public interface WAgentPolicyGuardPort {

    /**
     * 计划前钩子（§4.2：planning → plan_validated 之前）。
     * 返回 REQUIRE_APPROVAL ⇒ Run 转 {@code awaiting_approval}；DENY ⇒ Run {@code failed}。
     */
    PolicyDecision prePlan(PolicyCheckContext ctx);

    /** 步前钩子（executing 循环内，每 Step 调用一次；L1+ 需真实策略源）。 */
    PolicyDecision preStep(PolicyCheckContext ctx);

    /** 步后钩子（结果脱敏义务登记等；占位实现无副作用）。 */
    PolicyDecision postStep(PolicyCheckContext ctx);

    /** 输出后校验钩子（叙事数字纪律/掩码；F10-14 后置位）。 */
    PolicyDecision postOutput(PolicyCheckContext ctx);

    /** 提交前钩子（写通道唯一咽喉；F10-17 生产写必经）。 */
    PolicyDecision preCommit(PolicyCheckContext ctx);

    /** 钩子上下文：只带受控字段与引用，禁把业务明细原文塞入。 */
    record PolicyCheckContext(String tenantId, String runId, String stepKey, String stepType,
                              String toolName, Integer automationLevel, String sideEffect,
                              String planSource) {

        public static PolicyCheckContext ofPlan(String tenantId, String runId, String planSource) {
            return new PolicyCheckContext(tenantId, runId, null, null, null, null, null, planSource);
        }
    }

    /** 裁决结果：效果 + 命中规则号（落 V234 policy_effect/policy_rule_id）+ 诊断原因（只日志用）。 */
    record PolicyDecision(PolicyEffect effect, String ruleId, String reason) {

        public static PolicyDecision allow(String ruleId) {
            return new PolicyDecision(PolicyEffect.ALLOW, ruleId, null);
        }

        public static PolicyDecision deny(String ruleId, String reason) {
            return new PolicyDecision(PolicyEffect.DENY, ruleId, reason);
        }

        public static PolicyDecision requireApproval(String ruleId) {
            return new PolicyDecision(PolicyEffect.REQUIRE_APPROVAL, ruleId, null);
        }

        public boolean allowed() {
            return effect == PolicyEffect.ALLOW || effect == PolicyEffect.ALLOW_WITH_MASK;
        }
    }
}
