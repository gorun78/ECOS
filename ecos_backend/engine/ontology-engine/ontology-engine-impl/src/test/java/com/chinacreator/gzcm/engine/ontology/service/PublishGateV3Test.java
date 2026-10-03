package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.client.DataNetResourceClient;
import com.chinacreator.gzcm.engine.ontology.gate.DatanetColumnGuard;
import com.chinacreator.gzcm.engine.ontology.gate.GateContext;
import com.chinacreator.gzcm.engine.ontology.gate.GateResult;
import com.chinacreator.gzcm.engine.ontology.gate.GateViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F03-03 V3 血缘列门禁（W75/C59 P0）：经 data-engine 元数据 REST 校验，禁止跨 schema 直查。
 * 列不存在 → ECOS-ONTO-030 拒绝（依赖可用时）；不存在/数据引擎不可用 → 依赖 503（见 DependencyDownTest）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishGateV3Test {

    @Mock DataNetResourceClient datanetClient;

    private DatanetColumnGuard guard() { return new DatanetColumnGuard(datanetClient); }

    @Test
    @DisplayName("列存在 → V3 通过（case-sensitive 命中 physicalColumn 或 fieldName）")
    void columnPresent() {
        when(datanetClient.listMetadataFields("res-1")).thenReturn(List.of(
                Map.of("resourceId", "res-1", "physicalColumn", "revenue")
        ));
        List<GateContext.LineageRef> refs = new java.util.ArrayList<>();
        refs.add(new GateContext.LineageRef("res-1", "revenue"));
        DatanetColumnGuard.Outcome o = guard().evaluate(refs);
        assertFalse(o.isDependencyDown());
        assertTrue(o.getViolations().isEmpty());
    }

    @Test
    @DisplayName("列不存在 → ECOS-ONTO-030 拒绝，落单条 violation")
    void columnMissing() {
        when(datanetClient.listMetadataFields("res-1")).thenReturn(List.of(
                Map.of("physicalColumn", "revenue")
        ));
        List<GateContext.LineageRef> refs = new java.util.ArrayList<>();
        refs.add(new GateContext.LineageRef("res-1", "cost"));
        DatanetColumnGuard.Outcome o = guard().evaluate(refs);
        assertFalse(o.isDependencyDown());
        assertEquals(1, o.getViolations().size());
        assertEquals("ECOS-ONTO-030", o.getViolations().get(0).getCode());
    }

    @Test
    @DisplayName("多个缺失列 → 多条 violation，聚合到 GateResult 拒绝（作为 GateResult.of 正常违规形态）")
    void multipleMissing() {
        when(datanetClient.listMetadataFields(anyString())).thenReturn(List.of(
                Map.of("physicalColumn", "revenue")));
        List<GateContext.LineageRef> refs = new java.util.ArrayList<>();
        refs.add(new GateContext.LineageRef("res-1", "cost"));
        refs.add(new GateContext.LineageRef("res-1", "quantity"));
        DatanetColumnGuard.Outcome o = guard().evaluate(refs);
        assertEquals(2, o.getViolations().size());
        for (GateViolation v : o.getViolations()) {
            assertEquals("ECOS-ONTO-030", v.getCode());
        }
    }

    @Test
    @DisplayName("空 lineage 列表 → V3 直接通过（无判据）")
    void emptyRefs() {
        DatanetColumnGuard.Outcome o = guard().evaluate(java.util.Collections.emptyList());
        assertFalse(o.isDependencyDown());
        assertTrue(o.getViolations().isEmpty());
    }
}
