package com.chinacreator.gzcm.common.security.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GuardrailDecisionContract} 的限流义务读数面（#142/B4-3d-4，差距 D-B4-21）。
 *
 * <p><b>为什么测试里保留独立字面量</b>（与 {@code DataScopeContractTest} 同口径）：本类断言的
 * 正是<b>单源本身的字面量取值</b>。若期望值也从 {@link GuardrailDecisionContract} 取，
 * 把 {@code "rateLimitKey"} 改成 {@code "rate_limit_key"} 就测不出来 —— 生产者与断言同步漂移
 * ＝假绿。这里的字面量是与 {@code ecos-docker/opa-policies/data_access.rego} 的
 * {@code {"obligation": "rateLimit", "rateLimitKey": sprintf("ai.tool.%s", …)}} 逐字对照的独立见证；
 * 该逐字对照本身由 {@code RateLimitObligationConsumerGateTest} 在扫描侧再钉一次。</p>
 *
 * <p><b>读数≠判定</b>：本类只把策略给的义务形态翻译成「声明了没有 + 键原文」，
 * 不产生"放行/拒绝"结论。因此"声明了但键缺失"必须返回 {@code declared=true, key=null}
 * 而不是退化成"没声明"—— 后者会把消费方的 FAIL_CLOSED 洗成 PASS（D-B4-21 的原形态）。</p>
 */
class GuardrailRateLimitDeclarationTest {

    private static Map<String, Object> obligation(String type, Object key) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("obligation", type);
        if (key != null) {
            m.put("rateLimitKey", key);
        }
        return m;
    }

    @Test
    @DisplayName("键面取值逐字钉：与 data_access.rego 的 rate_obligations 字面量一致")
    void keyLiteralsMatchPolicySide() {
        assertEquals("obligations", GuardrailDecisionContract.KEY_OBLIGATIONS);
        assertEquals("obligation", GuardrailDecisionContract.KEY_OBLIGATION);
        assertEquals("rateLimit", GuardrailDecisionContract.OBLIGATION_RATE_LIMIT);
        assertEquals("rateLimitKey", GuardrailDecisionContract.KEY_RATE_LIMIT_KEY);
        assertEquals("ai.tool.", GuardrailDecisionContract.RATE_LIMIT_KEY_PREFIX);
    }

    @Test
    @DisplayName("策略没发义务 — null／空段／无关类型一律 undeclared（不得凭空计额）")
    void absentObligationsAreUndeclared() {
        assertFalse(GuardrailDecisionContract.readRateLimitDeclaration(null).declared());
        assertFalse(GuardrailDecisionContract.readRateLimitDeclaration(List.of()).declared());
        assertFalse(GuardrailDecisionContract.readRateLimitDeclaration(Map.of()).declared());
        assertNull(GuardrailDecisionContract.readRateLimitDeclaration(null).key());
    }

    @Test
    @DisplayName("rego 原文形态（obligations 为数组）— 声明与键原文都读到")
    void parsesPolicyArrayShape() {
        GuardrailDecisionContract.RateLimitDeclaration d =
                GuardrailDecisionContract.readRateLimitDeclaration(
                        List.of(obligation("rateLimit", "ai.tool.u-pj")));

        assertTrue(d.declared());
        assertEquals("ai.tool.u-pj", d.key());
    }

    @Test
    @DisplayName("对象形态载体（PRD 表述）— 同样声明成立，禁按载体形态丢义务")
    void parsesSingleObjectShape() {
        GuardrailDecisionContract.RateLimitDeclaration d = GuardrailDecisionContract
                .readRateLimitDeclaration(obligation("rateLimit", "ai.tool.u-pj"));

        assertTrue(d.declared());
        assertEquals("ai.tool.u-pj", d.key());
    }

    @Test
    @DisplayName("非限流义务项不得被误读成限流（mask／watermark／DENIED 各自一类）")
    void ignoresOtherObligationClasses() {
        List<Object> others = List.of(
                obligation("mask", null),
                obligation("watermark", "ai.tool.x"),
                obligation("DENIED", "ai.tool.x"),
                Map.of("column", "phone", "rule", "phone"));

        for (Object raw : others) {
            assertFalse(GuardrailDecisionContract.readRateLimitDeclaration(List.of(raw)).declared(),
                    "非 rateLimit 义务被判成限流: " + raw);
        }
    }

    @Test
    @DisplayName("声明了限流但键缺失 — declared=true 且 key=null（禁退化成未声明）")
    void declarationWithoutKeyStaysDeclared() {
        GuardrailDecisionContract.RateLimitDeclaration d =
                GuardrailDecisionContract.readRateLimitDeclaration(List.of(obligation("rateLimit", null)));

        assertTrue(d.declared(),
                "D-B4-21 的丢义务形态：义务类型命中但键缺失就报「没声明」，消费方会静默放行");
        assertNull(d.key());
    }

    @Test
    @DisplayName("空白键原文按 String.valueOf 保留 — 键可用性由消费方判，读数面不擅自清洗")
    void blankKeyIsReturnedAsIs() {
        GuardrailDecisionContract.RateLimitDeclaration d =
                GuardrailDecisionContract.readRateLimitDeclaration(
                        List.of(obligation("rateLimit", "   ")));

        assertTrue(d.declared());
        assertEquals("   ", d.key(), "读数面把空白键洗成 null 会让 FAIL_CLOSED 退化成 KEY_MISSING 之外的分类");
    }

    @Test
    @DisplayName("多条义务取首条限流 — 与策略恒单条的现形态兼容且不依赖顺序")
    void firstRateLimitItemWins() {
        GuardrailDecisionContract.RateLimitDeclaration d = GuardrailDecisionContract
                .readRateLimitDeclaration(List.of(
                        obligation("mask", null),
                        obligation("rateLimit", "ai.tool.u-a"),
                        obligation("rateLimit", "ai.tool.u-b")));

        assertTrue(d.declared());
        assertEquals("ai.tool.u-a", d.key());
    }

    @Test
    @DisplayName("不可解析载体（字符串／数字）— undeclared，判定权留给裁决闸而非本类")
    void unparsableCarrierIsUndeclared() {
        assertFalse(GuardrailDecisionContract.readRateLimitDeclaration("rateLimit").declared());
        assertFalse(GuardrailDecisionContract.readRateLimitDeclaration(7).declared());
    }

    @Test
    @DisplayName("undeclared() 工厂位是空态单点 — declared=false 且 key=null")
    void undeclaredFactoryCarriesNoKey() {
        GuardrailDecisionContract.RateLimitDeclaration d =
                GuardrailDecisionContract.RateLimitDeclaration.undeclared();

        assertFalse(d.declared());
        assertNull(d.key());
    }
}
