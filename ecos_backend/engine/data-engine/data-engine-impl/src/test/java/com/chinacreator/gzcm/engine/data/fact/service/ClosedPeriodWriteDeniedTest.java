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
import com.chinacreator.gzcm.engine.data.fact.period.OpenPeriodsPeriodLock;
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
 * ClosedPeriodWriteDeniedTest — F02-06-5：关账期写入拒 → 409 ECOS-DATA-022。
 *
 * <p>真实 PeriodLock 归 09 册 FC-05（未建）；本测试以 {@link OpenPeriodsPeriodLock}
 * 的 {@code ecos.data.facts.closed-periods} 属性模拟关账期（运维/测试态覆盖路径）。
 */
@ExtendWith(MockitoExtension.class)
class ClosedPeriodWriteDeniedTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private final DqRowGate okGate = (t, rows, ctx) -> List.of();
    /** 属性模拟：2025-01 ~ 2025-03 已关账（等效 ecos.data.facts.closed-periods=2025-01,2025-02,2025-03） */
    private final PeriodLock closedLock = new OpenPeriodsPeriodLock("2025-01,2025-02,2025-03");

    private FactImportService service() {
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, closedLock, Optional.empty(), 0.9);
    }

    private static FactImportRequest stageReq(String period) {
        FactImportRequest req = new FactImportRequest();
        req.setBatchId("b-closed");
        req.setRows(List.of(Map.of("projectId", "p-1", "departmentId", "d-1",
                "period", period, "stage", "ACCEPTANCE", "factType", "ACTUAL",
                "contractBase", "1000", "amount", 100.0, "currency", "CNY")));
        return req;
    }

    @Test
    @DisplayName("行期间已关账（period=2025-01）→ 409 ECOS-DATA-022，不入库")
    void rowInClosedPeriodRejected() {
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, stageReq("2025-01")));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_022),
                "须含 ECOS-DATA-022，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("2025-01"), "须指明关账期间");
        verify(stageMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("批次 level period 关账 → 409 ECOS-DATA-022")
    void batchPeriodClosedRejected() {
        FactImportRequest req = stageReq("2025-04");
        req.setPeriod("2025-02");
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service().importFacts(FactType.STAGE, req));
        assertTrue(ex.getMessage().contains(DataDomainErrorCode.ECOS_DATA_022));
        verify(stageMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("开放期间 → 不受 PeriodLock 阻断（后续 gate/insert 正常）")
    void openPeriodAuthorized() {
        FactImportService svc = service();
        // 只验证 pre-check 不抛（insert 由其他测试覆盖）
        FactImportRequest req = stageReq("2025-04");
        assertDoesNotThrow(() ->
                svc.checkPeriodsNotClosed(FactType.STAGE, req.getRows(), req.getPeriod()));
    }

    @Test
    @DisplayName("OpenPeriodsPeriodLock 未配置 closed-periods → 全开放")
    void defaultLockNeverClosed() {
        PeriodLock open = new OpenPeriodsPeriodLock("");
        assertDoesNotThrow(() -> {
            assertTrue(!open.isClosed("2025-01"));
            assertTrue(!open.isClosed("1999-12"));
            assertTrue(!open.isClosed(null));
        });
        OpenPeriodsPeriodLock cfg = new OpenPeriodsPeriodLock("2025-01, 2025-02 ");
        assertTrue(cfg.isClosed("2025-01"));
        assertTrue(cfg.isClosed("2025-02"));
        assertTrue(!cfg.isClosed("2025-03"));
    }
}
