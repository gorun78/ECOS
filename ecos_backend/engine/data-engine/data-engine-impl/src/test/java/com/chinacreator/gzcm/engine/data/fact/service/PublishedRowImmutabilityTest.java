package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PublishedRowImmutabilityTest — F02-06-4：PUBLISHED 行任何更新路径拒 → 409 ECOS-DATA-021。
 *
 * <p>本撮唯一更新通道 = action-outcomes（touchUpdate）。验证：
 * getDqStatus=PUBLISHED → guardPublished 抛 409；非 PUBLISHED（PASSED）→ 放行 touch。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublishedRowImmutabilityTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private FactImportService service() {
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper,
                (t, rows, ctx) -> java.util.List.of(),
                p -> false, Optional.empty(), 0.9);
    }

    @Test
    @DisplayName("PUBLISHED 行写入 outcome → 409 ECOS-DATA-021，不触 update_time/update_by")
    void publishedRowOutcomeRejected() {
        when(stageMapper.getDqStatusById("pub-1")).thenReturn("PUBLISHED");
        var ex = assertThrows(DataBridgeException.class, () -> service().writeActionOutcome(
                req("STAGE", "pub-1", "ADOPTED")));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_021),
                "必须落 ECOS-DATA-021，实际: " + ex.getMessage());
        verify(stageMapper, never()).touchUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("PASSED 行写入 outcome → 放行，update_time/update_by 触达 1 次")
    void passedRowOutcomeAllowed() {
        when(stageMapper.getDqStatusById("ok-1")).thenReturn("PASSED");
        when(stageMapper.touchUpdate(anyString(), anyString())).thenReturn(1);
        assertDoesNotThrow(() -> service().writeActionOutcome(req("STAGE", "ok-1", "DONE")));
        verify(stageMapper).touchUpdate(eq("ok-1"), anyString());
    }

    @Test
    @DisplayName("guardPublished 直调 — PUBLISHED 抛、PASSED 不抛")
    void guardDirect() {
        var svc = service();
        assertThrows(DataBridgeException.class,
                () -> svc.guardPublished(FactType.STAGE, "x", "PUBLISHED"));
        assertDoesNotThrow(() -> svc.guardPublished(FactType.STAGE, "x", "PASSED"));
        assertDoesNotThrow(() -> svc.guardPublished(FactType.STAGE, "x", "PENDING"));
    }

    @Test
    @DisplayName("action-outcome 缺 factId/outcome → 400")
    void missingFieldsRejected() {
        var r = req("STAGE", null, null);
        var ex = assertThrows(DataBridgeException.class, () -> service().writeActionOutcome(r));
        assertTrue(ex.getHttpStatus() == 400, "缺参应为 400，实际 " + ex.getHttpStatus());
        verify(stageMapper, never()).touchUpdate(anyString(), anyString());
    }

    private static com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest req(
            String type, String factId, String outcome) {
        com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest r =
                new com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest();
        r.setFactType(type);
        r.setFactId(factId);
        r.setOutcome(outcome);
        return r;
    }
}
