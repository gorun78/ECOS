package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-07 F07-02/W166/C148 契约单源验收：{@link CompletenessReport} 是唯一完整度出参，
 * 前端只渲染不重算（破 X-25 空场景 100%、X-26 前端二次推导、X-29 双实现）。
 *
 * <p>本测试锁 <b>POJO 契约</b>（verdict 五态、coverage 可空、视图字段命名）；覆盖率算法本身
 * 见 {@link RequiredEdgeDerivationTest}；数据库端到端链 {@code ScenarioCompletenessServiceTest}
 * 依赖 {@code *IT}，本环境 §8.4 P-2 未成立记"未执行"。</p>
 */
class CompletenessReportTest {

    @Test
    @DisplayName("空场景 verdict=EMPTY_SCENARIO，coverage 必 null（禁 100%）")
    void emptyScenarioIsNullNotOneHundred() {
        CompletenessReport r = new CompletenessReport("s-1", null, CompletenessReport.Verdict.EMPTY_SCENARIO);
        assertNull(r.getCoverage(), "空场景 coverage 必须 null，破 X-25");
        assertSame(CompletenessReport.Verdict.EMPTY_SCENARIO, r.getVerdict());
    }

    @Test
    @DisplayName("无必需边 verdict=NOT_APPLICABLE，coverage 亦 null")
    void notApplicableIsNullNotNullOneHundred() {
        CompletenessReport r = new CompletenessReport("s-2", null, CompletenessReport.Verdict.NOT_APPLICABLE);
        assertNull(r.getCoverage());
        assertSame(CompletenessReport.Verdict.NOT_APPLICABLE, r.getVerdict());
    }

    @Test
    @DisplayName("OK 与 PARTIAL 携带 0~1 之间的 coverage；PARTIAL 附缺边视图")
    void okAndPartialCarryNumericCoverage() {
        CompletenessReport ok = new CompletenessReport("s-3", 1.0d, CompletenessReport.Verdict.OK);
        ok.getMissingEdges().add(new CompletenessReport.MissingEdgeView(
                "mapping", "workspace.completeness.missing.mapping", "endpoints_unbound"));
        assertEquals(1.0d, ok.getCoverage());

        CompletenessReport partial = new CompletenessReport("s-4", 2.0d / 3.0d, CompletenessReport.Verdict.PARTIAL);
        partial.getMissingEdges().add(new CompletenessReport.MissingEdgeView(
                "mapping", "workspace.completeness.missing.mapping", "no_contract_verified"));
        assertEquals(1, partial.getMissingEdges().size());
        assertTrue(partial.getCoverage() > 0.66 && partial.getCoverage() < 0.68);
    }

    @Test
    @DisplayName("presentEdges 视图带契约计数与最近校验时间（hover 数据，前端不再自行推导）")
    void presentEdgesCarryProbeMetadata() {
        CompletenessReport r = new CompletenessReport("s-5", 1.0d, CompletenessReport.Verdict.OK);
        Instant verifiedAt = Instant.parse("2026-09-29T00:00:00Z");
        r.getPresentEdges().add(new CompletenessReport.PresentEdgeView(
                "MAPPING", "ds-1", "ot-1", 12L, verifiedAt));
        CompletenessReport.PresentEdgeView e = r.getPresentEdges().get(0);
        assertEquals("MAPPING", e.getType());
        assertEquals(12L, e.getContractCount());
        assertNotNull(e.getLastVerifiedAt());
        assertEquals(verifiedAt, e.getLastVerifiedAt());
    }

    @Test
    @DisplayName("孤岛视图承载 bindingId/name/bindingType/reason（后端字段驱动，破 X-27 类型判定）")
    void islandViewFields() {
        CompletenessReport r = new CompletenessReport("s-6", 0.0d, CompletenessReport.Verdict.PARTIAL);
        r.getIslands().add(new CompletenessReport.IslandView(
                "b-1", "孤儿数据集", "DATASET", "no_edge_to_target"));
        CompletenessReport.IslandView island = r.getIslands().get(0);
        assertEquals("b-1", island.getBindingId());
        assertEquals("DATASET", island.getBindingType());
        assertEquals("no_edge_to_target", island.getReason());
    }

    @Test
    @DisplayName("probeErrors 允许非空而不使端点 500（F07-03-2 探测失败降级为 missing）")
    void probeErrorsDoNotFailRequest() {
        CompletenessReport r = new CompletenessReport("s-7", 0.0d, CompletenessReport.Verdict.PARTIAL);
        r.getProbeErrors().addAll(Arrays.asList("kb_engine_timeout", "ontology_unreachable"));
        r.getMissingEdges().add(new CompletenessReport.MissingEdgeView(
                "extraction", "workspace.completeness.missing.extraction", "probe_failed"));
        assertEquals(2, r.getProbeErrors().size());
        // 关键：probeErrors 非空但 verdict 仍是可判定的枚举，coverage 仍是数值/ null 二元
        assertSame(CompletenessReport.Verdict.PARTIAL, r.getVerdict());
        assertNotNull(r.getMissingEdges());
        assertEquals(Collections.singletonList("kb_engine_timeout"),
                Collections.singletonList(r.getProbeErrors().get(0)));
    }
}
