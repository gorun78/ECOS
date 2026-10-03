package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import com.chinacreator.gzcm.engine.ontology.gate.DatanetColumnGuard;
import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateResult;
import com.chinacreator.gzcm.engine.ontology.gate.GateViolation;
import com.chinacreator.gzcm.engine.ontology.gate.MetricCaliberGuard;
import com.chinacreator.gzcm.engine.ontology.gate.PublishGateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * F03-03 V1 口径门禁（W75/C59 P0）：
 * <ul>
 *   <li>缺 caliber_id → ECOS-ONTO-040（禁止"跳过口径"）；</li>
 *   <li>缺 formula_version → ECOS-ONTO-040；</li>
 *   <li>版本非 APPROVED → ECOS-ONTO-040；</li>
 *   <li>空上下文 → 无违规（无指标即无 V1 判据）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishGateV1Test {

    @Mock CaliberService caliberService;
    @Mock DataNetResourceClient datanetClient;

    private GateContext ctx(GateContext.MetricRef... refs) {
        GateContext c = new GateContext();
        c.setMetrics(List.of(refs));
        return c;
    }

    private GateContext.MetricRef ref(String code, String cal, String ver, String expr, String agg) {
        GateContext.MetricRef r = new GateContext.MetricRef();
        r.code = code; r.caliberId = cal; r.formulaVersion = ver;
        r.expression = expr; r.aggregation = agg;
        return r;
    }

    @Test
    @DisplayName("缺 caliber_id → 违反 ECOS-ONTO-040")
    void missingCaliberId() {
        MetricCaliberGuard v1 = new MetricCaliberGuard(caliberService);
        List<GateViolation> vs = v1.evaluate(ctx(ref("M_X", null, "1.0", "a", "SUM")));
        assertEquals(1, vs.size());
        assertEquals("ECOS-ONTO-040", vs.get(0).getCode());
    }

    @Test
    @DisplayName("缺 formula_version → ECOS-ONTO-040 另一分支")
    void missingFormulaVersion() {
        MetricCaliberGuard v1 = new MetricCaliberGuard(caliberService);
        List<GateViolation> vs = v1.evaluate(ctx(ref("M_X", "CAL", null, "a", "SUM")));
        assertEquals(1, vs.size());
        assertEquals("ECOS-ONTO-040", vs.get(0).getCode());
        assertFalse(vs.get(0).getMessage().contains("APPROVED"), "缺 formula_version 分支不查 APPROVED");
    }

    @Test
    @DisplayName("版本非 APPROVED → ECOS-ONTO-040（V1 核心）")
    void unapprovedVersion() {
        when(caliberService.isVersionApproved("CAL", "1.0")).thenReturn(false);
        MetricCaliberGuard v1 = new MetricCaliberGuard(caliberService);
        List<GateViolation> vs = v1.evaluate(ctx(ref("M_X", "CAL", "1.0", "a", "SUM")));
        assertEquals(1, vs.size());
        assertEquals("ECOS-ONTO-040", vs.get(0).getCode());
        assertTrue(vs.get(0).getMessage().contains("APPROVED"));
    }

    @Test
    @DisplayName("版本 APPROVED → V1 通过")
    void approvedVersion() {
        when(caliberService.isVersionApproved("CAL", "1.0")).thenReturn(true);
        MetricCaliberGuard v1 = new MetricCaliberGuard(caliberService);
        assertTrue(v1.evaluate(ctx(ref("M_X", "CAL", "1.0", "a", "SUM"))).isEmpty());
    }

    @Test
    @DisplayName("空上下文：无指标 → V1 空违规")
    void emptyCtx() {
        MetricCaliberGuard v1 = new MetricCaliberGuard(caliberService);
        assertTrue(v1.evaluate(new GateContext()).isEmpty());
    }

    @Test
    @DisplayName("PublishGateService 聚合：唯一失效来源为 V1 时 → 返回 400 语义，非依赖不可用")
    void aggregatedViaPublishGate() {
        when(caliberService.isAvailable()).thenReturn(true);
        when(caliberService.isVersionApproved("CAL", "1.0")).thenReturn(false);
        PublishGateService svc = new PublishGateService(new MetricUnitAnalyzer(), caliberService,
                new DatanetColumnGuard(datanetClient));
        GateResult r = svc.validate("prop-1", "trace-1",
                ctx(ref("M_X", "CAL", "1.0", "a", "SUM")));
        assertFalse(r.isPassed());
        assertFalse(r.hasDependencyErrors(), "V1 违规不应被误判为依赖不可用");
        assertEquals("ECOS-ONTO-040", r.getViolations().get(0).getCode());
    }
}

