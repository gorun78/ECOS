package com.chinacreator.gzcm.ai.wagent.guard;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PolicyCheckpoint;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.PolicyEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * 分册10 F10-22 · Policy Guard 五检查点预检（pre-plan / pre-step / post-step / pre-commit / pre-output）。
 *
 * <p><b>fail-closed</b>：{@code available=false} 且 {@code level >= 1} ⇒ {@link PolicyEffect#DENY}
 * （security 不可用一律拒绝，铁律 :2.4 #5）。<b>DENY 消息不泄漏存在性</b>：reason 固定
 * {@code "policy denied (no further detail)"}，绝不携带资源名。</p>
 *
 * <p>post-step / pre-output 额外回传义务清单（mask / rowFilter）——<b>本类只返回、不执行</b>
 * （脱敏由 security 侧落地，Agent 不实现脱敏）。</p>
 */
public final class PolicyGuard {

    /** DENY 固定话术（不泄漏资源存在性）。 */
    public static final String DENY_REASON = "policy denied (no further detail)";

    /** 评估上下载（security 侧消费）。 */
    public record PolicyTarget(String runId, String questionId, String tool, int level, String scopeHash) {}

    public record EvaluationContext(String caller, String tenantId, String scopeHash, int level, String runId) {}

    /** 安全裁决四值。 */
    public enum Verdict { ALLOW, ALLOW_WITH_MASK, REQUIRE_APPROVAL, DENY }

    /** 安全客户端（wiring 注入）。 */
    public interface SecurityClient {
        Verdict evaluate(String ruleSet, EvaluationContext ctx);
    }

    /** 义务清单（mask/rowFilter 等签名，本类不执行）。 */
    public record Obligations(List<String> mask, List<String> rowFilter) {}

    /** 评估结果：effect + ruleId + reason。 */
    public record EvaluateResponse(PolicyEffect effect, String ruleId, String reason) {}

    private static final String RULE_SET = "wagent.PolicyGuard";

    public EvaluateResponse evaluate(PolicyCheckpoint cp, PolicyTarget target, int runLevel,
                                     SecurityClient security, boolean available) {
        int level = target == null ? runLevel : target.level();

        // fail-closed：security 不可用 ⇒ level>=1 一律 DENY。
        if (!available) {
            if (level >= 1) {
                return new EvaluateResponse(PolicyEffect.DENY, "SEC-UNAVAILABLE", DENY_REASON);
            }
            return new EvaluateResponse(PolicyEffect.ALLOW, "SEC-UNAVAILABLE", "security unavailable, level 0 passthrough");
        }

        EvaluationContext ctx = new EvaluationContext(
                target == null ? null : target.runId(),
                null,
                target == null ? null : target.scopeHash(),
                Math.max(level, runLevel),
                target == null ? null : target.runId());
        Verdict v = security.evaluate(RULE_SET, ctx);
        return switch (v) {
            case ALLOW            -> new EvaluateResponse(PolicyEffect.ALLOW, RULE_SET, "allowed");
            case ALLOW_WITH_MASK  -> new EvaluateResponse(PolicyEffect.ALLOW_WITH_MASK, RULE_SET, "allowed with mask");
            case REQUIRE_APPROVAL -> new EvaluateResponse(PolicyEffect.REQUIRE_APPROVAL, RULE_SET, "approval required");
            case DENY             -> new EvaluateResponse(PolicyEffect.DENY, RULE_SET, DENY_REASON);
        };
    }

    /**
     * post-step & pre-output 义务清单回传（本类**不执行**），供上游 security 落地。
     * mask/rowFilter 均可能为空；非空时调用方须经 security-engine 才落。
     */
    public Obligations obligations(PolicyCheckpoint cp, boolean maskRequired, boolean rowFilterRequired) {
        boolean postPhase = cp == PolicyCheckpoint.POST_STEP || cp == PolicyCheckpoint.PRE_OUTPUT;
        List<String> mask = (postPhase && maskRequired) ? new ArrayList<>(List.of("MASK")) : List.of();
        List<String> rowFilter = (postPhase && rowFilterRequired) ? new ArrayList<>(List.of("ROW_FILTER")) : List.of();
        return new Obligations(mask, rowFilter);
    }
}
