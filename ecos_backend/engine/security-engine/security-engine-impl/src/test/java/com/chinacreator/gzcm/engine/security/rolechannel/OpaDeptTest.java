package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵「部门用户」行的护栏列（可执行标识 {@code OpaDeptTest}，#142/B4-3c）。
 *
 * <p>本行两格：AI（本部门的 {@code fc_resource_input_fact}）ALLOW、导出 ALLOW（带脱敏 + 水印）。
 * 与项目负责人行同判据（非财务主体 ⇒ 掩码义务必带、限流键按主体派生），差别在资产与角色集 ——
 * 资产名取自 oracle 的 {@code input.asset}（该格录的是 {@code fc_resource_input_fact}），
 * 用例不内联表名，避免与 V246 种子/矩阵登记形成第二套真值。</p>
 *
 * <p>{@code dept_ai} 带 {@code resource.dataScope}，只有 AI 工具裁决载荷会发，
 * 因此走直连通道；{@code dept_export} 走三通道唯一入口。理由同
 * {@link OpaProjOwnerTest} 类注释。</p>
 */
class OpaDeptTest {

    @Test
    @DisplayName("export — 部门用户导出 ALLOW，义务集逐字等于录制态（掩码 + 水印）")
    void exportAllowsWithMaskAndWatermark() throws IOException {
        OpaOracleReplaySupport.ChainReplay r = OpaOracleReplaySupport.replayViaDecisionChain(
                "dept_export", DecisionBundle.Purpose.EXPORT, V246SeedFixtures.COST_COLUMNS);

        assertEquals(GuardrailDecisionContract.STATE_ALLOW, r.bundle().opaPolicyState());
        assertTrue(r.bundle().guardrailEvaluated());
        List<Map<String, Object>> watermark =
                OpaOracleReplaySupport.obligationsOf("watermark", r.oracle().obligations());
        assertEquals(1, watermark.size());
        assertEquals(String.valueOf(watermark.get(0).get("watermark")), r.bundle().watermarkObligation(),
                "水印串里出现的资产必须是 oracle 记录的 asset，禁由用例拼装");
        assertEquals(OpaOracleReplaySupport.writeJson(r.oracle().obligations()),
                OpaOracleReplaySupport.writeJson(r.bundle().opa()
                        .get(GuardrailDecisionContract.KEY_OBLIGATIONS)));
    }

    @Test
    @DisplayName("ai — 本部门资源事实表 ALLOW（请求域＝主体组织域 ⇒ 无横向越权）")
    void aiWithinOwnDepartmentScopeAllowsOnResourceFact() throws IOException {
        OpaOracleReplaySupport.GuardrailReplay r =
                OpaOracleReplaySupport.replayViaGuardrailDirect("dept_ai");

        assertEquals(Boolean.TRUE, r.data().get(GuardrailDecisionContract.KEY_ALLOW));
        assertEquals(GuardrailDecisionContract.STATE_ALLOW,
                r.data().get(GuardrailDecisionContract.KEY_POLICY_STATE));
        assertEquals("ecos_dw.fc_resource_input_fact", r.oracle().input().get("asset"),
                "本格录的是资源事实表；若单源被改动则本断言先红，禁改期望值迁就");
        assertEquals(2, OpaOracleReplaySupport.obligationsOf("mask", r.oracle().obligations()).size(),
                "dept_user 同属非财务主体 ⇒ 掩码义务不得缺席");
        assertEquals(1, OpaOracleReplaySupport.obligationsOf("rateLimit", r.oracle().obligations()).size());
    }
}
