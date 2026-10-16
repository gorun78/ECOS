package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ToolObligations} 读第三类义务（{@code rateLimit}）的形态面
 * （#142/B4-3d-4，差距 D-B4-21 的<b>丢义务</b>半句收口）。
 *
 * <p><b>本类钉住的具体回归</b>：改动前 {@code fromEvaluateData} 只找 mask/rowFilter，
 * 一条"只签发限流义务"的应答会命中末尾的 {@code return NONE} —— 于是消费方拿到的是
 * "策略什么也没说"，限流腿静默不执行。这与"生产侧零消费方"是同一个缺口的两半：
 * 光加消费方而不修解析，消费方永远看不到义务。因此这里每条都是针对<b>仅限流义务</b>
 * 与<b>限流+其它义务共存</b>两种形态。</p>
 *
 * <p>用例放在 ai-engine-impl：ai-engine-api 侧无测试树（pom 无 test 依赖，
 * 为其新增依赖属基线变更），而本类的判据是纯解析行为，不需要容器。</p>
 */
class ToolObligationsRateLimitTest {

    private static Map<String, Object> rateItem(String key) {
        return key == null
                ? Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT)
                : Map.of(GuardrailDecisionContract.KEY_OBLIGATION,
                        GuardrailDecisionContract.OBLIGATION_RATE_LIMIT,
                        GuardrailDecisionContract.KEY_RATE_LIMIT_KEY, key);
    }

    private static ToolObligations fromObligations(Object obligationsRaw) {
        return ToolObligations.fromEvaluateData(
                Map.of(GuardrailDecisionContract.KEY_OBLIGATIONS, obligationsRaw));
    }

    @Test
    @DisplayName("仅限流义务（rego 原文数组形态）不得再读成「无义务」—— D-B4-21 丢义务半句的反例")
    void loneRateObligationIsNotParsedAsNone() {
        ToolObligations obligations = fromObligations(List.of(rateItem("ai.tool.u-pj")));

        assertFalse(obligations.isEmpty(), "仅限流义务被读成空义务 ⇒ 消费方永远不会执行");
        assertSame(ToolObligations.none(), ToolObligations.fromEvaluateData(Map.of()),
                "对照项：真的无义务时仍是 NONE（本类不得把空义务也变成非空）");
        assertTrue(obligations.declaresRateLimit());
        assertEquals("ai.tool.u-pj", obligations.rateLimitKey());
        assertTrue(obligations.mask().isEmpty(), "限流义务不应被解析成脱敏列");
        assertNull(obligations.rowFilter());
    }

    @Test
    @DisplayName("mask 与 rateLimit 共存 — 两类义务同时可读，禁先到先得把另一类丢掉")
    void maskAndRateLimitCoexist() {
        Object obligationsRaw = List.of(
                Map.of(GuardrailDecisionContract.KEY_OBLIGATION, GuardrailDecisionContract.OBLIGATION_MASK,
                        "column", "phone", "rule", "phone"),
                rateItem("ai.tool.u-pj"));

        ToolObligations obligations = fromObligations(obligationsRaw);

        assertEquals(1, obligations.mask().size());
        assertEquals("phone", obligations.mask().get(0).column());
        assertTrue(obligations.declaresRateLimit());
        assertEquals("ai.tool.u-pj", obligations.rateLimitKey());
    }

    @Test
    @DisplayName("rowFilter 与 rateLimit 共存 — 结果段与调用段义务互不遮蔽")
    void rowFilterAndRateLimitCoexist() {
        ToolObligations obligations = fromObligations(List.of(
                Map.of("rowFilter", "dept = 'd01'"), rateItem("ai.tool.u-pj")));

        assertEquals("dept = 'd01'", obligations.rowFilter());
        assertTrue(obligations.declaresRateLimit());
        assertFalse(obligations.isEmpty());
    }

    @Test
    @DisplayName("声明了限流但键缺失 — declaresRateLimit 为真、键为 null（三态不折叠的解析侧）")
    void declarationWithoutKeyKeepsDeclaredFlag() {
        ToolObligations obligations = fromObligations(List.of(rateItem(null)));

        assertTrue(obligations.declaresRateLimit(),
                "键缺失属形态不合契约（FAIL_CLOSED 面），不得退化成未声明（PASS 面）");
        assertNull(obligations.rateLimitKey());
        assertFalse(obligations.isEmpty(), "义务存在但不可用仍要进入消费方，否则又被静默丢弃");
    }

    @Test
    @DisplayName("无义务载体 — NONE、isEmpty 为真、不声明限流")
    void absentObligationsStayNone() {
        assertTrue(fromObligations(List.of()).isEmpty());
        assertFalse(fromObligations(List.of()).declaresRateLimit());
        assertFalse(fromObligations(List.of(Map.of("obligation", "watermark"))).declaresRateLimit());
        assertFalse(ToolObligations.none().declaresRateLimit());
    }

    @Test
    @DisplayName("台账摘要记限流义务的存在与键可用性，不回显键原文（自造键不得进审计面）")
    void summaryRecordsRateLimitWithoutEchoingKey() {
        List<Map<String, Object>> summary = fromObligations(List.of(rateItem("ai.tool.u-pj"))).summary();

        Map<String, Object> item = summary.stream()
                .filter(o -> "RATE_LIMIT".equals(o.get("type")))
                .findFirst().orElseThrow(() -> new AssertionError("摘要未记限流义务: " + summary));
        assertEquals(Boolean.TRUE, item.get("declared"));
        assertEquals(Boolean.TRUE, item.get("keyUsable"));
        assertFalse(item.containsKey("rateLimitKey"), "审计摘要只记形态，禁把义务键原文写进台账");
        assertFalse(String.valueOf(summary).contains("ai.tool."),
                "摘要整段都不得含键原文（F06-03 要点 2：只带摘要与 ID）");
    }

    @Test
    @DisplayName("不可用键在摘要里显式记 keyUsable=false（区别于根本没这条义务）")
    void summaryMarksUnusableKey() {
        List<Map<String, Object>> summary = fromObligations(List.of(rateItem(null))).summary();

        Map<String, Object> item = summary.stream()
                .filter(o -> "RATE_LIMIT".equals(o.get("type")))
                .findFirst().orElseThrow(() -> new AssertionError("摘要未记限流义务: " + summary));
        assertEquals(Boolean.FALSE, item.get("keyUsable"));
    }

    @Test
    @DisplayName("无义务时摘要不含 RATE_LIMIT 项（禁凭空造出义务）")
    void summaryOmitsRateLimitWhenUndeclared() {
        assertEquals(List.of(), ToolObligations.none().summary());
        assertFalse(String.valueOf(fromObligations(List.of()).summary()).contains("RATE_LIMIT"));
    }

    @Test
    @DisplayName("旧三参构造形态保持编译面且不带限流声明（只增不改纪律）")
    void legacyThreeArgShapeCarriesNoRateLimit() {
        ToolObligations legacy = new ToolObligations(
                List.of(new ToolObligations.Mask("phone", "phone")), null, Map.of());

        assertEquals(1, legacy.mask().size());
        assertFalse(legacy.declaresRateLimit());
        assertNull(legacy.rateLimitKey());
    }

    @Test
    @DisplayName("toString 暴露三类义务计数 — 日志侧能一眼看出限流是否签发")
    void toStringExposesRateLimitPresence() {
        assertTrue(fromObligations(List.of(rateItem("ai.tool.u-pj"))).toString().contains("rateLimit=true"));
        assertTrue(ToolObligations.none().toString().contains("rateLimit=false"));
    }
}
