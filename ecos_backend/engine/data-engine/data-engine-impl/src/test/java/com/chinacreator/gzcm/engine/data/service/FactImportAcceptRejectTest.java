package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-06（详细设计-02，DATA-01 P0）业务事实导入验收：
 * <ul>
 *   <li>{@code FactImportAcceptRejectTest} —— 全合规行 → accepted 全入库、{@code dq_status=PASSED}、
 *       走 {@code INSERT INTO ecos_dw.ecos_biz_stage_fact}；</li>
 *   <li>{@code FactBadDataTripleRejectTest} —— DQ-F01(必填空) / DQ-F02(期间格式) 各 1 行 →
 *       rejected（含 rowNo/field/ruleId/suggestion），且不入库。</li>
 * </ul>
 * 判定性单测（Mockito 打桩 {@code JdbcTemplate}，不触库）。
 */
class FactImportAcceptRejectTest {

    private JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private BusinessFactService svc = new BusinessFactService(jdbc);

    private static Map<String, Object> validStageRow() {
        return Map.of(
                "project_id", "p-1",
                "department_id", "d-1",
                "period", "2025-07",
                "stage", "DELIVERY",
                "fact_type", "REVENUE",
                "amount", "1000.00",
                "currency", "CNY");
    }

    @Test
    @DisplayName("FactImportAcceptRejectTest — 合规行全 accepted，INSERT 落 ecos_dw 事实表")
    @SuppressWarnings("unchecked")
    void allValid_allAccepted() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(validStageRow());
        rows.add(validStageRow());

        BusinessFactService.ImportResult res = svc.importBusinessFacts("stage", "batch-A1", rows);

        assertNotNull(res.batchId(), "批次 ID 非空");
        assertEquals(2, res.accepted(), "两行全合规应全 accepted");
        assertTrue(res.rejected().isEmpty(), "无 rejected");

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Object[]>> argsCap = ArgumentCaptor.forClass((Class) List.class);
        verify(jdbc).batchUpdate(sqlCap.capture(), argsCap.capture());
        assertTrue(sqlCap.getValue().startsWith("INSERT INTO ecos_dw.ecos_biz_stage_fact"),
                "应写入业务域事实表 ecos_dw 限号名; 实际 " + sqlCap.getValue());
        assertEquals(2, argsCap.getValue().size(), "2 行合规应批量插 2 行");
    }

    @Test
    @DisplayName("FactBadDataTripleRejectTest — DQ-F01/F02 坏行被拒且不入库，含 rowNo/field/ruleId/suggestion")
    @SuppressWarnings("unchecked")
    void badRows_rejected_notPersisted() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(validStageRow()); // row1 合规
        // row2：department_id 缺失 → DQ-F01（必填空）
        Map<String, Object> missing = new java.util.LinkedHashMap<>(validStageRow());
        missing.remove("department_id");
        rows.add(missing);
        // row3：period 格式非法 → DQ-F02
        Map<String, Object> badPeriod = new java.util.LinkedHashMap<>(validStageRow());
        badPeriod.put("period", "25/07");
        rows.add(badPeriod);

        BusinessFactService.ImportResult res = svc.importBusinessFacts("stage", "batch-A2", rows);

        assertEquals(1, res.accepted(), "仅 1 行合规");
        assertEquals(2, res.rejected().size(), "两坏行各占一条 rejected");

        List<String> ruleIds = res.rejected().stream().map(BusinessFactService.RejectedRow::ruleId).toList();
        assertTrue(ruleIds.contains("DQ-F01"), "应命中 DQ-F01 必填空; 实际 " + ruleIds);
        assertTrue(ruleIds.contains("DQ-F02"), "应命中 DQ-F02 期间格式; 实际 " + ruleIds);

        // rejected 明细字段齐全
        for (BusinessFactService.RejectedRow r : res.rejected()) {
            assertTrue(r.rowNo() >= 1, "rowNo 应 ≥1");
            assertNotNull(r.field());
            assertNotNull(r.suggestion(), "rejected 必须带修复建议");
        }
        // 坏行不入库：仅 1 行合规落库
        verify(jdbc).batchUpdate(anyString(), (List<Object[]>) any());
        ArgumentCaptor<List<Object[]>> argsCap = ArgumentCaptor.forClass((Class) List.class);
        verify(jdbc).batchUpdate(anyString(), argsCap.capture());
        assertEquals(1, argsCap.getValue().size(), "坏行不得入库，仅合规 1 行落库");
    }
}
