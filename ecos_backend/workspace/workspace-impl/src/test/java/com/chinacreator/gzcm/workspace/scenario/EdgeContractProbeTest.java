package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-02/F07-03-2 present 语义验收：present=两端绑定 ∧ 存在连边行 ∧ 契约通过；
 * 探测失败一律降级为 missing（携 probeError），绝不向上抛出让端点 500。纯内存，无 DB/Spring。
 */
class EdgeContractProbeTest {

    private static final RequiredEdge MAPPING =
            new RequiredEdge(EdgeType.MAPPING, BindingType.DATASET, BindingType.OBJECT_TYPE);
    private static final Instant VERIFIED_AT = Instant.parse("2026-09-29T00:00:00Z");

    @SuppressWarnings("unchecked")
    private EdgeContractProbe probe(ContractProbeFacade facade) {
        ObjectProvider<ContractProbeFacade> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(facade);
        return new EdgeContractProbe(provider);
    }

    @Test
    @DisplayName("present 需两端均绑定 + 有连边行 + 契约校验通过（四者缺一即 missing）")
    void presentEdgeRequiresBothEndpointsBoundAndContractVerified() {
        EdgeContractProbe p = probe(edge -> new ContractSnapshot(12L, VERIFIED_AT));

        ProbeResult allGood = p.probe(MAPPING, true, true, true);
        assertTrue(allGood.isPresent(), "两端绑定+连边+契约通过才 present");
        assertEqualsCount(12L, allGood);

        assertFalse(p.probe(MAPPING, false, true, true).isPresent(), "源未绑定 → missing");
        assertFalse(p.probe(MAPPING, true, false, true).isPresent(), "目标未绑定 → missing");
        assertFalse(p.probe(MAPPING, true, true, false).isPresent(), "无连边行 → missing");

        EdgeContractProbe unverified = probe(edge -> new ContractSnapshot(0L, VERIFIED_AT));
        assertFalse(unverified.probe(MAPPING, true, true, true).isPresent(), "契约计数 0 → missing");

        EdgeContractProbe neverVerified = probe(edge -> new ContractSnapshot(5L, null));
        assertFalse(neverVerified.probe(MAPPING, true, true, true).isPresent(), "从未校验 → missing");
    }

    @Test
    @DisplayName("探测异常 → 该边 present=false 且带 probeError，不抛出（端点仍 200）")
    void probeFailureMarksEdgeMissingNotError() {
        ContractProbeFacade boom = edge -> {
            throw new IllegalStateException("kb_engine_timeout");
        };
        EdgeContractProbe p = probe(boom);

        ProbeResult r = p.probe(MAPPING, true, true, true);

        assertFalse(r.isPresent(), "探测失败绝不判 present");
        assertNotNull(r.getProbeError(), "探测失败原因需回传（供 probeErrors）");
        assertTrue(r.getProbeError().contains("kb_engine_timeout"));
    }

    @Test
    @DisplayName("探测门面未装配 → missing 且无 probeError（诚实缺省，非 500）")
    void missingFacadeYieldsMissingWithoutError() {
        EdgeContractProbe p = probe(null);
        ProbeResult r = p.probe(MAPPING, true, true, true);
        assertFalse(r.isPresent());
        assertNull(r.getProbeError());
    }

    private void assertEqualsCount(long expected, ProbeResult r) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, r.getContractCount());
    }
}
