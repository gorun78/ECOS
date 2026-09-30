package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dq.DqRowGate;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportRequest;
import com.chinacreator.gzcm.engine.data.fact.period.PeriodLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * FactBatchLimitTest — EN03 整批上限 ≤100 行，101 行 → 400 ECOS-DATA-023 且不触达任何写库。
 */
@ExtendWith(MockitoExtension.class)
class FactBatchLimitTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private final DqRowGate okGate = (t, rows, ctx) -> List.of();
    private final PeriodLock openLock = p -> false;

    private FactImportService service() {
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, openLock, Optional.empty(), 0.9);
    }

    private static FactImportRequest rows(String batchId, int n) {
        FactImportRequest req = new FactImportRequest();
        req.setBatchId(batchId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Map.of("projectId", "p-1", "departmentId", "d-1", "period", "2025-03",
                    "stage", "ACCEPTANCE", "factType", "ACTUAL",
                    "contractBase", "1000", "amount", 100.0, "currency", "CNY"));
        }
        req.setRows(list);
        return req;
    }

    @Test
    @DisplayName("101 行 → 400 ECOS-DATA-023（超 EN03 上限）")
    void batchOver100Rejected() {
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, rows("b-1", 101)));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_023),
                "错误信息须含 ECOS-DATA-023，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("DQ-F05") || ex.getMessage().contains("100"),
                "整批 400 需提示 EN03 上限 100");
        verify(stageMapper, never()).insertBatch(any());
        verify(attributionMapper, never()).insertBatch(any());
        verify(resourceMapper, never()).insertBatch(any());
        verify(costMapper, never()).insertBatch(any());
        verify(forecastMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("rows 为空 → 400（不触库）")
    void emptyBatchRejected() {
        FactImportRequest req = new FactImportRequest();
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, req));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_023));
        verify(stageMapper, never()).insertBatch(any());
    }
}
