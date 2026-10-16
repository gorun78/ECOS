package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵「项目负责人」行的护栏列 —— AI 通道全域扫描禁止 +
 * 跨部门请求域横向越权拒绝（可执行标识 {@code OpaProjOwnerTest}，#142/B4-3c）。
 *
 * <p>矩阵在本行要求四格：AI(p1 本部门) ALLOW、AI(p2 跨部门) DENY、
 * AI(不声明数据域) DENY、导出 ALLOW（带脱敏 + 水印）。四格的判据都在
 * {@code data_access.rego} 的 {@code violation} 集里，本类用录制 oracle 回放逐字断出来，
 * 不在用例里另写一套判定（差距文档 D-B4-22：AI 通道此前查的是另一份策略 {@code rbac}，
 * 「三通道同源」只在入口形态成立、在策略面上是假的 —— 本类即改判据后的证据）。</p>
 *
 * <p><b>回放通道的分工（#142/B4-3d-3 订正，D-B4-20 收口）</b>：带
 * {@code resource.dataScope} 的 p1/p2 两例<b>必须</b>走三通道唯一入口
 * {@link OpaOracleReplaySupport#replayViaDecisionChain} —— 本批前它们走护栏源直连，
 * 理由（"safeOpa 不发 dataScope"）正是 D-B4-20 登记的缺陷本身；现在 {@code safeOpa}
 * 按 {@link com.chinacreator.gzcm.common.security.decision.DataScopeContract} 写出声明域，
 * 可比性钉会深比 {@code resource} 段 ⇒ 若生产者漏写/改写该段，回放立刻变红。
 * 无数据域的 {@code proj_ai_no_scope} 与 {@code proj_export} 同走唯一入口；只有带
 * {@code input.environment} 的通道推断例才保留直连（{@code /decision} 发不出该段）。</p>
 */
class OpaProjOwnerTest {

    @Test
    @DisplayName("ai/无数据域 — 不声明 resource.dataScope 即策略拒绝（禁全域扫描兜底）")
    void aiWithoutDeclaredScopeIsPolicyDeny() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "proj_ai_no_scope", DecisionBundle.Purpose.AI, V246SeedFixtures.COST_COLUMNS);

        DecisionBundle b = r.bundle();
        assertTrue(b.guardrailEvaluated(), "必须是护栏「给出了裁决」的拒绝态，不是「护栏没说话」");
        assertEquals(GuardrailDecisionContract.STATE_DENY, b.opaPolicyState());
        assertFalse(b.opaAllowed());
        assertEquals(deniedReasonOf(r.oracle()), b.opaDeniedReason(),
                "拒绝原因串逐字来自 rego 的 violation 名（审计可回查）");
    }

    @Test
    @DisplayName("ai/本部门 p1 — 经唯一入口 ALLOW 且义务集逐字等于录制态（D-B4-20 收口硬证）")
    void aiWithInDepartmentScopeIsAllow() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "proj_ai_p1", DecisionBundle.Purpose.AI, V246SeedFixtures.COST_COLUMNS);

        DecisionBundle b = r.bundle();
        assertTrue(b.opaAllowed(), "域内请求经 /decision 必须得到放行（此前该形态在唯一入口上不可表达）");
        assertEquals(GuardrailDecisionContract.STATE_ALLOW, b.opaPolicyState());
        assertEquals(OpaOracleReplaySupport.writeJson(r.oracle().obligations()),
                OpaOracleReplaySupport.writeJson(obligationsOf(b.opa())),
                "义务集（含掩码列与限流键）必须逐字透传，禁在此裁剪或补写");
        assertEquals(2, OpaOracleReplaySupport.obligationsOf("mask", r.oracle().obligations()).size(),
                "非财务主体必带两条掩码义务（与 cls-non-finance-hide 同源列集）");
    }

    @Test
    @DisplayName("ai/跨部门 p2 — 经唯一入口 DENY(AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT) 且拒绝态仍带掩码义务")
    void aiWithCrossDepartmentScopeIsDenyButKeepsMaskObligations() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "proj_ai_p2", DecisionBundle.Purpose.AI, V246SeedFixtures.COST_COLUMNS);

        DecisionBundle b = r.bundle();
        assertFalse(b.opaAllowed());
        assertEquals(GuardrailDecisionContract.STATE_DENY, b.opaPolicyState());
        assertEquals("AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT", deniedReasonOf(r.oracle()));
        assertEquals(deniedReasonOf(r.oracle()), b.opaDeniedReason(),
                "跨部门请求域的拒绝理由必须由义务给出，不能只在 allow 布尔上体现");
        assertFalse(OpaOracleReplaySupport.obligationsOf("mask", obligationsOf(b.opa())).isEmpty(),
                "拒绝态义务集不得整段消失（rego 侧 default X=[] 的存在理由：分支未定义会让 DENIED 理由一起丢）");
    }

    @Test
    @DisplayName("export — 项目负责人导出 ALLOW，脱敏 + 水印义务齐备")
    void exportChannelAllowsWithMaskAndWatermark() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "proj_export", DecisionBundle.Purpose.EXPORT, V246SeedFixtures.COST_COLUMNS);

        assertEquals(GuardrailDecisionContract.STATE_ALLOW, r.bundle().opaPolicyState());
        List<Map<String, Object>> watermark =
                OpaOracleReplaySupport.obligationsOf("watermark", r.oracle().obligations());
        assertEquals(1, watermark.size());
        assertEquals(String.valueOf(watermark.get(0).get("watermark")), r.bundle().watermarkObligation(),
                "非财务主体的导出水印同样只能取护栏义务值");
        assertEquals(OpaOracleReplaySupport.writeJson(r.oracle().obligations()),
                OpaOracleReplaySupport.writeJson(obligationsOf(r.bundle().opa())));
    }

    // ── 读面helper（只读 oracle/义务，不含判定逻辑） ──────────────────────────

    /** oracle 顶层 DENIED 义务里的拒绝原因。 */
    private static String deniedReasonOf(OpaOracleReplaySupport.OracleCase c) {
        List<Map<String, Object>> denied =
                OpaOracleReplaySupport.obligationsOf("DENIED", c.obligations());
        assertEquals(1, denied.size(), c.name() + " 的录制态应恰有一条 DENIED 义务");
        return String.valueOf(denied.get(0).get(GuardrailDecisionContract.KEY_DENIED_REASON));
    }

    /** 生产返回段里的义务集（只读，不含判定逻辑）。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> obligationsOf(Map<String, Object> opaSegment) {
        Object raw = opaSegment.get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        return raw instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
