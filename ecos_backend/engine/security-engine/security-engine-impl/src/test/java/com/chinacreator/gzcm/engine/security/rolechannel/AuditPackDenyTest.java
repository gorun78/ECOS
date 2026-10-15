package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-01 §4.7.2 矩阵第 4 行第 2 格 —— 无权用户 / <b>导出</b>通道
 * （可执行标识 {@code AuditPackDenyTest}，#142/B4-3b）。
 *
 * <p><b>矩阵期望</b>：403，且不泄露存在性。</p>
 *
 * <p><b>本格钉住的三件事</b>：</p>
 * <ol>
 *   <li><b>无命中策略时证据面必须是"空"而非"全量"</b>：{@code rls.policyRefs=[]} 且
 *       {@code denyIfEmpty=true} —— 这一对是 workspace 侧
 *       {@code ScenarioReadGuard#exportDecision} 做出 403 的充分条件；
 *       缺任何一半（refs 非空 / denyIfEmpty 丢段）都会把"无权"渲染成"有权"。</li>
 *   <li><b>范围证据不得冒充覆盖范围</b>：无命中时 {@code rls=[NONE] cls=[NONE]}，
 *       与有权主体的范围串必须可区分（否则"scope 自证"退化为常量）。</li>
 *   <li><b>不泄露策略元信息</b>：bundle 文本表示里不得出现 seed 的策略名。</li>
 * </ol>
 *
 * <p><b>在册缺陷（不作为通过项）</b>：CLS 黑名单式（<b>D-B4-6b</b>）使无权用户在
 * {@code columns} 面拿到"全部列可见"，与本行"空集不泄露"冲突；由
 * {@link #clsBlackListLeavesAllColumnsVisibleForNoRoleUser()} 以特征化断言钉住，
 * 收口项 B4-4。</p>
 */
class AuditPackDenyTest {

    private static PageChannelHarness harness;

    @BeforeAll
    static void setUp() throws IOException {
        harness = PageChannelHarness.create();
    }

    @Test
    @DisplayName("export — 无权主体：0 命中策略 + denyIfEmpty=true（导出咽喉据此 403 的充分证据）")
    void exportEvidenceCarriesNoPolicyRefsButKeepsDenyIfEmpty() {
        for (String table : List.of(V246SeedFixtures.T_PERIOD, V246SeedFixtures.T_COST,
                V246SeedFixtures.T_RESOURCE)) {
            DecisionBundle b = harness.decide(table, List.of(),
                    V246SeedFixtures.NO_ROLE_USER, DecisionBundle.Purpose.EXPORT);

            assertEquals(List.of(), b.rlsPolicyRefs(),
                    table + "：无权主体不得带出任何策略出处");
            assertEquals("1=1", b.rlsPredicateTemplate(),
                    table + "：无策略时兜底模板是 1=1（只有配合 denyIfEmpty 才不等于全量放行）");
            assertTrue(b.rlsDenyIfEmpty(),
                    table + "：denyIfEmpty 丢段＝把「没策略」变成「全量可导」，必须为 true");

            String scope = b.scopeEvidence(DecisionBundle.Purpose.EXPORT);
            assertTrue(scope.contains("rls=[NONE]"), "无命中必须显式写 NONE: " + scope);
            assertTrue(scope.contains("cls=[NONE]"), "列面无命中同样不得留空冒充已裁决: " + scope);
        }
    }

    @Test
    @DisplayName("export — 无权主体与财务主体的范围证据可区分（禁把「无命中」渲染成「全量范围」）")
    void scopeEvidenceCannotClaimCoverageWhenNothingMatched() {
        DecisionBundle denied = harness.decide(V246SeedFixtures.T_COST, List.of(),
                V246SeedFixtures.NO_ROLE_USER, DecisionBundle.Purpose.EXPORT);
        DecisionBundle granted = harness.decide(V246SeedFixtures.T_COST, List.of(),
                V246SeedFixtures.FIN_USER, DecisionBundle.Purpose.EXPORT);

        // 谓词文本归一后现状相同（都是恒真），差别只在「命中出处 + denyIfEmpty」这一对：
        // 这正是导出件不能只写谓词、必须写命中策略名的理由（D-B4-10/D-B4-11）。
        assertEquals(bare(denied.rlsPredicateTemplate()), bare(granted.rlsPredicateTemplate()),
                "两者谓词面同为恒真（区别只在有无出处），故本用例的区分度必须来自 policyRefs");
        assertTrue(granted.rlsPolicyRefs().contains("rls-finance-all"));
        assertTrue(denied.rlsPolicyRefs().isEmpty());
        assertTrue(granted.scopeEvidence(DecisionBundle.Purpose.EXPORT)
                .contains("rls=[rls-finance-all]"));
        assertFalse(denied.scopeEvidence(DecisionBundle.Purpose.EXPORT).contains("finance"),
                "无权主体的范围串不得出现有权主体的策略名（存在性泄露）: "
                        + denied.scopeEvidence(DecisionBundle.Purpose.EXPORT));
    }

    @Test
    @DisplayName("export — bundle 不回显策略名/表清单给无权主体（存在性不泄露）")
    void bundleLeaksNoPolicyMetadataForNoRoleUser() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.NO_ROLE_USER, DecisionBundle.Purpose.EXPORT);

        String rendered = String.valueOf(b.toMap());
        assertFalse(rendered.contains("rls-"), "无权主体 bundle 不得含 seed 策略名: " + rendered);
        assertFalse(rendered.contains("cls-"), "同上（列面）: " + rendered);
    }

    @Test
    @DisplayName("defect/D-B4-6b — 无权主体的 CLS 现状：全列可见（导出列面同样未被收口，特征化钉桩）")
    void clsBlackListLeavesAllColumnsVisibleForNoRoleUser() {
        DecisionBundle b = harness.decide(V246SeedFixtures.T_COST, V246SeedFixtures.COST_COLUMNS,
                V246SeedFixtures.NO_ROLE_USER, DecisionBundle.Purpose.EXPORT);

        // ↓ 断的是「现状」，不是「应然」。矩阵第4行应然＝空集不泄露。
        assertFalse(b.columnsDenied(), "D-B4-6b 现状钉桩：黑名单式 CLS 下无权用户全列可见");
        assertEquals(V246SeedFixtures.COST_COLUMNS, PageChannelHarness.visibleColumns(b),
                "B4-4 收口（敏感列改 allow-list 默认拒绝）后本断言须改为空集断言");
        assertEquals(List.of(), b.clsPolicyRefs(),
                "全列可见的根因是「一条 CLS 都没命中」，出处必须如实为空而非伪造");
    }

    /** 剥掉 {@code mergePolicies} 的段括号与空白后的谓词形态（与 workspace 侧判定同一口径）。 */
    private static String bare(String predicate) {
        return predicate == null ? "" : predicate.replaceAll("[()\\s]", "");
    }
}
