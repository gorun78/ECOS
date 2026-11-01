package com.chinacreator.gzcm.runtime.access.git.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §2.9.6③ 对账 8 类判定的可执行判据（#147D）。
 *
 * <p>每枚判定各一条用例，外加两条「判据本身」用例：</p>
 * <ol>
 *   <li><b>腿不成套先于取数缺失</b>：否则「Git 成、DB 败」会被降级成一句「没法验证」；</li>
 *   <li><b>缺证据绝不判一致</b>：{@code CONSISTENT} 必须六项证据齐备——
 *       这是收口前「读不到即无差异」假绿的正面处置。</li>
 * </ol>
 */
class PublishReconcilerTest {

    private static final DualWriteOutcome HEALTHY = DualWriteOutcome.builder(
                    LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE)
            .gitRef("abc123")
            .versionRef("v2")
            .build();

    private static ReconciliationInput.Builder healthyInput() {
        return ReconciliationInput.builder()
                .outcome(HEALTHY)
                .authoritativeObjectKeys(new LinkedHashSet<>(Set.of("e1", "e2")))
                .repositoryObjectKeys(new LinkedHashSet<>(Set.of("e1", "e2")))
                .authoritativeContentDigest("sha256:aaa")
                .repositoryContentDigest("sha256:aaa")
                .versionRefMatched(true)
                .dependencyMatched(true);
    }

    @Test
    @DisplayName("无输入 / 无三腿记录 / 无任何证据 → UNVERIFIABLE（不落一致也不落差异）")
    void missingEvidenceNeverBecomesConsistent() {
        assertEquals(ReconciliationVerdict.UNVERIFIABLE,
                PublishReconciler.reconcile(null).verdict());
        assertEquals(ReconciliationVerdict.UNVERIFIABLE,
                PublishReconciler.reconcile(ReconciliationInput.builder().build()).verdict());
        PublishReconciler.Result bare = PublishReconciler.reconcile(
                ReconciliationInput.builder().outcome(HEALTHY).build());
        assertEquals(ReconciliationVerdict.UNVERIFIABLE, bare.verdict());
        assertEquals(Set.of("authoritativeObjectKeys", "repositoryObjectKeys",
                        "authoritativeContentDigest", "repositoryContentDigest",
                        "versionRefMatched", "dependencyMatched"),
                bare.evidenceGaps());
    }

    @Test
    @DisplayName("三腿不成套优先于取数缺失 → PARTIAL_PUBLISH")
    void incompleteLegsWinOverEvidenceGaps() {
        DualWriteOutcome partial = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.FAILED, LegOutcome.NOT_APPLICABLE)
                .gitRef("abc123").build();
        assertEquals(ReconciliationVerdict.PARTIAL_PUBLISH, PublishReconciler.reconcile(
                ReconciliationInput.builder().outcome(partial).build()).verdict());
    }

    @Test
    @DisplayName("八类判定逐枚可达")
    void allEightVerdictsAreReachable() {
        assertEquals(ReconciliationVerdict.CONSISTENT,
                PublishReconciler.reconcile(healthyInput().build()).verdict());

        assertEquals(ReconciliationVerdict.VERSION_REF_MISMATCH,
                PublishReconciler.reconcile(healthyInput().versionRefMatched(false).build()).verdict());

        assertEquals(ReconciliationVerdict.MISSING_OBJECT, PublishReconciler.reconcile(healthyInput()
                .repositoryObjectKeys(new LinkedHashSet<>(Set.of("e1"))).build()).verdict());

        assertEquals(ReconciliationVerdict.EXTRA_OBJECT, PublishReconciler.reconcile(healthyInput()
                .repositoryObjectKeys(new LinkedHashSet<>(Set.of("e1", "e2", "e3"))).build()).verdict());

        assertEquals(ReconciliationVerdict.CONTENT_DRIFT, PublishReconciler.reconcile(healthyInput()
                .repositoryContentDigest("sha256:bbb").build()).verdict());

        assertEquals(ReconciliationVerdict.DEPENDENCY_MISMATCH,
                PublishReconciler.reconcile(healthyInput().dependencyMatched(false).build()).verdict());

        assertEquals(ReconciliationVerdict.UNVERIFIABLE,
                PublishReconciler.reconcile(ReconciliationInput.builder().outcome(HEALTHY).build()).verdict());

        DualWriteOutcome partial = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.FAILED, LegOutcome.NOT_APPLICABLE)
                .gitRef("abc123").build();
        assertEquals(ReconciliationVerdict.PARTIAL_PUBLISH, PublishReconciler.reconcile(
                ReconciliationInput.builder().outcome(partial).build()).verdict());
    }

    @Test
    @DisplayName("伪成功形态不成套：SUCCEEDED 却无 gitRef / versionRef → PARTIAL_PUBLISH")
    void successWithoutEvidencePointerIsNotConsistent() {
        DualWriteOutcome noRefs = DualWriteOutcome.builder(
                LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE).build();
        PublishReconciler.Result r = PublishReconciler.reconcile(
                ReconciliationInput.builder().outcome(noRefs)
                        .authoritativeObjectKeys(Set.of())
                        .repositoryObjectKeys(Set.of())
                        .authoritativeContentDigest("x")
                        .repositoryContentDigest("x")
                        .versionRefMatched(true)
                        .dependencyMatched(true)
                        .build());
        assertEquals(ReconciliationVerdict.PARTIAL_PUBLISH, r.verdict());
        assertFalse(r.reason().isBlank());
    }

    @Test
    @DisplayName("关键安全变更：审计腿不适用不放行（fail-closed）")
    void securityCriticalRequiresAuditSuccess() {
        DualWriteOutcome critical = DualWriteOutcome.builder(
                        LegOutcome.SUCCEEDED, LegOutcome.SUCCEEDED, LegOutcome.NOT_APPLICABLE)
                .gitRef("abc123").versionRef("v2").securityCritical(true).build();
        assertEquals(ReconciliationVerdict.PARTIAL_PUBLISH, PublishReconciler.reconcile(
                ReconciliationInput.builder().outcome(critical).build()).verdict());
        assertTrue(critical.anyLegFailed() == false);
    }
}
