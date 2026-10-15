package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵第 1 行第 2 格 —— 财务负责人 / <b>导出</b>通道
 * （可执行标识 {@code AuditPackFinTest}，#142/B4-3b）。
 *
 * <p><b>矩阵期望</b>：全量行 + manifest 标 {@code scope=finance-all}。</p>
 *
 * <p><b>本用例证明什么、不证明什么</b>：{@code scope=finance-all} 在设计里是导出件的
 * 权限自证串，而它的合法出处只能是「本次裁决命中了哪条策略」。因此本用例断言的是
 * {@code decide(purpose=EXPORT)} 返回 bundle 里的<b>范围证据</b>
 * （{@code rls.policyRefs=[rls-finance-all]} + 谓词 {@code (1=1)} + decisionId），
 * 由 {@code DecisionBundle#scopeEvidence} 渲染成导出件用的串；
 * workspace 侧消费者（{@code ForecastExportService} 文件头、
 * {@code ForecastAuditPackService} manifest）只能逐字引用它，
 * 接线与禁常量回潮由 workspace 侧 {@code ExportDecisionContractTest} 钉。</p>
 *
 * <p>策略输入取自 V246 单源（{@link V246SeedFixtures}），装配走
 * {@link PageChannelHarness} 的真实裁决链 —— 与本矩阵页面通道行同源，禁在本类内联第二套策略。</p>
 */
class AuditPackFinTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("export — 财务负责人全量表命中 rls-finance-all，范围证据可反查（scope=finance-all 的唯一出处）")
    void exportScopeEvidenceNamesFinanceAllPolicy() {
        for (String table : List.of(V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST,
                V246SeedFixtures.T_RESOURCE)) {
            DecisionBundle b = harness.decide(table, List.of(),
                    V246SeedFixtures.FIN_USER, DecisionBundle.Purpose.EXPORT);

            assertNotNull(b.decisionId(), "导出件要落 decisionId，缺失即无法反查");
            assertEquals(List.of("rls-finance-all"), b.rlsPolicyRefs(),
                    table + " 上财务负责人的导出范围证据必须逐字是 seed 里的 policy_name");
            assertEquals("(1=1)", b.rlsPredicateTemplate(),
                    "rls-finance-all 是 legacy_text '1=1'，mergePolicies 后带段括号");
            assertTrue(b.rlsDenyIfEmpty(), "denyIfEmpty 必须随 bundle 外发，供导出侧做无策略拒绝判定");

            String scope = b.scopeEvidence(DecisionBundle.Purpose.EXPORT);
            assertTrue(scope.contains("purpose=export"), scope);
            assertTrue(scope.contains("rls=[rls-finance-all]"), scope);
            assertTrue(scope.startsWith("decision=" + b.decisionId()),
                    "范围串必须以裁决号起头（审计反查键）: " + scope);
        }
    }

    @Test
    @DisplayName("export — 带列请求时 CLS 范围证据同步落 bundle（财务角色无 CLS 拒绝策略 ⇒ 列面不 DENY）")
    void exportWithColumnsCarriesClsEvidence() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.FIN_USER, DecisionBundle.Purpose.EXPORT);

        assertEquals(List.of(), b.clsPolicyRefs(),
                "V246 未给 finance_lead 播 CLS 策略（cls-non-finance-hide 属 non_finance），"
                        + "因此 cls 段必须是 NONE 而非伪造策略名");
        assertFalse(b.columnsDenied(), "财务负责人列面不得 DENY: " + b.columns());
        assertEquals(V246SeedFixtures.COST_COLUMNS, PageChannelHarness.visibleColumns(b),
                "整请求列集在财务角色下应全部可见（hourly_rate/amount 含内）");
        assertTrue(b.scopeEvidence(DecisionBundle.Purpose.EXPORT).contains("cls=[NONE]"),
                "无 CLS 命中时范围串必须显式写 NONE，禁留空冒充'已裁决'");
    }

    @Test
    @DisplayName("export/D-B4-12 现状半边 — 护栏不可达时水印义务如实为 null（可达态见 OpaFinAiTest 的 oracle 回放）")
    void watermarkObligationIsAbsentWhileOpaUnreachable() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.FIN_USER, DecisionBundle.Purpose.EXPORT);

        // 特征化断言（钉现状，非认可）：本 harness 的 OPA 对端不可达 ⇒ 义务集为空是诚实结果。
        // 订正（B4-3c）：仓内已有名合规的 data_access.rego（含 watermark 规则），
        // "水印义务可得"的正面证据在 OpaFinAiTest（录制型 oracle 回放），不在本用例。
        assertEquals(Boolean.FALSE, b.opa().get("allow"), "OPA 源不可达必须 fail-close（D-B4-12 现状）");
        assertNull(b.watermarkObligation(),
                "不可达态不可能带出水印义务；若此处凭空得到义务文本即为伪造");
    }

    /**
     * 缺陷根因钉桩 + 订正（D-B4-14/D-B4-18 → B4-3c 已修）。
     *
     * <p><b>原缺陷</b>：{@code SecurityDecisionService.safeOpa} 用策略名 {@code "data.access"}
     * 调 {@code OpaPolicyService.evaluate}，而名称白名单 {@code ^[a-zA-Z0-9_-]{1,64}$} 不含点号
     * ⇒ 在发起任何 HTTP 之前就抛 {@code IllegalArgumentException}，被兜成
     * {@code allow=false + OPA_ERROR_FAIL_CLOSED}。bundle 里的 {@code allow=false}
     * 看起来像"策略拒绝"，实际是"护栏根本没被咨询"——二者混同后，任何以 {@code opa.allow}
     * 为硬门的通道都会把引擎缺陷伪装成用户无权限。</p>
     *
     * <p><b>B4-3c 后的现状（本用例断言的新事实）</b>：策略名取自单源
     * {@code OpaPolicyService.POLICY_DATA_ACCESS}（{@code data_access}，白名单可过），
     * 请求真的发到了 OPA 网络面；harness 的对端不可达 ⇒ 呈现的是<b>不可用态</b>
     * {@code policyState=OPA_UNAVAILABLE + deniedReason=OPA_UNAVAILABLE_FAIL_CLOSED}，
     * 不再是异常兜底态。二者都必须与"策略拒绝"分态（§4.7.2 不可用 ≠ 无权限）：
     * 消费侧据此报 503/FAIL_CLOSED，禁折叠成 403。名称白名单那条根因规则本身仍在位，
     * 故保留其回归断言（非法名永远进不了网络面）。</p>
     */
    @Test
    @DisplayName("export/护栏分态 — 策略名已合规（点号名仍被白名单拒），不可达如实呈现 OPA_UNAVAILABLE 而非策略拒绝")
    void guardrailStateIsUnavailableNotPolicyDeny() {
        OpaPolicyService opa = new OpaPolicyService("http://127.0.0.1:1", null);
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> opa.evaluate("data.access", Map.of("purpose", "export")),
                "根因规则仍在位：含点号的策略名在名称白名单处即被拒（未触达 OPA 网络面）");
        assertTrue(rejected.getMessage().contains("非法策略名称"), rejected.getMessage());

        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.FIN_USER, DecisionBundle.Purpose.EXPORT);
        assertEquals("ecos." + OpaPolicyService.POLICY_DATA_ACCESS, b.opa().get("policyRef"),
                "裁决入口必须用单源合规名，否则护栏从未被咨询");
        assertEquals(GuardrailDecisionContract.STATE_UNAVAILABLE, b.opaPolicyState(),
                "对端不可达必须是不可用态，禁冒充策略裁决");
        assertEquals("OPA_UNAVAILABLE_FAIL_CLOSED", deniedReason(b),
                "不可用态的 deniedReason 必须自带 fail-close 出处，供审计区分「策略拒绝」与「护栏没说话」");
        assertFalse(b.guardrailEvaluated(), "不可用态不属于已评估态，硬门须走 503 而非 403");
    }

    private static String deniedReason(DecisionBundle b) {
        Object obligations = b.opa().get("obligations");
        if (obligations instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("deniedReason") != null) {
                    return String.valueOf(m.get("deniedReason"));
                }
            }
        }
        return null;
    }
}
