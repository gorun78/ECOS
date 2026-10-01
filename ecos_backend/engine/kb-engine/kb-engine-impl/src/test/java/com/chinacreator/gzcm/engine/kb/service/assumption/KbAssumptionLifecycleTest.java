package com.chinacreator.gzcm.engine.kb.service.assumption;

import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-07 假设库版本化验收（REQ-KB-03，P0 新能力）。
 *
 * <p>状态机 {@code DRAFT → PENDING_APPROVAL → ACTIVE → [EXPIRED | SUPERSEDED]}（仅 ACTIVE 可引用；
 * DELTA 语义相对基准；reason 必填；evidence_ref 缺失 → unverified）。
 * 纯内存 fake store，禁 {@code @SpringBootTest}。
 */
class KbAssumptionLifecycleTest {

    /** 内存 fake：保留引用以便状态变更在同一对象上可见。 */
    static final class FakeStore implements KbAssumptionStore {
        final List<KbAssumptionDO> rows = new ArrayList<>();

        @Override
        public List<KbAssumptionDO> query(String scenario, String metric, String status) {
            List<KbAssumptionDO> out = new ArrayList<>();
            for (KbAssumptionDO r : rows) {
                if (r.getIsDeleted() != 0) continue;
                if (scenario != null && !scenario.isBlank() && !scenario.equals(r.getScenarioType())) continue;
                if (metric != null && !metric.isBlank() && !metric.equals(r.getMetricCode())) continue;
                if (status != null && !status.isBlank() && !status.equals(r.getStatus())) continue;
                out.add(r);
            }
            return out;
        }

        @Override
        public KbAssumptionDO findLatestByKey(String key) {
            KbAssumptionDO best = null;
            for (KbAssumptionDO r : rows) {
                if (r.getIsDeleted() == 0 && key.equals(r.getAssumptionKey())) {
                    if (best == null || r.getUpdateTime() >= best.getUpdateTime()) {
                        best = r;
                    }
                }
            }
            return best;
        }

        @Override
        public KbAssumptionDO findById(String id) {
            for (KbAssumptionDO r : rows) {
                if (id.equals(r.getId())) return r;
            }
            return null;
        }

        @Override
        public void insert(KbAssumptionDO row) {
            if (findById(row.getId()) == null) {
                rows.add(row);
            }
        }
    }

    private KbAssumptionDO draft(String scenario, String metric, String value, String semantic,
                                 String baseId, String evidence, String reason) {
        KbAssumptionDO d = new KbAssumptionDO();
        d.setScenarioType(scenario);
        d.setMetricCode(metric);
        d.setDimScopeJson("{}");
        d.setValueType("RATE");
        d.setValue(new BigDecimal(value));
        d.setValueSemantic(semantic);
        d.setBaseAssumptionId(baseId);
        d.setValidFrom("2026-01");
        d.setValidTo("2026-12");
        d.setReason(reason);
        d.setEvidenceRef(evidence);
        d.setDomain("kb");
        return d;
    }

    /** 便捷：create→submit→approve 得到 ACTIVE 行。 */
    private KbAssumptionDO makeActive(KbAssumptionService svc, KbAssumptionDO d) {
        KbAssumptionDO created = svc.create(d, "business-owner");
        svc.submit(created.getId(), "business-owner");
        return svc.approve(created.getId(), "approver");
    }

    @Test
    void threeScenariosResolveCorrectly() {
        // BASE(ABSOLUTE=10) + CONSERVATIVE(DELTA=-2) + AGGRESSIVE(DELTA=+3)，全 ACTIVE
        FakeStore store = new FakeStore();
        KbAssumptionService svc = new KbAssumptionService(store);

        KbAssumptionDO base = makeActive(svc,
                draft("BASE", "defect_rate", "10.0", KbAssumptionService.SEMANTIC_ABSOLUTE, null,
                        "report/2026", "基线缺陷率"));
        KbAssumptionDO cons = makeActive(svc,
                draft("CONSERVATIVE", "defect_rate", "-2.0", KbAssumptionService.SEMANTIC_DELTA,
                        base.getId(), "report/2026", "保守：相对基线下调"));
        KbAssumptionDO aggr = makeActive(svc,
                draft("AGGRESSIVE", "defect_rate", "3.0", KbAssumptionService.SEMANTIC_DELTA,
                        base.getId(), "report/2026", "进取：相对基线上调"));

        long now = System.currentTimeMillis();
        KbAssumptionService.ResolveResult baseR = svc.resolve("BASE", "defect_rate", "2026-06", now);
        assertNotNull(baseR.assumption());
        assertEquals(0, baseR.effectiveValue().compareTo(new BigDecimal("10.0")));
        assertFalse(baseR.expired());

        KbAssumptionService.ResolveResult consR = svc.resolve("CONSERVATIVE", "defect_rate", "2026-06", now);
        assertNotNull(consR.assumption());
        assertEquals(0, consR.effectiveValue().compareTo(new BigDecimal("8.0")));

        KbAssumptionService.ResolveResult aggrR = svc.resolve("AGGRESSIVE", "defect_rate", "2026-06", now);
        assertNotNull(aggrR.assumption());
        assertEquals(0, aggrR.effectiveValue().compareTo(new BigDecimal("13.0")));
    }

    @Test
    void approvedUpdateReturns409() {
        FakeStore store = new FakeStore();
        KbAssumptionService svc = new KbAssumptionService(store);
        KbAssumptionDO active = makeActive(svc,
                draft("BASE", "cycle_days", "5.0", KbAssumptionService.SEMANTIC_ABSOLUTE, null,
                        "rep", "基准周期"));
        KbErrorCodes.DomainException ex = assertThrows(KbErrorCodes.DomainException.class,
                () -> svc.assertUpdatable(active.getId()));
        assertEquals("ECOS-KB-071", ex.getCode());
    }

    @Test
    void expiredAssumptionNotResolved() {
        FakeStore store = new FakeStore();
        KbAssumptionService svc = new KbAssumptionService(store);
        KbAssumptionDO active = makeActive(svc,
                draft("BASE", "cycle_days", "5.0", KbAssumptionService.SEMANTIC_ABSOLUTE,
                        null, "rep", "基准"));
        // ACTIVE 但到期时间已过 → 引用判 EXPIRED（resolve 内 expireAt < now）
        active.setExpireAt(System.currentTimeMillis() - 1000L);
        KbAssumptionService.ResolveResult r =
                svc.resolve("BASE", "cycle_days", "2026-06", System.currentTimeMillis());
        assertNotNull(r.assumption());
        assertTrue(r.expired());
        assertNull(r.effectiveValue());
    }

    @Test
    void missingEvidenceRefMarksUnverified() {
        FakeStore store = new FakeStore();
        KbAssumptionService svc = new KbAssumptionService(store);

        KbAssumptionDO noEv = svc.create(
                draft("BASE", "cost_index", "1.2", KbAssumptionService.SEMANTIC_ABSOLUTE,
                        null, null, "无证据来源"), "owner");
        assertTrue(noEv.isUnverifiedFlag());
        assertEquals(Integer.valueOf(1), noEv.getIsUnverified());

        KbAssumptionDO withEv = svc.create(
                draft("BASE", "cost_index2", "1.2", KbAssumptionService.SEMANTIC_ABSOLUTE,
                        null, "https://rep/1", "有证据"), "owner");
        assertFalse(withEv.isUnverifiedFlag());
        assertEquals(Integer.valueOf(0), withEv.getIsUnverified());
    }
}
