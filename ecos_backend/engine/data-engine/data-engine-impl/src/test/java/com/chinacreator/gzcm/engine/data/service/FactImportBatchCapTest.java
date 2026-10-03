package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.common.exception.BusinessException;

/**
 * C.9 性能与容量基线「导入批量 ≤100 行/批（EN03）」护栏（F02-06 / importBusinessFacts）。
 *
 * <p>{@code BusinessFactService.importBusinessFacts} 在 persist 前置校验
 * {@code rows.size() > 100 → 400 单批 ≤100 行（EN03）}（BusinessFactService:157）。
 * 本护栏锁该 <b>容量上限</b>：越过 100 行须在入库前整体拒绝（不部分入库、不静默截断），
 * 且边界 {@code ==100} 恰好放行（判据是 {@code >100} 而非 {@code >=100}，防止上限误缩）。
 * 判定性单测（Mockito 打桩 {@code JdbcTemplate}，不触库）。
 */
class FactImportBatchCapTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessFactService svc = new BusinessFactService(jdbc);

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

    private static List<Map<String, Object>> rows(int n) {
        List<Map<String, Object>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(validStageRow());
        }
        return rows;
    }

    @Test
    @DisplayName("101 行 → 400 单批 ≤100 行（EN03），且在入库前整体拒绝（对 JdbcTemplate 零交互）")
    void over100_rows_rejectedBeforePersist() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.importBusinessFacts("stage", "batch-cap", rows(101)));

        assertTrue(ex.getMessage().contains("100"), "应提示 100 上限；实际 " + ex.getMessage());
        assertTrue(ex.getMessage().contains("EN03"), "应带 EN03 容量码；实际 " + ex.getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("恰 100 行 → 放行（判据是 >100 而非 >=100，上限不误缩）")
    void exactly100_rows_passCap() {
        BusinessFactService.ImportResult res = svc.importBusinessFacts("stage", "batch-b100", rows(100));

        assertNotNull(res.batchId(), "100 行恰好达标应受理");
        assertEquals(100, res.accepted(), "100 行全合规应全 accepted，上限不应误缩到 <100");
        assertTrue(res.rejected().isEmpty(), "100 行无 rejected");
    }
}
