package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dq.DqRowGate;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportRequest;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportResult;
import com.chinacreator.gzcm.engine.data.fact.model.RowRejection;
import com.chinacreator.gzcm.engine.data.fact.period.PeriodLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FactImportAcceptRejectTest — 50 accepted 行全部 PASSED 入库（is_active=1 + batch_id 回填），
 * 行级被拒行不进 INSERT（C.5 控制流）。全 mock DAO，不连 PG。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FactImportAcceptRejectTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;

    private final DqRowGate lenientGate = (type, rows, ctx) -> java.util.Collections.emptyList();
    private final PeriodLock openLock = period -> false;

    @org.junit.jupiter.api.BeforeEach
    void stubIntReturners() {
        lenient().when(stageMapper.insertBatch(any())).thenReturn(100);
        lenient().when(attributionMapper.insertBatch(any())).thenReturn(100);
        lenient().when(resourceMapper.insertBatch(any())).thenReturn(100);
        lenient().when(costMapper.insertBatch(any())).thenReturn(100);
        lenient().when(forecastMapper.insertBatch(any())).thenReturn(100);
    }

    private FactImportService service() {
        return new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, lenientGate, openLock, Optional.empty(), 0.9);
    }

    private static Map<String, Object> stageRow(String project, int i) {
        return Map.of(
                "projectId", "p-001",
                "departmentId", "d-" + i,
                "period", "2025-03",
                "stage", "ACCEPTANCE",
                "factType", "ACTUAL",
                "contractBase", "100000.00",
                "attributionRatio", "0.6000",
                "amount", (60000.00 + i),
                "currency", "CNY",
                "sourceType", "IMPORT");
    }

    @Test
    @DisplayName("50 行全通过 → 全部 PASSED 入库，is_active=1 + batch_id 回填")
    void allFiftyAccepted() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            rows.add(stageRow("p-001", i));
        }
        FactImportRequest req = new FactImportRequest();
        req.setRows(rows);
        req.setBatchId("batch-abc");

        when(stageMapper.insertBatch(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        FactImportResult result = service().importFacts(FactType.STAGE, req);

        assertEquals(50, result.getAccepted(), "全部行应 accepted");
        assertTrue(result.getRejected().isEmpty(), "无行应被拒");
        assertEquals("batch-abc", result.getBatchId());

        org.mockito.ArgumentCaptor<List<Map<String, Object>>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(stageMapper, atLeastOnce()).insertBatch(cap.capture());
        List<Map<String, Object>> inserted = cap.getValue();
        assertEquals(50, inserted.size());
        for (Map<String, Object> r : inserted) {
            assertEquals("PASSED", r.get("dqStatus"), "C.5: accepted → PASSED");
            assertEquals(1, r.get("isActive"), "F02-06-3: is_active=1");
            assertEquals("batch-abc", r.get("batchId"), "batch_id 回填");
            assertEquals("1", String.valueOf(r.get("versionNo")), "DR07 version_no=1");
            assertTrue(r.containsKey("id") && !String.valueOf(r.get("id")).isBlank(), "MC01 应用侧 UUID");
        }
    }

    @Test
    @DisplayName("行级被拒行（gate 返回 rowNo=2）不进 INSERT，其余入 PASSED")
    void rejectedRowsNotPersisted() {
        DqRowGate rejectGate = (type, rows, ctx) ->
                List.of(new RowRejection(2, "attributionRatio", "DQ-F02",
                        "ratio 越界", "改为 0~1"));
        FactImportService svc = new FactImportService(attributionMapper, stageMapper,
                resourceMapper, costMapper, forecastMapper, rejectGate, openLock,
                Optional.empty(), 0.9);
        when(stageMapper.insertBatch(any())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(stageRow("p-001", i));
        }
        FactImportRequest req = new FactImportRequest();
        req.setRows(rows);
        req.setBatchId("b-2");

        FactImportResult result = svc.importFacts(FactType.STAGE, req);

        assertEquals(4, result.getAccepted());
        assertEquals(1, result.getRejected().size());
        assertEquals("DQ-F02", result.getRejected().get(0).ruleId());
        assertEquals(2, result.getRejected().get(0).rowNo());
        org.mockito.ArgumentCaptor<List<Map<String, Object>>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(stageMapper).insertBatch(cap.capture());
        assertEquals(4, cap.getValue().size(), "被拒行不得入库");
    }
}
