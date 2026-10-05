package com.chinacreator.gzcm.ai.wagent.runstate;

import com.chinacreator.gzcm.ai.wagent.orchestrator.RunBudget;
import com.chinacreator.gzcm.ai.wagent.orchestrator.RunBudget.Consumption;
import com.chinacreator.gzcm.ai.wagent.orchestrator.RunBudget.Limits;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分册10 F10-11 Run 预算离线单测：Step 59 在 60 上限内、61 越界；
 * 租户上限只可下调不可上调（enforceTenantDowngrade 取 min）。
 */
class RunBudgetTest {

    /** F10-11：Consumption(59 steps, 30 llm, cost 50) 在默认上限（60/40/...）之内。 */
    @Test
    void stepSixtyInsideLimit() {
        Limits limits = Limits.defaults();
        Consumption inside = new Consumption(59, 30, 0L, 0L, 0, 0, 0L, 0L, new BigDecimal("50"));
        assertTrue(RunBudget.withinLimit(limits, inside), "Step 59 ≤ 60 应在预算内");

        // 边界等值（step 恰 = 60 / llm 恰 = 40）也在限内（> 才越界）。
        Consumption atEdge = new Consumption(60, 40, 0L, 0L, 0, 0, 0L, 0L, BigDecimal.ZERO);
        assertTrue(RunBudget.withinLimit(limits, atEdge), "恰好压线（step=60, llm=40）应判在限内");
    }

    /** F10-11：Consumption(61 steps, ...) 越过硬顶 60 ⇒ 预算判定失败（E-WA-BUDGET 早退前置）。 */
    @Test
    void stepSixtyOneOver() {
        Limits limits = Limits.defaults();
        Consumption over = new Consumption(61, 0, 0L, 0L, 0, 0, 0L, 0L, BigDecimal.ZERO);
        assertFalse(RunBudget.withinLimit(limits, over), "Step 61 > 60 必越界");

        // 其它维度越界同样 fail（LLM 41 > 40）。
        Consumption llmOver = new Consumption(0, 41, 0L, 0L, 0, 0, 0L, 0L, BigDecimal.ZERO);
        assertFalse(RunBudget.withinLimit(limits, llmOver));
    }

    /** F10-11：租户自定义上限只可下调不可上调（对每项取 min(租户, 默认)）。 */
    @Test
    void tenantCanOnlyLower() {
        Limits d = Limits.defaults();

        // 租户 step 上限 30 < 默认 60 ⇒ 保留 30。
        Limits lower = new Limits(30, d.maxLlmCall(), d.syncTimeoutMs(), d.asyncTimeoutMs(),
                d.maxReplan(), d.maxRefillRound());
        Limits appliedLower = RunBudget.enforceTenantDowngrade(lower);
        assertEquals(30, appliedLower.maxStep(), "租户下调 30 必须生效");

        // 租户 step 上限 100 > 默认 60 ⇒ 抑制回 60（禁上调）。
        Limits higher = new Limits(100, d.maxLlmCall(), d.syncTimeoutMs(), d.asyncTimeoutMs(),
                d.maxReplan(), d.maxRefillRound());
        Limits appliedHigher = RunBudget.enforceTenantDowngrade(higher);
        assertEquals(60, appliedHigher.maxStep(), "租户上调必须被抑制回默认 60");
    }
}
