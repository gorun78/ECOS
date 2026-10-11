package com.chinacreator.gzcm.engine.data.fact.service;

import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.dao.ActionOutcomeMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.AttributionFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.CostFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ForecastSnapshotMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.ResourceFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dao.StageFactMapper;
import com.chinacreator.gzcm.engine.data.fact.dq.DqRowGate;
import com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest;
import com.chinacreator.gzcm.engine.data.fact.period.OpenPeriodsPeriodLock;
import com.chinacreator.gzcm.engine.data.model.ActionOutcomeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * outcome 写入可读（H-3 冻结测试类③）：
 * <ul>
 *   <li>writeActionOutcome 在旧形态（触达列+审计）之上<b>只增</b>物理落 V279 载体行（列值/系统列口径核验）；</li>
 *   <li>listActionOutcomes 按 forecastRunId / actionId 读回（snake→camel 无损）；</li>
 *   <li>双键皆空 → 400（禁全库扫描）；幂等键重放 → 首写回显、零触达零二次写；</li>
 *   <li>金额超 NUMERIC(18,2) 精度域 → 400 显式拒绝；</li>
 *   <li>mapper 未装配（V279 未应用环境）→ 旧形态行为保持（回归不破坏）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ActionOutcomeCarrierTest {

    @Mock private StageFactMapper stageMapper;
    @Mock private AttributionFactMapper attributionMapper;
    @Mock private ResourceFactMapper resourceMapper;
    @Mock private CostFactMapper costMapper;
    @Mock private ForecastSnapshotMapper forecastMapper;
    @Mock private ActionOutcomeMapper outcomeMapper;

    private final DqRowGate okGate = (t, rows, ctx) -> List.of();

    private FactImportService service;

    @BeforeEach
    void setUp() {
        service = new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, new OpenPeriodsPeriodLock(""),
                Optional.empty(), 0.9);
        service.setActionOutcomeMapper(outcomeMapper);
    }

    private static ActionOutcomeRequest carrierReq() {
        ActionOutcomeRequest req = new ActionOutcomeRequest();
        req.setFactType("STAGE");
        req.setFactId("f-1");
        req.setOutcome("ACCEPTED");
        req.setComment("复盘：实际影响低于预期");
        req.setBatchId("b-1");
        req.setActionId("act-9");
        req.setForecastRunId("run-77");
        req.setPeriod("2025-03");
        req.setExpectedImpact(new BigDecimal("120000.50"));
        req.setActualImpact(new BigDecimal("98000.00"));
        return req;
    }

    @Test
    @DisplayName("写入 → 物理载体行 INSERT（列口径：MC01 UUID/DR06~08/金额/币种缺省 CNY）")
    void writeActionOutcomeLandsPhysicalRow() {
        when(stageMapper.getDqStatusById("f-1")).thenReturn("PASSED");
        when(stageMapper.touchUpdate(eq("f-1"), anyString())).thenReturn(1);

        Map<String, Object> out = service.writeActionOutcome(carrierReq());

        assertNotNull(out.get("outcomeId"));
        assertEquals(Boolean.FALSE, out.get("alreadyRecorded"));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.captor();
        verify(outcomeMapper).insertRow(captor.capture());
        Map<String, Object> row = captor.getValue();
        assertNotNull(row.get("id"));
        assertEquals("run-77", row.get("forecastRunId"));
        assertEquals("act-9", row.get("actionId"));
        assertEquals("STAGE", row.get("factType"));
        assertEquals("f-1", row.get("factId"));
        assertEquals("2025-03", row.get("period"));
        assertEquals("ACCEPTED", row.get("outcome"));
        assertEquals("复盘：实际影响低于预期", row.get("outcomeComment"));
        assertEquals(new BigDecimal("120000.50"), row.get("expectedImpact"));
        assertEquals(new BigDecimal("98000.00"), row.get("actualImpact"));
        assertEquals("CNY", row.get("currency"));            // 缺省口径
        assertEquals("b-1", row.get("batchId"));
        assertEquals("1", row.get("versionNo"));             // DR07
        assertEquals(0, row.get("isDeleted"));               // DR06
        assertEquals("default", row.get("domain"));          // DR08
        assertEquals("system", row.get("createBy"));
    }

    @Test
    @DisplayName("写入后可读：forecastRunId / actionId 双定位键 snake→camel 无损回显")
    void outcomeReadableByBothKeys() {
        Map<String, Object> dbRow = new LinkedHashMap<>();
        dbRow.put("id", "oc-1");
        dbRow.put("forecast_run_id", "run-77");
        dbRow.put("action_id", "act-9");
        dbRow.put("fact_type", "STAGE");
        dbRow.put("fact_id", "f-1");
        dbRow.put("period", "2025-03");
        dbRow.put("outcome", "ACCEPTED");
        dbRow.put("outcome_comment", "备注");
        dbRow.put("expected_impact", new BigDecimal("120000.50"));
        dbRow.put("actual_impact", new BigDecimal("98000.00"));
        dbRow.put("currency", "CNY");
        dbRow.put("batch_id", "b-1");
        dbRow.put("idempotency_key", "idem-1");
        dbRow.put("create_by", "tester");
        dbRow.put("create_time", Timestamp.valueOf(LocalDateTime.of(2025, 4, 1, 8, 30)));
        when(outcomeMapper.selectByForecastRunId("run-77")).thenReturn(List.of(dbRow));
        when(outcomeMapper.selectByActionId("act-9")).thenReturn(List.of(dbRow));

        List<ActionOutcomeVO> byRun = service.listActionOutcomes("run-77", null);
        List<ActionOutcomeVO> byAct = service.listActionOutcomes(null, "act-9");
        assertEquals(1, byRun.size());
        assertEquals(1, byAct.size());
        ActionOutcomeVO vo = byRun.get(0);
        assertEquals("oc-1", vo.getOutcomeId());
        assertEquals("run-77", vo.getForecastRunId());
        assertEquals("act-9", vo.getActionId());
        assertEquals("STAGE", vo.getFactType());
        assertEquals(new BigDecimal("120000.50"), vo.getExpectedImpact());
        assertEquals(new BigDecimal("98000.00"), vo.getActualImpact());
        assertEquals("idem-1", vo.getIdempotencyKey());
        assertEquals("2025-04-01T08:30:00", vo.getCreateTime());
    }

    @Test
    @DisplayName("双键皆空 → 400 ECOS-DATA-023（禁全库扫描）")
    void bothKeysEmptyRejected() {
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service.listActionOutcomes(null, "  "));
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("ECOS-DATA-023"), ex.getMessage());
    }

    @Test
    @DisplayName("幂等键重放 → 返回首写行 alreadyRecorded=true，零触达零二次 INSERT")
    void idempotentReplayReturnsFirstRow() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("id", "oc-first");
        first.put("outcome", "ACCEPTED");
        when(outcomeMapper.selectByIdempotencyKey("idem-1")).thenReturn(first);

        ActionOutcomeRequest req = carrierReq();
        req.setIdempotencyKey("idem-1");
        Map<String, Object> out = service.writeActionOutcome(req);

        assertEquals("oc-first", out.get("outcomeId"));
        assertEquals(Boolean.TRUE, out.get("alreadyRecorded"));
        verify(stageMapper, never()).touchUpdate(anyString(), anyString());
        verify(outcomeMapper, never()).insertRow(any());
    }

    @Test
    @DisplayName("金额超 NUMERIC(18,2) 精度域（3 位小数 / 17 位整数）→ 400 显式拒绝")
    void amountPrecisionGuard() {
        // 精度校验在 DQ/触达之前短路（FactImportService.writeActionOutcome 序②），
        // 故本用例不需任何 mapper stub——400 即返，天然验证"拒绝不触库"。
        ActionOutcomeRequest r1 = carrierReq();
        r1.setActualImpact(new BigDecimal("100.123"));
        DataBridgeException ex1 = assertThrows(DataBridgeException.class, () -> service.writeActionOutcome(r1));
        assertEquals(400, ex1.getHttpStatus());
        assertTrue(ex1.getMessage().contains("actualImpact"), ex1.getMessage());

        ActionOutcomeRequest r2 = carrierReq();
        r2.setExpectedImpact(new BigDecimal("12345678901234567.00"));   // 整数位 17 > 16
        assertThrows(DataBridgeException.class, () -> service.writeActionOutcome(r2));
        verify(outcomeMapper, never()).insertRow(any());
    }

    @Test
    @DisplayName("mapper 未装配（V279 未应用环境）→ 旧形态保持：触达+审计，无 outcomeId，不报错")
    void legacyBehaviorWithoutCarrier() {
        FactImportService legacy = new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, new OpenPeriodsPeriodLock(""),
                Optional.empty(), 0.9);   // 不设 setter = 未装配形态
        when(stageMapper.getDqStatusById("f-1")).thenReturn("PASSED");
        when(stageMapper.touchUpdate(eq("f-1"), anyString())).thenReturn(1);

        Map<String, Object> out = legacy.writeActionOutcome(carrierReq());

        assertFalse(out.containsKey("outcomeId"));
        assertEquals("ACCEPTED", out.get("outcome"));
    }

    @Test
    @DisplayName("PUBLISHED 事实行触达 → 409 ECOS-DATA-021 先行，物理载体行不写（不可变守卫不被绕过）")
    void publishedRowStillBlockedBeforeCarrierWrite() {
        when(stageMapper.getDqStatusById("f-1")).thenReturn("PUBLISHED");

        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> service.writeActionOutcome(carrierReq()));
        assertEquals(409, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("ECOS-DATA-021"), ex.getMessage());
        verify(outcomeMapper, never()).insertRow(any());
    }

    @Test
    @DisplayName("载体未就绪时查询 → 503 ECOS-DATA-041（fail-loud，禁空结果伪装）")
    void queryFailsLoudWithoutCarrier() {
        FactImportService legacy = new FactImportService(attributionMapper, stageMapper, resourceMapper,
                costMapper, forecastMapper, okGate, new OpenPeriodsPeriodLock(""),
                Optional.empty(), 0.9);
        DataBridgeException ex = assertThrows(DataBridgeException.class,
                () -> legacy.listActionOutcomes("run-77", null));
        assertEquals(503, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("ECOS-DATA-041"), ex.getMessage());
    }

    @Test
    @DisplayName("双键同给 → 按 forecastRunId 索引查询后内存收敛 actionId（不二次查库）")
    void bothKeysNarrowInMemory() {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", "oc-1");
        hit.put("forecast_run_id", "run-77");
        hit.put("action_id", "act-9");
        Map<String, Object> miss = new LinkedHashMap<>();
        miss.put("id", "oc-2");
        miss.put("forecast_run_id", "run-77");
        miss.put("action_id", "act-other");
        when(outcomeMapper.selectByForecastRunId("run-77")).thenReturn(List.of(hit, miss));

        List<ActionOutcomeVO> out = service.listActionOutcomes("run-77", "act-9");
        assertEquals(1, out.size());
        assertEquals("oc-1", out.get(0).getOutcomeId());
        verify(outcomeMapper, never()).selectByActionId(anyString());
    }

    @Test
    @DisplayName("factType 非法仍走既有校验口径（只增不改：IllegalArgumentException 语义不变，载体不写）")
    void badFactTypeKeepsLegacyError() {
        ActionOutcomeRequest req = carrierReq();
        req.setFactType("NOPE");
        assertThrows(IllegalArgumentException.class, () -> service.writeActionOutcome(req));
        verify(outcomeMapper, never()).insertRow(any());
    }
}
