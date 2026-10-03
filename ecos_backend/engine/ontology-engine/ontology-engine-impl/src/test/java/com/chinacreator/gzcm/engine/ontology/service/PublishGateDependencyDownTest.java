package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import com.chinacreator.gzcm.engine.ontology.gate.DatanetColumnGuard;
import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateResult;
import com.chinacreator.gzcm.engine.ontology.gate.PublishGateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F03-03 依赖不可用（W75/C59 P0）：
 * <ul>
 *   <li>V3 调 data-engine REST 抛 DataAccessException → fail-closed 503 ECOS-ONTO-050（禁默认通过）；</li>
 *   <li>口径服务同 JVM 宿主异常（isAvailable=false）→ fail-closed 500 ECOS-ONTO-042（F03-01）；</li>
 *   <li>两种状态不互相干扰，前者 dependencyErrors 非空，后者 serviceErrorCode 非空。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishGateDependencyDownTest {

    @Mock CaliberService caliberService;
    @Mock DataNetResourceClient datanetClient;

    @Test
    @DisplayName("V3 依赖不可用（DataNetResourceClient 抛 DataAccessException）→ 503 ECOS-ONTO-050，passed=false")
    void v3DependencyDown() {
        when(datanetClient.listMetadataFields(anyString()))
                .thenThrow(new DataAccessException("down", null));
        GateContext ctx = new GateContext();
        List<GateContext.LineageRef> refs = new ArrayList<>();
        refs.add(new GateContext.LineageRef("res-1", "revenue"));
        ctx.setLineageRefs(refs);

        DatanetColumnGuard.Outcome o = new DatanetColumnGuard(datanetClient).evaluate(refs);
        assertTrue(o.isDependencyDown());
        GateResult r = o.getDependencyDown();
        assertFalse(r.isPassed());
        assertTrue(r.hasDependencyErrors());
        assertEquals("ECOS-ONTO-050", r.getDependencyErrors().get(0).code);
    }

    @Test
    @DisplayName("口径服务不可用：CaliberService.isAvailable=false → Validate 直接短路 500 ECOS-ONTO-042")
    void caliberUnavailable() {
        when(caliberService.isAvailable()).thenReturn(false);
        PublishGateService svc = new PublishGateService(new MetricUnitAnalyzer(), caliberService,
                new DatanetColumnGuard(datanetClient));
        GateResult r = svc.validate("prop-1", "trace-1", new GateContext());
        assertFalse(r.isPassed());
        assertTrue(r.hasServiceError());
        assertEquals("ECOS-ONTO-042", r.getServiceErrorCode());
        assertEquals(500, r.getServiceHttpStatus());
    }

    @Test
    @DisplayName("口径服务在正常但 isAvailable() 中途抛 → 上层 fail-closed（模拟同 JVM 宿主异常）")
    void caliberThrowsOnValidate() {
        // isAvailable() 走 true（可用）；校准 isVersionApproved 中途抛（同 JVM 宿主异常场景）
        when(caliberService.isAvailable()).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(caliberService).isVersionApproved(anyString(), anyString());
        PublishGateService svc = new PublishGateService(new MetricUnitAnalyzer(), caliberService,
                new DatanetColumnGuard(datanetClient));

        GateContext ctx = new GateContext();
        GateContext.MetricRef m = new GateContext.MetricRef();
        m.code = "M_X";
        m.caliberId = "CAL";
        m.formulaVersion = "1.0";
        ctx.setMetrics(List.of(m));

        GateResult r = svc.validate("prop-1", "trace-1", ctx);
        assertFalse(r.isPassed());
        assertTrue(r.hasServiceError(), "V1 内部异常应走 serviceUnavailable 而非依赖不可用");
        assertEquals("ECOS-ONTO-042", r.getServiceErrorCode());
    }

    @Test
    @DisplayName("综合：V1 APPROVED + V2 通过 + V3 依赖 down → 拦截在 V3 依赖层，503 ECOS-ONTO-050")
    void combinedV3DownStillBlocks() {
        when(caliberService.isAvailable()).thenReturn(true);
        when(caliberService.isVersionApproved("CAL", "1.0")).thenReturn(true);
        when(datanetClient.listMetadataFields(anyString()))
                .thenThrow(new DataAccessException("down", null));

        PublishGateService svc = new PublishGateService(new MetricUnitAnalyzer(), caliberService,
                new DatanetColumnGuard(datanetClient));
        GateContext ctx = new GateContext();
        GateContext.MetricRef m = new GateContext.MetricRef();
        m.code = "M_X"; m.caliberId = "CAL"; m.formulaVersion = "1.0";
        m.expression = "a"; m.aggregation = "SUM";
        ctx.setMetrics(List.of(m));
        List<GateContext.LineageRef> refs = new ArrayList<>();
        refs.add(new GateContext.LineageRef("res-1", "revenue"));
        ctx.setLineageRefs(refs);

        GateResult r = svc.validate("prop-1", "trace-1", ctx);
        assertFalse(r.isPassed());
        assertTrue(r.hasDependencyErrors(), "V3 依赖 down 必优先返回 dependencyErrors（503）");
        assertEquals("ECOS-ONTO-050", r.getDependencyErrors().get(0).code);
    }
}
