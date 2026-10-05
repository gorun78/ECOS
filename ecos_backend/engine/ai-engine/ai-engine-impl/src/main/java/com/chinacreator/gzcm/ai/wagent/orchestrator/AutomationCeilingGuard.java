package com.chinacreator.gzcm.ai.wagent.orchestrator;

/**
 * 分册10 F10-21 · 自动化天花板守卫：三重上限取 min（工具最低要求 / run 天花板 / 租户上限）。
 *
 * <p>等级 L0..L3（int 0..3）。有效等级 = min(toolMin, runCeiling, tenantMax)。
 * 一旦有效等级 = L3(3)，返回附加标志 {@code requiresApprovalToken=true}——L3 无自动路径，
 * 强制 {@code approval_token} 非空（W AgentEnums.AutomationLevel javadoc 冻结）。</p>
 */
public final class AutomationCeilingGuard {

    private AutomationCeilingGuard() {}

    public static final int L3 = 3;

    /** 有效等级 + L3 审批标志（L3 ⇒ 必须携 approval_token 方可执行）。 */
    public record Effective(int level, boolean requiresApprovalToken) {}

    public static int effectiveLevel(int toolMin, int runCeiling, int tenantMax) {
        return Math.min(toolMin, Math.min(runCeiling, tenantMax));
    }

    /** 一句话：取三重 min，且 L3 ⇒ 强制审批 token（调用方可据此闸住无 token 的 L3 步）。 */
    public static Effective resolve(int toolMin, int runCeiling, int tenantMax) {
        int eff = effectiveLevel(toolMin, runCeiling, tenantMax);
        return new Effective(eff, eff >= L3);
    }
}
