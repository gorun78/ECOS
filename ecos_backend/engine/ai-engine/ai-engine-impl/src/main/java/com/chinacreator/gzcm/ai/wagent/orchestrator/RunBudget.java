package com.chinacreator.gzcm.ai.wagent.orchestrator;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 分册10 F10-11 · Run 预算（Step 60 / LLM 40 / 同步 60s / 异步 30min / 重规划 3 / 补齐 3）。
 *
 * <p>来源：sysman 配置单源 {@code config_group='wagent.budget'}（生产链路），
 * 本类默认值 = 一份<b>不可覆盖</b>的租户上限（租户只能在下方下调，禁上调，
 * 见 {@code RunBudgetTest#stepCountOver60Terminates}）。
 * 越界即触发 E-WA-BUDGET HTTP 429 早退。</p>
 */
public final class RunBudget {

    private RunBudget() {}

    /** 默认上限（附件 §2.2 冻结）：任何租户不可超过这些值。 */
    public record Limits(int maxStep, int maxLlmCall, long syncTimeoutMs, long asyncTimeoutMs,
                          int maxReplan, int maxRefillRound) {
        public static Limits defaults() {
            return new Limits(60, 40, 60L * 1000L, 30L * 60L * 1000L, 3, 3);
        }
    }

    /** 消耗累加器：每 Step 结束写一次 metrics_json（不双写本地状态 = 无状态 orchestrator）。 */
    public record Consumption(int step, int llmCalls, long syncMs, long asyncMs, int replan, int refill,
                               long tokensIn, long tokensOut, BigDecimal cost) {
        public static Consumption zero() {
            return new Consumption(0, 0, 0, 0, 0, 0, 0L, 0L, BigDecimal.ZERO);
        }
    }

    /** 硬约束：所有硬顶在 defaults，不能上调。 */
    public static boolean withinLimit(Limits limits, Consumption c) {
        if (limits == null || c == null) return false;
        if (c.step() > limits.maxStep()) return false;
        if (c.llmCalls() > limits.maxLlmCall()) return false;
        if (c.replan() > limits.maxReplan()) return false;
        if (c.refill() > limits.maxRefillRound()) return false;
        if (c.syncMs() > limits.syncTimeoutMs()) return false;
        if (c.asyncMs() > limits.asyncTimeoutMs()) return false;
        return true;
    }

    /** 租户侧若给高于 defaults 的自定义，抑制回 defaults。 */
    public static Limits enforceTenantDowngrade(Limits provided) {
        if (provided == null) return Limits.defaults();
        Limits d = Limits.defaults();
        return new Limits(
                Math.min(provided.maxStep(), d.maxStep()),
                Math.min(provided.maxLlmCall(), d.maxLlmCall()),
                Math.min(provided.syncTimeoutMs(), d.syncTimeoutMs()),
                Math.min(provided.asyncTimeoutMs(), d.asyncTimeoutMs()),
                Math.min(provided.maxReplan(), d.maxReplan()),
                Math.min(provided.maxRefillRound(), d.maxRefillRound()));
    }

    /** 度量回读例：给前端/审计按计划返回「预算十六宫格」以 E-* 前置。 */
    public static Map<String, Object> measure(Consumption c, Limits limits) {
        Map<String, Object> m = new HashMap<>();
        m.put("stepUsed", c.step() + "/" + limits.maxStep());
        m.put("llmUsed", c.llmCalls() + "/" + limits.maxLlmCall());
        m.put("syncMs", c.syncMs() + "/" + limits.syncTimeoutMs());
        m.put("asyncMs", c.asyncMs() + "/" + limits.asyncTimeoutMs());
        m.put("replanUsed", c.replan() + "/" + limits.maxReplan());
        m.put("refillUsed", c.refill() + "/" + limits.maxRefillRound());
        m.put("ok", withinLimit(limits, c));
        return m;
    }
}
