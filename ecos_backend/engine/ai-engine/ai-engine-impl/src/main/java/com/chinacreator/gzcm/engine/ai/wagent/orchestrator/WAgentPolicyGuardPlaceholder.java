package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.AutomationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 策略护栏占位实现（<b>接口位专供 #131/R3-A 接真实 security 通道</b>）。
 *
 * <p>占位姿态（F10-22 fail-closed 口径）：</p>
 * <ul>
 *   <li>{@code prePlan}：ALLOW——<b>纯透传占位</b>，pre-plan 权限闸的真实裁决
 *       （ABAC/OPA 三通道）由 #131 替换；PlanValidator 的 PERMISSION 检查即消费本钩子。</li>
 *   <li>{@code preStep}：L0（只读）ALLOW；<b>≥L1 一律 DENY</b>——真实策略源缺席时
 *       L1+ 步骤不可放行（"安全卡未接入 = DENY"，禁伪称已裁决放行）。</li>
 *   <li>{@code postStep}/{@code postOutput}：ALLOW 无副作用（脱敏义务登记随 #131 落）。</li>
 *   <li>{@code preCommit}：一律 DENY——生产写通道在 #131 Candidate 发布闭环 +
 *       F10-17 唯一写通道落地前保持关闭。</li>
 * </ul>
 *
 * <p>#131 提供真实 bean 后，本类经 {@code @ConditionalOnMissingBean}-式缺席退位由装配侧决定；
 * 编排侧一律按 {@link WAgentPolicyGuardPort} 接口消费，不留 instanceof 判定。</p>
 */
@Component
public class WAgentPolicyGuardPlaceholder implements WAgentPolicyGuardPort {

    private static final Logger log = LoggerFactory.getLogger(WAgentPolicyGuardPlaceholder.class);

    /** 占位规则号（审计可区分"占位放行/占位拒绝"与真实裁决）。 */
    public static final String RULE_PLACEHOLDER = "WA-POLICY-PLACEHOLDER";

    @Override
    public PolicyDecision prePlan(PolicyCheckContext ctx) {
        // 占位透传：#131 在此接缝接 pre-plan 真实策略裁决
        log.debug("[F10-12] pre-plan 占位放行（真实裁决属 #131）: runId={}", ctx.runId());
        return PolicyDecision.allow(RULE_PLACEHOLDER);
    }

    @Override
    public PolicyDecision preStep(PolicyCheckContext ctx) {
        int level = ctx.automationLevel() == null ? 0 : ctx.automationLevel();
        if (level <= AutomationLevel.L0.level()) {
            return PolicyDecision.allow(RULE_PLACEHOLDER);
        }
        return PolicyDecision.deny(RULE_PLACEHOLDER,
                "策略源未接入（占位 fail-closed）：≥L1 步骤暂拒，待 #131 实装");
    }

    @Override
    public PolicyDecision postStep(PolicyCheckContext ctx) {
        return PolicyDecision.allow(RULE_PLACEHOLDER);
    }

    @Override
    public PolicyDecision postOutput(PolicyCheckContext ctx) {
        return PolicyDecision.allow(RULE_PLACEHOLDER);
    }

    @Override
    public PolicyDecision preCommit(PolicyCheckContext ctx) {
        return PolicyDecision.deny(RULE_PLACEHOLDER,
                "生产写通道未开（占位 fail-closed）：唯一写通道随 F10-17/#131 落地");
    }
}
