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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * DqF05BatchCurrencyTest — DQ-F05 批级不变量（本撮内建 pre-check）：
 * 同批多币种 → 整批 400 ECOS-DATA-023（ruleId DQ-F05），不触库。
 */
@ExtendWith(MockitoExtension.class)
class DqF05BatchCurrencyTest {

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

    private static Map<String, Object> row(String currency) {
        return Map.of("projectId", "p-1", "departmentId", "d-1", "period", "2025-03",
                "stage", "ACCEPTANCE", "factType", "ACTUAL",
                "contractBase", "1000", "amount", 100.0,
                "currency", currency);
    }

    @Test
    @DisplayName("批内 CNY+USD 两币种 → 400，消息含 DQ-F05 + ECOS-DATA-023，不入库")
    void mixedCurrenciesWholeBatchRejected() {
        FactImportRequest req = new FactImportRequest();
        req.setBatchId("b-f05");
        req.setRows(List.of(row("CNY"), row("USD"), row("cny")));

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, req));
        assertTrue(ex.getMessage().contains("DQ-F05"), "须含 ruleId DQ-F05，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_023),
                "须含 ECOS-DATA-023");
        verify(stageMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("批次声明币种 CNY 与行内 USD 冲突 → 400 DQ-F05")
    void declaredCurrencyConflict() {
        FactImportRequest req = new FactImportRequest();
        req.setBatchId("b-f05b");
        req.setCurrency("CNY");
        req.setRows(List.of(row("USD")));

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, req));
        assertTrue(ex.getMessage().contains("DQ-F05"));
        verify(stageMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("全批单币种（含大小写不敏感）→ DQ-F05 不触发")
    void singleCurrencyPasses() {
        FactImportService svc = service();
        FactImportRequest req = new FactImportRequest();
        req.setBatchId("b-ok");
        req.setCurrency("cny");
        req.setRows(List.of(row("CNY"), row("cny")));
        // package-private 直接调 pre-check：不抛 = DQ-F05 通过
        assertDoesNotThrow(() -> svc.checkDqF05BatchCurrency(req));
    }
}
