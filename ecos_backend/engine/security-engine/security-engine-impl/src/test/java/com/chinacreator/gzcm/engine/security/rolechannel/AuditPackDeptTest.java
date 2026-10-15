package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵第 3 行第 2 格 —— 部门用户 d01 / <b>导出</b>通道
 * （可执行标识 {@code AuditPackDeptTest}，#142/B4-3b）。
 *
 * <p><b>矩阵期望</b>：仅本部门行 + 隐藏时薪/金额列，导出件按同一范围裁剪。</p>
 *
 * <p><b>本格的可执行结论（与页面格的差异所在）</b>：d01 的命中谓词
 * {@code (department_id = :userDeptId)} 是<b>需要参数化下推</b>的非平凡形态，
 * 而 CSV/zip 导出件的数据载体不接参数化谓词。因此本格唯一诚实的落地是
 * <b>导出 DENY</b>（差距文档 D-B4-13：宁 DENY 不超发，禁"全量捞回内存再滤"，
 * 与 F07-18-1 红线同源）。本类钉住的是<b>作出该判定的证据面</b>
 * —— 谓词非恒真/非恒假、命中策略名可反查、绑定来源为服务端上下文；
 * 拒绝动作本身在 workspace 侧 {@code ScenarioReadGuard#exportDecision}，
 * 由 {@code ExportDecisionContractTest} 与 {@code ForecastExportPipelineTest} 钉。</p>
 *
 * <p>策略输入取自 V246 单源（{@link V246SeedFixtures}），装配走
 * {@link PageChannelHarness} 的真实裁决链，禁在本类内联第二套策略。</p>
 */
class AuditPackDeptTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("export — d01 的范围证据点名 rls-dept-scope 与部门维度谓词（可反查、不可冒充全量）")
    void exportEvidenceNamesDeptScopePolicyAndItsPredicate() {
        for (String table : List.of(V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST,
                V246SeedFixtures.T_RESOURCE)) {
            DecisionBundle b = harness.decide(table, List.of(),
                    V246SeedFixtures.DEPT_USER, DecisionBundle.Purpose.EXPORT);

            assertEquals(List.of("rls-dept-scope"), b.rlsPolicyRefs(),
                    table + "：导出范围证据必须是 seed 里的 policy_name，实得 " + b.rlsPolicyRefs());
            assertEquals("(department_id = :userDeptId)", b.rlsPredicateTemplate(),
                    table + "：d01 的导出谓词必须是部门维度列比较");

            String scope = b.scopeEvidence(DecisionBundle.Purpose.EXPORT);
            assertTrue(scope.contains("rls=[rls-dept-scope]"), scope);
            assertTrue(scope.startsWith("decision=" + b.decisionId()),
                    "范围串以裁决号起头，审计可反查: " + scope);
            assertNotEquals(
                    harness.decide(table, List.of(), V246SeedFixtures.FIN_USER,
                            DecisionBundle.Purpose.EXPORT).scopeEvidence(DecisionBundle.Purpose.EXPORT),
                    scope, "同一张表上 d01 与 u-fin 的范围证据不得相同，否则范围自证等于没证");

            List<?> bindings = (List<?>) b.rls().get("bindings");
            assertEquals(1, bindings.size(), table + "：部门维度必须带 1 个绑定，禁空调上下文");
            Map<?, ?> binding = (Map<?, ?>) bindings.get(0);
            assertEquals("userDeptId", String.valueOf(binding.get("name")));
            assertEquals("CONTEXT_ORG", String.valueOf(binding.get("source")),
                    "部门维度只从服务端上下文取（F01-08），不得由请求体供给");
        }
    }

    @Test
    @DisplayName("export/缺陷 D-B4-13 — d01 谓词非恒真亦非恒假 ⇒ 不可下推至导出件载体（判定根据）")
    void predicateIsNonTrivialSoExportCarrierCannotHonourIt() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, List.of(),
                V246SeedFixtures.DEPT_USER, DecisionBundle.Purpose.EXPORT);

        String template = b.rlsPredicateTemplate();
        // 剥掉 mergePolicies 的段括号后才是"可判定形态"：既不 1=1（全量）也不 1=0（拒绝）
        String bare = template.replaceAll("[()\\s]", "");
        assertNotEquals("1=1", bare, "不得把部门限定谓词渲染成全量");
        assertNotEquals("1=0", bare, "不得把「有范围但导不出」谎称成「无权限」");
        assertTrue(bare.contains("department_id"), "谓词主体必须是部门维度列: " + template);
        assertTrue(b.rlsDenyIfEmpty(), "denyIfEmpty 必须随 bundle 外发");
    }

    @Test
    @DisplayName("export/缺陷 D-B4-6a — d01 的 CLS 范围证据点名 cls-non-finance-hide，列面现状为 0 列可读")
    void clsEvidenceNamesNonFinanceHidePolicy() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.DEPT_USER, DecisionBundle.Purpose.EXPORT);

        assertEquals(List.of("cls-non-finance-hide"), b.clsPolicyRefs(),
                "列面出处必须落进 bundle（导出件 cls= 段的唯一合法出处）");
        assertTrue(b.scopeEvidence(DecisionBundle.Purpose.EXPORT).contains("cls=[cls-non-finance-hide]"),
                b.scopeEvidence(DecisionBundle.Purpose.EXPORT));

        // 特征化断言（钉现状，非认可）：seed 的 visible_cols='[]' 在 allow 语义下＝整表 0 列可读，
        // 矩阵期望是「隐藏 hourly_rate/amount、其余 3 列可导」。收口项 B4-4，届时本断言须改为 3 列投影。
        assertTrue(b.columnsDenied(), "D-B4-6a 现状钉桩：d01 导出列面整表拒绝");
        assertEquals(List.of(), PageChannelHarness.visibleColumns(b));
    }
}
