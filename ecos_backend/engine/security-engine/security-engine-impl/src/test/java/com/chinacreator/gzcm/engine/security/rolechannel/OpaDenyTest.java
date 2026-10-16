package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵「无权用户」行的护栏列 +「不可用 ≠ 无权限」分态不变式
 * （可执行标识 {@code OpaDenyTest}，#142/B4-3c）。
 *
 * <p>三通道同判据：{@code no_such_role} 主体在页面/导出/AI 三格上都被
 * {@code NO_DATA_ACCESS_ROLE} 拒绝（默认 {@code allow=false}，缺上下文一律不放行）。
 * 本类断的是<b>状态串</b>而不是布尔：{@code allow=false} 在旧形态里同时意味着
 * 「策略拒绝」「策略未加载」「OPA 挂了」，消费方无法据此选 403 还是 503，
 * 于是引擎缺陷会被伪装成用户无权限（差距文档 D-B4-14/D-B4-18/D-B4-21）。</p>
 *
 * <p>{@link #policyDenyAndGuardrailSilenceAreDistinctStates()} 是本批的<b>分态不变式</b>：
 * 同一主体、同一输入下，回放态＝{@code EVALUATED_DENY}（护栏说了话）而不可达态＝
 * {@code OPA_UNAVAILABLE}（护栏没说），二者必须不同名且 {@code guardrailEvaluated} 一真一假。
 * workspace 导出咽喉的 503 硬门（{@code ScenarioReadGuard#exportDecision}）与
 * ai 闸的 FAIL_CLOSED 判据都建立在这一条上。</p>
 */
class OpaDenyTest {

    /**
     * 矩阵行的三格拆成三条具名用例（#142/B4-3d-3）。
     *
     * <p>本批前后差别：原先是一个 {@code for (purpose ...)} 循环，用例名靠
     * {@code "norole_" + purpose.name().toLowerCase()} 拼出来。运行时三格确实都跑了，
     * 但覆盖率钉（{@code DecisionChainCoverageTest}）按调用点的字面名比对 oracle 单源，
     * 拼接名读不到 ⇒ {@code norole_ai} 被报成「无人回放」。<b>这是门禁抓到的真问题，
     * 不是门禁的假阳</b>：§4.7.2 的验收单位是「一格一条具名用例」，拼名让某一格失去
     * 可静态定位的属主，改名或漏一格都不会变红。故改为三条独立用例，
     * 判据仍走同一个私有断言（禁三处各写一套）。</p>
     */
    @Test
    @DisplayName("page — 无权用户经唯一入口落 EVALUATED_DENY(NO_DATA_ACCESS_ROLE)")
    void pageChannelDeniesUserWithoutDataAccessRole() throws IOException {
        assertDeniesWithNoDataAccessRole(OpaOracleReplaySupport.replayViaDecisionChain(
                "norole_page", DecisionBundle.Purpose.PAGE, List.of()));
    }

    @Test
    @DisplayName("export — 无权用户经唯一入口落 EVALUATED_DENY(NO_DATA_ACCESS_ROLE)")
    void exportChannelDeniesUserWithoutDataAccessRole() throws IOException {
        assertDeniesWithNoDataAccessRole(OpaOracleReplaySupport.replayViaDecisionChain(
                "norole_export", DecisionBundle.Purpose.EXPORT, List.of()));
    }

    @Test
    @DisplayName("ai — 无权用户经唯一入口落 EVALUATED_DENY(NO_DATA_ACCESS_ROLE)")
    void aiChannelDeniesUserWithoutDataAccessRole() throws IOException {
        assertDeniesWithNoDataAccessRole(OpaOracleReplaySupport.replayViaDecisionChain(
                "norole_ai", DecisionBundle.Purpose.AI, List.of()));
    }

    /** 三格共用的判据：拒绝态、护栏确实说话、原因串与录制态逐字一致。 */
    private static void assertDeniesWithNoDataAccessRole(OpaOracleReplaySupport.ChainReplay r) {
        DecisionBundle b = r.bundle();
        String purpose = String.valueOf(r.oracle().input().get("purpose"));
        assertEquals(GuardrailDecisionContract.STATE_DENY, b.opaPolicyState(),
                purpose + " 通道必须是「已评估的拒绝」态");
        assertTrue(b.guardrailEvaluated(), purpose + " 通道不得落「护栏未给出裁决」态");
        assertFalse(b.guardrailUnevaluated(), purpose + " 通道不得被报成 503");
        assertFalse(b.opaAllowed());
        assertEquals("NO_DATA_ACCESS_ROLE", deniedReasonOf(r.oracle()),
                purpose + " 通道的拒绝原因逐字取自 rego violation 名");
        assertEquals(deniedReasonOf(r.oracle()), b.opaDeniedReason());
    }

    @Test
    @DisplayName("拒绝态义务不缺席 — NO_DATA_ACCESS_ROLE 下仍带掩码义务（禁把义务段整体丢掉）")
    void deniedCaseStillCarriesMaskObligations() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "norole_export", DecisionBundle.Purpose.EXPORT, List.of());

        List<Map<String, Object>> bundleObligations = obligationsOf(r.bundle().opa());
        assertEquals(OpaOracleReplaySupport.writeJson(r.oracle().obligations()),
                OpaOracleReplaySupport.writeJson(bundleObligations));
        assertFalse(OpaOracleReplaySupport.obligationsOf("mask", bundleObligations).isEmpty(),
                "rego 四类义务集一律 default=[] 的存在理由：分支未定义会让 obligations 段连同 DENIED 理由一起消失");
        assertEquals(1, OpaOracleReplaySupport.obligationsOf("DENIED", bundleObligations).size());
        assertEquals(0, OpaOracleReplaySupport.obligationsOf("watermark", bundleObligations).size(),
                "拒绝态不得伪造水印义务（导出件禁拿 NOT_ISSUED 冒充已加水印）");
    }

    /**
     * 分态不变式：策略拒绝（403 语义）与护栏沉默（503 语义）不得折叠成同一个 {@code allow=false}。
     */
    @Test
    @DisplayName("分态不变式 — 同输入下 EVALUATED_DENY 与 OPA_UNAVAILABLE 必须不同态")
    void policyDenyAndGuardrailSilenceAreDistinctStates() throws IOException {
        OpaOracleReplaySupport.ChainReplay replayed = OpaOracleReplaySupport.replayViaDecisionChain(
                "norole_page", DecisionBundle.Purpose.PAGE, List.of());

        // 默认装配的对端是不可达地址（http://127.0.0.1:1）⇒ 护栏确实没说话
        PageChannelHarness unreachable = PageChannelHarness.create();
        OpaOracleReplaySupport.OracleCase c = OpaOracleReplaySupport.byName("norole_page");
        DecisionBundle silenced = unreachable.decide(
                new AssetRef(PageChannelHarness.SCHEMA, (String) c.input().get("asset"), List.of()),
                unreachable.explicitContext(OpaOracleReplaySupport.str(c, "userId"),
                        OpaOracleReplaySupport.str(c, "tenantId"),
                        OpaOracleReplaySupport.str(c, "department"),
                        OpaOracleReplaySupport.rolesOf(c)),
                DecisionBundle.Purpose.PAGE);

        // 两态共同点：都 fail-close（这是铁律，不是缺陷）
        assertFalse(replayed.bundle().opaAllowed());
        assertFalse(silenced.opaAllowed());
        // 两态不同点：状态串必须可分辨，否则消费方无法选 403 还是 503
        assertEquals(GuardrailDecisionContract.STATE_DENY, replayed.bundle().opaPolicyState());
        assertEquals(GuardrailDecisionContract.STATE_UNAVAILABLE, silenced.opaPolicyState());
        assertNotEquals(silenced.opaPolicyState(), replayed.bundle().opaPolicyState());
        assertTrue(replayed.bundle().guardrailEvaluated());
        assertTrue(silenced.guardrailUnevaluated(),
                "不可达态必须落「护栏未给出裁决」，workspace/AI 侧据此报 503/FAIL_CLOSED 而非 403");
        assertNotEquals(GuardrailDecisionContract.STATE_DENY, GuardrailDecisionContract.STATE_UNAVAILABLE,
                "词表本身若把两态写成同一个串，上面的分态断言会一起失效 —— 本条是门禁有效性的对照");
    }

    private static String deniedReasonOf(OpaOracleReplaySupport.OracleCase c) {
        List<Map<String, Object>> denied =
                OpaOracleReplaySupport.obligationsOf("DENIED", c.obligations());
        assertEquals(1, denied.size(), c.name() + " 的录制态应恰有一条 DENIED 义务");
        return String.valueOf(denied.get(0).get(GuardrailDecisionContract.KEY_DENIED_REASON));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> obligationsOf(Map<String, Object> opaSegment) {
        Object raw = opaSegment.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        return raw instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
