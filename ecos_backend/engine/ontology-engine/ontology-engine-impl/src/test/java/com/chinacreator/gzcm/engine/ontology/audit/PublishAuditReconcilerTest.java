package com.chinacreator.gzcm.engine.ontology.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 发布审计对账四态门（§2.9.4④ L95；#147AD）。
 *
 * <p>核心不被允许的假绿只有一条：<b>查询失败不得判 PERSISTED</b>（也不得判 MISSING——
 * 把「读不到」当「没写过」同样是假证据）。另三态各一枚：读回口缺席 = NOT_AVAILABLE、
 * 查无此行 = MISSING（含异步窗口）、查到行 = PERSISTED 且带 apply/verification。</p>
 *
 * @author ECOS Ontology #147AD
 */
class PublishAuditReconcilerTest {

    private static final String EVENT = "ont-pub-v-1-3";

    private static ObjectProvider<PublishAuditLookup> provider(PublishAuditLookup lookup) {
        return new ObjectProvider<>() {
            @Override
            public PublishAuditLookup getObject(Object... args) {
                return lookup;
            }

            @Override
            public PublishAuditLookup getObject() {
                return lookup;
            }

            @Override
            public PublishAuditLookup getIfAvailable() {
                return lookup;
            }

            @Override
            public PublishAuditLookup getIfUnique() {
                return lookup;
            }
        };
    }

    @Test
    @DisplayName("缺 eventId＝UNKNOWN（无标识无从对账，不得臆断已落库）")
    void blankEventIdIsUnknown() {
        PublishAuditReconciler reconciler = new PublishAuditReconciler(provider(mock(PublishAuditLookup.class)));

        assertEquals(PublishAuditReconciler.Verdict.UNKNOWN, reconciler.reconcile(" ").verdict());
        assertEquals(PublishAuditReconciler.Verdict.UNKNOWN, reconciler.reconcile(null).verdict());
    }

    @Test
    @DisplayName("读回口缺席＝NOT_AVAILABLE（语义是「无法对账」，不是「没写」）")
    void missingLookupIsNotAvailable() {
        PublishAuditReconciler reconciler = new PublishAuditReconciler(provider(null));

        assertEquals(PublishAuditReconciler.Verdict.NOT_AVAILABLE, reconciler.reconcile(EVENT).verdict());
    }

    @Test
    @DisplayName("查无此行＝MISSING（投递已受理但落库未发生，异步窗口内亦属此态）")
    void absentRowIsMissing() {
        PublishAuditLookup lookup = mock(PublishAuditLookup.class);
        when(lookup.findByEventId(EVENT)).thenReturn(Optional.empty());

        assertEquals(PublishAuditReconciler.Verdict.MISSING,
                new PublishAuditReconciler(provider(lookup)).reconcile(EVENT).verdict());
    }

    @Test
    @DisplayName("查到行＝PERSISTED，原因里带 apply/verification 原值")
    void presentRowIsPersisted() {
        PublishAuditLookup lookup = mock(PublishAuditLookup.class);
        when(lookup.findByEventId(EVENT))
                .thenReturn(Optional.of(new PublishAuditLookup.AuditRow("APPLIED", "PENDING")));

        PublishAuditReconciler.Reconciliation r = new PublishAuditReconciler(provider(lookup)).reconcile(EVENT);

        assertEquals(PublishAuditReconciler.Verdict.PERSISTED, r.verdict());
        assertTrue(r.detail().contains("APPLIED") && r.detail().contains("PENDING"), r.detail());
    }

    @Test
    @DisplayName("反证：查询抛异常＝UNKNOWN，绝不许判 PERSISTED 或 MISSING")
    void queryFailureNeverReadsAsPersistedOrMissing() {
        PublishAuditLookup broken = mock(PublishAuditLookup.class);
        when(broken.findByEventId(EVENT)).thenThrow(new RuntimeException("relation does not exist"));

        PublishAuditReconciler.Verdict verdict =
                new PublishAuditReconciler(provider(broken)).reconcile(EVENT).verdict();

        assertEquals(PublishAuditReconciler.Verdict.UNKNOWN, verdict);
        assertNotEquals(PublishAuditReconciler.Verdict.PERSISTED, verdict);
        assertNotEquals(PublishAuditReconciler.Verdict.MISSING, verdict);
    }
}
