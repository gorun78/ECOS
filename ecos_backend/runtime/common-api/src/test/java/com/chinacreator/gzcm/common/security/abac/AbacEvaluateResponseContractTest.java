package com.chinacreator.gzcm.common.security.abac;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract.Verdict;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbacEvaluateResponseContract} 的<b>读数语义</b>单测（#142/B4-3e-4，收口差距 D-B4-37
 * 与同批新登记 <b>D-B4-39</b>）。
 *
 * <p>静态门禁 {@code AbacEvaluateResponseSingleSourceTest} 只钉"键面没被另写一遍"；
 * 本类钉"三态翻译本身对不对"。两支缺一不可 —— 只有本类时，下一版把消费方改回内联
 * {@code get("result")} 照样全绿（那正是 D-B4-39 的发生形态）；只有静态门禁时，
 * 键面单源但把"护栏没说话"翻成 DENY 也照样全绿（那正是 §4.7.2 禁止的折叠）。</p>
 *
 * <p><b>夹具口径</b>：键名一律取自单源常量（常量漂移即本类变红），形态分三代 ——
 * 现契约（{@code PolicyEngineController} 摊平 {@code data}）、历史别名（裸布尔
 * {@code result}）、嵌套（{@code SecurityPolicyController} 的 {@code result:{…}}）。
 * 三代都测，是因为三个恒 DENY 消费者（kb 导航写闸／本体操作授权／workspace 导出咽喉）
 * 收口前只认第二代，而现网应答只有第一代。</p>
 */
class AbacEvaluateResponseContractTest {

    /** 现契约形态：{@code data} 段摊平，裁决键＝{@code allow}。 */
    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ── 一、现契约形态（生产侧实测只发这一代） ──────────────────────────────

    @Test
    @DisplayName("现契约布尔形态：allow=true 放行 / allow=false 才是策略拒绝")
    void currentContractBooleanForm() {
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, true)));
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, false)));
        // 生产侧真实形态（allow + policyState + source=opa 同时在位）
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(data(
                "policy", "rbac",
                GuardrailDecisionContract.KEY_ALLOW, true,
                GuardrailDecisionContract.KEY_POLICY_STATE, GuardrailDecisionContract.STATE_ALLOW,
                AbacEvaluateResponseContract.KEY_SOURCE, AbacEvaluateResponseContract.VALUE_SOURCE_OPA)));
    }

    @Test
    @DisplayName("字符串布尔形态：trim + 大小写归一，词表未命中即 UNEVALUATED（禁默认放行）")
    void stringBooleanVocabulary() {
        for (String allow : List.of("true", "TRUE", " allow ", "Permit")) {
            assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(
                    data(GuardrailDecisionContract.KEY_ALLOW, allow)), "放行词: " + allow);
        }
        for (String deny : List.of("false", "FALSE", " deny", "DENIED")) {
            assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                    data(GuardrailDecisionContract.KEY_ALLOW, deny)), "拒绝词: " + deny);
        }
        // 不可识别取值不得被兜底成放行，也不得冒充策略拒绝
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, "maybe")));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, 1)));
    }

    // ── 二、历史别名形态（收口前三个消费者只认这一代 ⇒ 恒 DENY） ────────────

    @Test
    @DisplayName("历史别名 result：布尔真/假各映射 ALLOW/DENY，仅在 allow 缺失时兼容读")
    void legacyAliasBooleanForm() {
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_RESULT_LEGACY, true)));
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_RESULT_LEGACY, false)));
        // allow 在位时优先读 allow，别名不得反向覆盖
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, false,
                        AbacEvaluateResponseContract.KEY_RESULT_LEGACY, true)));
    }

    @Test
    @DisplayName("D-B4-39 复现夹具：现契约应答喂给只认 result 的旧读法必得 DENY")
    void legacyOnlyReaderWouldSilentlyDenyOnCurrentContract() {
        Map<String, Object> current = data(GuardrailDecisionContract.KEY_ALLOW, true);
        // 旧读法（kb／本体／workspace 收口前的三处形态）：Boolean.TRUE.equals(data.get("result"))
        Object legacyRaw = current.get(AbacEvaluateResponseContract.KEY_RESULT_LEGACY);
        assertFalse(Boolean.TRUE.equals(legacyRaw),
                "该夹具已不再复现「只认别名即恒 DENY」，D-B4-39 的登记依据随之失效");
        // 同一份应答经单源读数：放行
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(current));
    }

    @Test
    @DisplayName("嵌套 envelope（SecurityPolicyController 的 result:{整份裁决}）只下钻一层")
    void nestedResultEnvelopeUnwrapsOneLevel() {
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(
                data("input", data("action", "export"),
                        AbacEvaluateResponseContract.KEY_RESULT_LEGACY,
                        data(GuardrailDecisionContract.KEY_ALLOW, true))));
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_RESULT_LEGACY,
                        data(GuardrailDecisionContract.KEY_ALLOW, false,
                                GuardrailDecisionContract.KEY_POLICY_STATE,
                                GuardrailDecisionContract.STATE_DENY))));
        // 内层没有裁决键 ⇒ 不下钻（递归放宽会把"形态异常"读成放行）
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_RESULT_LEGACY, data("note", "x"))));
    }

    // ── 三、显式裁决串键优先于布尔键 ────────────────────────────────────────

    @Test
    @DisplayName("显式 decision/verdict 词表命中即结论；未命中即 UNEVALUATED，且不回退布尔键")
    void explicitDecisionKeysTakePrecedence() {
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_DECISION, "PERMIT")));
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_VERDICT, "reject")));
        assertEquals(Verdict.DENY, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_DECISION, "DENY",
                        GuardrailDecisionContract.KEY_ALLOW, true)));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(AbacEvaluateResponseContract.KEY_DECISION, "ASK",
                        GuardrailDecisionContract.KEY_ALLOW, true)),
                "显式裁决键存在但取值不认识时禁回退到布尔键放行（否则词表漂移＝静默放宽）");
    }

    // ── 四、护栏态串优先（不可用 ≠ 无权限，§4.7.2） ─────────────────────────

    @Test
    @DisplayName("policyState 未评估态优先于 allow 布尔：POLICY_UNDEFINED/OPA_UNAVAILABLE ⇒ UNEVALUATED")
    void unevaluatedPolicyStateBeatsBoolean() {
        for (String state : List.of(GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                GuardrailDecisionContract.STATE_UNAVAILABLE)) {
            assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                    data(GuardrailDecisionContract.KEY_ALLOW, false,
                            GuardrailDecisionContract.KEY_POLICY_STATE, state)), "态: " + state);
            assertFalse(AbacEvaluateResponseContract.isExplicitAllow(
                    data(GuardrailDecisionContract.KEY_ALLOW, false,
                            GuardrailDecisionContract.KEY_POLICY_STATE, state)));
        }
        // 同态串配 allow=true 属形态矛盾，仍按"护栏没说话"处理（放行权不在状态串之外）
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, true,
                        GuardrailDecisionContract.KEY_POLICY_STATE,
                        GuardrailDecisionContract.STATE_UNAVAILABLE)));
    }

    @Test
    @DisplayName("兜底来源识别：source=fallback 或带 opaError ⇒ UNEVALUATED，禁算策略 DENY")
    void fallbackSourceIsUnavailableNotDeny() {
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(data(
                GuardrailDecisionContract.KEY_ALLOW, false,
                AbacEvaluateResponseContract.KEY_SOURCE,
                AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK,
                AbacEvaluateResponseContract.KEY_FALLBACK,
                AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN)));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data(GuardrailDecisionContract.KEY_ALLOW, false,
                        AbacEvaluateResponseContract.KEY_OPA_ERROR, "connection refused")));
        // 正常来源不受影响
        assertEquals(Verdict.ALLOW, AbacEvaluateResponseContract.verdictOf(data(
                GuardrailDecisionContract.KEY_ALLOW, true,
                AbacEvaluateResponseContract.KEY_SOURCE,
                AbacEvaluateResponseContract.VALUE_SOURCE_OPA)));
    }

    // ── 五、缺省与容错：一律 fail-closed 且归入 UNEVALUATED ─────────────────

    @Test
    @DisplayName("空/非 Map/无裁决键 ⇒ UNEVALUATED；消费方据此报 503/FAIL_CLOSED 而非 403")
    void missingOrMalformedIsUnevaluatedNotDeny() {
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(null));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf("DENY"));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(List.of(1, 2)));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(data()));
        assertEquals(Verdict.UNEVALUATED, AbacEvaluateResponseContract.verdictOf(
                data("policy", "rbac", "obligations", List.of())));
        assertFalse(AbacEvaluateResponseContract.isExplicitAllow(null));
        assertFalse(AbacEvaluateResponseContract.isExplicitAllow(
                data(GuardrailDecisionContract.KEY_ALLOW, false)));
    }

    @Test
    @DisplayName("isExplicitAllow 只在 ALLOW 为真：DENY 与 UNEVALUATED 都不得放行")
    void explicitAllowIsFailClosed() {
        assertTrue(AbacEvaluateResponseContract.isExplicitAllow(
                data(GuardrailDecisionContract.KEY_ALLOW, true)));
        assertFalse(AbacEvaluateResponseContract.isExplicitAllow(
                data(GuardrailDecisionContract.KEY_ALLOW, false)));
        assertFalse(AbacEvaluateResponseContract.isExplicitAllow(
                data(GuardrailDecisionContract.KEY_ALLOW, true,
                        AbacEvaluateResponseContract.KEY_SOURCE,
                        AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK)));
    }

    @Test
    @DisplayName("键面常量与护栏单源同值：KEY_ALLOW 是引用不是重定义")
    void keyConstantsAlignWithGuardrailContract() {
        assertEquals(GuardrailDecisionContract.KEY_ALLOW, AbacEvaluateResponseContract.KEY_ALLOW);
        assertEquals("fallback", AbacEvaluateResponseContract.VALUE_SOURCE_FALLBACK);
        assertEquals("DENY_OPA_DOWN", AbacEvaluateResponseContract.VALUE_FALLBACK_DENY_OPA_DOWN);
    }
}
