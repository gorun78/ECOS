package com.chinacreator.gzcm.engine.cognitive2.service.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.common.security.abac.AbacEvaluateResponseContract;
import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.cognitive2.service.backtest.BacktestReviewRoleDecider.ReviewRoleDecision;
import com.chinacreator.gzcm.engine.cognitive2.service.backtest.BacktestReviewRoleDecider.ReviewRoleQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 复审角色裁决的<b>不可用／策略拒绝分态</b>验收（#142/B4-3e-6，收口差距 <b>D-B4-41</b>）。
 *
 * <p><b>本类钉的是一条不变式，不是一组结论</b>：同一份输入（同 {@code policyRef} +
 * 同 {@code inputDigest}）下，「策略判了拒绝」与「护栏没说话」必须落成<b>两个不同的状态串</b>。
 * 收口前 {@code SecurityPolicyReviewRoleDecider#parse} 自己拼读数，最后一腿是
 * {@code !data.path("allow").asBoolean(false)} ⇒ 布尔键缺失时 {@code asBoolean} 取默认值
 * {@code false}，"护栏没说话"就此折叠成 deny，上层 403（{@code ROLE_DENIED_BY_POLICY}）；
 * 于是 security 侧的形态漂移在用户面表现为"你没权限"，运维面看不到任何依赖故障。
 * 折叠的后果不是保守，而是<b>把缺陷伪装成结论</b>（§4.7.2「不可用 ≠ 无权限」/ DG-6）。</p>
 *
 * <p>同批把折叠口径本身留作<b>反面基线</b>（{@link #naiveBooleanFoldIsTheDefectBeingClosed()}）：
 * 一旦有人把读数改回 {@code asBoolean(false)}，该夹具仍能证明"当时确实会折叠"，
 * 而不需要靠注释里的回忆。</p>
 */
class SecurityPolicyReviewRoleDeciderVerdictStateTest {

    private static final ObjectMapper M = new ObjectMapper();

    /** parse() 不触网（只用应答体），构造参数在此为占位；网络面由 decide() 的另一批用例覆盖。 */
    private final SecurityPolicyReviewRoleDecider decider =
            new SecurityPolicyReviewRoleDecider(null, null);

    private static ReviewRoleQuery query() {
        return new ReviewRoleQuery("fc_backtest_review", "eval-1", "run-1", "sc001",
                "t1", "o1", List.of("metric_a"), List.of("1.4"), Map.of("metric_a", "cfg"));
    }

    /** 信封：{@code ApiResponse{data:{…}}}；键位与 {@code PolicyEngineController} 实测形态一致。 */
    private static JsonNode envelope(Map<String, Object> data) {
        return M.valueToTree(Map.of("code", 200, "data", data));
    }

    /** 两态各自的"状态串"：上层据此分列 503 与 403（{@code BacktestReviewRouter}）。 */
    private static String stateStringOf(ReviewRoleDecision d) {
        return "allowed=" + d.allowed() + " unavailable=" + d.unavailable() + " reason=" + d.reason();
    }

    @Test
    @DisplayName("不变式：同输入下 DENY 与 UNEVALUATED 的状态串必异（decisionRef 相同）")
    void denyAndUnevaluatedStateStringsDifferOnSameInput() {
        Map<String, Object> anchor = Map.of(
                GuardrailDecisionContract.KEY_POLICY_REF, "rbac/fc_backtest_review",
                "inputDigest", "digest-abc");
        ReviewRoleDecision denied = decider.parse(envelope(withKey(anchor, "allow", false)), query());
        ReviewRoleDecision unevaluated = decider.parse(envelope(anchor), query());

        assertEquals(denied.decisionRef(), unevaluated.decisionRef(),
                "两态的裁决定位锚点必须同源，否则状态串差异会被 ref 漂移掩盖");
        assertFalse(denied.unavailable(), "allow=false 是真实策略判定，不得报不可用");
        assertTrue(unevaluated.unavailable(),
                "allow 键缺失＝护栏没说话，必须报不可用（上层 503），禁折叠成 403");
        assertNotEquals(stateStringOf(denied), stateStringOf(unevaluated),
                "D-B4-41 复发：DENY 与 UNEVALUATED 状态串相同 ⇒ 用户面无法区分'没权限'与'护栏故障'");
        assertFalse(denied.allowed());
        assertFalse(unevaluated.allowed(), "不可用态同样不得放行（fail-closed 方向不变）");
    }

    @Test
    @DisplayName("不可用族全量：source=fallback / opaError / policyState 未评估 / 值形态不可识别")
    void unevaluatedFormsAllMapToUnavailable() {
        List<Map<String, Object>> forms = List.of(
                Map.of("allow", false, "source", "fallback", "fallback", "DENY_OPA_DOWN"),
                Map.of("allow", false, "opaError", "opa unreachable"),
                Map.of("policyState", GuardrailDecisionContract.STATE_POLICY_UNDEFINED),
                Map.of("policyState", GuardrailDecisionContract.STATE_UNAVAILABLE),
                Map.of("allow", "maybe"),
                Map.of("policyRef", "rbac"));
        for (Map<String, Object> data : forms) {
            ReviewRoleDecision d = decider.parse(envelope(data), query());
            assertTrue(d.unavailable(), "该形态属'护栏没说话'，必须不可用: " + data);
            assertFalse(d.allowed(), "不可用态禁放行: " + data);
        }
    }

    @Test
    @DisplayName("策略拒绝族全量：allow=false / EVALUATED_DENY / decision=deny 才是 DENY")
    void explicitDenyFormsMapToDeny() {
        List<Map<String, Object>> forms = List.of(
                Map.of("allow", false),
                Map.of("policyState", GuardrailDecisionContract.STATE_DENY),
                Map.of("decision", "deny"),
                Map.of("verdict", "reject"));
        for (Map<String, Object> data : forms) {
            ReviewRoleDecision d = decider.parse(envelope(data), query());
            assertFalse(d.unavailable(), "真实策略判定不得冒充不可用（否则 403 面永远为 0）: " + data);
            assertFalse(d.allowed(), "拒绝态禁放行: " + data);
            assertTrue(d.reason().contains("policy denied"), "拒绝理由须说明是策略判定: " + d.reason());
        }
    }

    @Test
    @DisplayName("放行族：角色只从 obligations 透传；无角色既不放行也不 deny（认知侧零推断）")
    void allowPassesRoleThroughWithoutLocalInference() {
        ReviewRoleDecision withRole = decider.parse(envelope(Map.of(
                "allow", true,
                GuardrailDecisionContract.KEY_OBLIGATIONS,
                List.of(Map.of("reviewerRole", "risk_reviewer")))), query());
        assertEquals("risk_reviewer", withRole.reviewerRole());
        assertFalse(withRole.unavailable());

        ReviewRoleDecision noRole = decider.parse(envelope(Map.of(
                "allow", true,
                GuardrailDecisionContract.KEY_OBLIGATIONS, List.of())), query());
        assertTrue(noRole.allowed(), "策略确实放行，读数不得改成 DENY/不可用");
        assertFalse(noRole.unavailable());
        assertNull(noRole.reviewerRole(), "认知侧禁本地推断角色");
        assertTrue(noRole.reason().contains("no local role inference allowed"),
                "无角色必须留可解释理由（fail-loud，禁空串）");
    }

    @Test
    @DisplayName("反面基线：收口前的朴素布尔折叠确实会把缺失 allow 读成拒绝（本批的登记依据）")
    void naiveBooleanFoldIsTheDefectBeingClosed() {
        JsonNode data = envelope(Map.of("policyRef", "rbac")).path("data");
        // 旧判据原样复现：!data.path("allow").asBoolean(false) ⇒ 键缺失即 deny
        boolean oldWouldDeny = !data.path(GuardrailDecisionContract.KEY_ALLOW).asBoolean(false);
        assertTrue(oldWouldDeny, "反面夹具失效：该形态本应被旧口径折叠成 DENY");
        assertEquals(AbacEvaluateResponseContract.Verdict.UNEVALUATED,
                AbacEvaluateResponseContract.verdictOfTree(data),
                "新口径必须把它归为 UNEVALUATED，否则 D-B4-41 的收口依据不成立");
    }

    @Test
    @DisplayName("空信封/缺 data 段：契约断裂按不可用，禁默认放行")
    void malformedEnvelopeIsUnavailable() {
        assertTrue(decider.parse(null, query()).unavailable());
        assertTrue(decider.parse(M.createObjectNode().put("code", 200), query()).unavailable(),
                "envelope 无 data 段＝形态漂移，属不可用");
        assertTrue(decider.parse(envelope(Map.of()), query()).unavailable(),
                "data 段为空对象＝没有任何裁决");
    }

    @Test
    @DisplayName("批次与差距标记在册：本类的上下文不得被后续顺手抹掉")
    void registrationMarkersExist() throws IOException {
        String decider = java.nio.file.Files.readString(repoRoot().resolve(
                "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/cognitive2/service/backtest/"
                        + "SecurityPolicyReviewRoleDecider.java"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(decider.contains("D-B4-41") && decider.contains("#142/B4-3e-6"),
                "裁决器丢失差距/批次标记 ⇒ 台账与本类的对应关系断链，无法用 git log --grep 溯源");
        assertTrue(decider.contains("AbacEvaluateResponseContract.verdictOfTree"),
                "裁决器不再委托单源三态读数 ⇒ D-B4-41 的折叠形态已回归，本类的不变式失去支点");
        assertFalse(decider.contains(".asBoolean(false)"),
                "裁决器重新出现布尔折叠读法（该形态正是 D-B4-41 的发生位置）");
    }

    private static java.nio.file.Path repoRoot() {
        java.nio.file.Path start = java.nio.file.Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 12 && start != null; i++) {
            if (java.nio.file.Files.isDirectory(start.resolve("ecos_backend"))
                    && java.nio.file.Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        throw new AssertionError("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker）");
    }

    /** 在锚点 map 上追加一个键（Map.of 不可变，这里只做夹具拼装）。 */
    private static Map<String, Object> withKey(Map<String, Object> base, String key, Object value) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>(base);
        m.put(key, value);
        return m;
    }
}
