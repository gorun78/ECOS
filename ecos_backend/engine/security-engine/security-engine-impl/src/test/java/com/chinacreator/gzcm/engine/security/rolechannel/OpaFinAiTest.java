package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵「财务负责人」行的<b>护栏（OPA）列</b> —— 三通道全 ALLOW，
 * 义务集随通道而变（可执行标识 {@code OpaFinAiTest}，#142/B4-3c）。
 *
 <p><b>本类证明什么</b>：护栏<b>真的被咨询</b>并且<b>真的能给出义务</b>。
 * 前两批在同一矩阵行上只能断到行/列面，护栏面被 {@code D-B4-12/D-B4-14} 卡死
 * （策略名 {@code data.access} 含点号 ⇒ 白名单在校验阶段就拒 ⇒ OPA 从未收到请求 ⇒
 * {@code obligations} 恒空 ⇒ 水印义务永不可得）。B4-3c 把名字改成单源
 * {@code data_access}、策略落到 {@code ecos-docker/opa-policies/data_access.rego} 之后，
 * 本类用真引擎录制的 oracle 回放，正面收口「导出件水印义务可得的出处」这条验收项。</p>
 *
 * <p><b>不证明什么（诚实边界）</b>：oracle 是<b>录制型</b>（离线真 Rego 引擎求值后逐字录成
 * {@code data_access_oracles.json}），不是 live {@code ecos-opa} 端到端 —— dev 容器
 * {@code /v1/policies} 实数 0 个，加载它需改 compose 并重启共享容器，属未授权运维项，
 * 已登记在差距文档剩余项。本类断的是「策略语义 → OPA 应答原文 → 生产裁决链」三段一致，
 * 见 {@link OpaOracleReplaySupport}。</p>
 *
 * <p>期望值一律来自 oracle（{@code c.obligations()}）或单源词表
 * {@link GuardrailDecisionContract}，禁在本类内联义务文本。</p>
 */
class OpaFinAiTest {

    @Test
    @DisplayName("page — 财务负责人页面通道护栏放行且义务集为空（不冒充「已加义务」）")
    void pageChannelAllowsWithEmptyObligations() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "fin_page", DecisionBundle.Purpose.PAGE, V246SeedFixtures.COST_COLUMNS);

        DecisionBundle b = r.bundle();
        assertTrue(b.guardrailEvaluated(),
                "护栏必须处于「已给出裁决」态；false 意味着本类退化成 B4-3b 之前的「从未被咨询」现状");
        assertEquals(GuardrailDecisionContract.STATE_ALLOW, b.opaPolicyState());
        assertTrue(b.opaAllowed());
        assertFalse(b.guardrailUnevaluated(), "ALLOW 态不得被报成 503");

        List<Map<String, Object>> expected = OpaOracleReplaySupport.obligationsOf(
                "watermark", r.oracle().obligations());
        assertEquals(expected.size(), watermarkCount(b), "义务文本只能来自 oracle");
        assertTrue(r.oracle().obligations().isEmpty(),
                "fin_page 录制态义务集为空（页面通道无水印/限流义务），本用例据此断 null 而非伪造");
        assertNull(b.watermarkObligation(), "页面通道无水印义务时必须取到 null，禁渲染成「已加水印」");
        assertNull(b.opaDeniedReason(), "ALLOW 态不得带 deniedReason");
        assertNotNull(b.decisionId(), "护栏面必须挂在同一次裁决上（decisionId 是审计反查键）");
    }

    @Test
    @DisplayName("export — 导出通道水印义务逐字来自 rego 录制态（D-B4-12 的正面收口）")
    void exportChannelCarriesRecordedWatermarkObligation() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "fin_export", DecisionBundle.Purpose.EXPORT, V246SeedFixtures.COST_COLUMNS);

        assertEquals(GuardrailDecisionContract.STATE_ALLOW, r.bundle().opaPolicyState());
        List<Map<String, Object>> recorded =
                OpaOracleReplaySupport.obligationsOf("watermark", r.oracle().obligations());
        assertEquals(1, recorded.size(), "oracle 里 fin_export 必须恰有一条 watermark 义务，否则本断言无出处");

        String expectedWatermark = String.valueOf(recorded.get(0).get("watermark"));
        assertEquals(expectedWatermark, r.bundle().watermarkObligation(),
                "导出件页眉水印串必须由护栏义务给出（workspace 侧只能逐字引用，禁自造）");
        assertFalse(expectedWatermark.isBlank());
    }

    @Test
    @DisplayName("ai — AI 通道限流义务逐字来自 rego 录制态（rateLimitKey 同样不是自述）")
    void aiChannelCarriesRecordedRateLimitObligation() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "fin_ai", DecisionBundle.Purpose.AI, V246SeedFixtures.COST_COLUMNS);

        assertEquals(GuardrailDecisionContract.STATE_ALLOW, r.bundle().opaPolicyState());
        List<Map<String, Object>> recorded =
                OpaOracleReplaySupport.obligationsOf("rateLimit", r.oracle().obligations());
        assertEquals(1, recorded.size());
        assertEquals(String.valueOf(recorded.get(0).get("rateLimitKey")),
                rateLimitKeyOf(r.bundle()), "限流键只能来自 oracle 录制的义务");
        assertNull(r.bundle().watermarkObligation(), "AI 通道不携水印义务（rego 只在 export 分支定义）");
    }

    /**
     * 缺陷根因的反面证据（D-B4-14）：请求真的发到 OPA，且打的是<b>策略文档</b>
     * {@code POST /v1/data/ecos/data_access} —— 而不是含点号的非法名（那会在发出前就被白名单拒），
     * 也不是旧的裸布尔 {@code .../data_access/allow}（那会让 obligations 段结构上不可达，D-B4-20）。
     *
     * <p>本断言由 {@link OpaOracleReplaySupport} 在返回回放前强制执行，这里再显式跑一次
     * 是为了让「线路形态」成为一条可单独变红的用例，而不是藏在夹具里。</p>
     */
    @Test
    @DisplayName("线路形态 — 恰一次 POST /v1/data/ecos/<单源策略名>，名合规且非裸布尔查询")
    void guardrailIsConsultedOnTheLegalDocumentPath() throws IOException {
        OpaOracleReplaySupport.assertPolicyNotDrifted();
        assertEquals("data_access", OpaPolicyService.POLICY_DATA_ACCESS,
                "单源策略名必须无点号（OpaPolicyService.SAFE_NAME 白名单不含点号）");
        assertEquals("/v1/data/ecos/" + OpaPolicyService.POLICY_DATA_ACCESS,
                OpaOracleReplaySupport.OPA_DOC_PATH);
        assertTrue(OpaOracleReplaySupport.regoText().contains("package ecos." + OpaPolicyService.POLICY_DATA_ACCESS),
                "rego 包名必须与线上路径同源自洽，否则回放打到的是不存在的文档");
    }

    // ── 从 bundle 的 opa 段取义务字段（读面与生产同键名，不另造解析） ──────────────

    private static int watermarkCount(DecisionBundle b) {
        return OpaOracleReplaySupport.obligationsOf("watermark", obligations(b)).size();
    }

    private static String rateLimitKeyOf(DecisionBundle b) {
        List<Map<String, Object>> rate = OpaOracleReplaySupport.obligationsOf("rateLimit", obligations(b));
        return rate.isEmpty() ? null : String.valueOf(rate.get(0).get("rateLimitKey"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> obligations(DecisionBundle b) {
        Object raw = b.opa().get(GuardrailDecisionContract.KEY_OBLIGATIONS);
        return raw instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }
}
