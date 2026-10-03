package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-06-5 / D.1（详细设计-02，DATA-01 P0）—— 关账期间写入拒绝（ECOS-DATA-022）。
 *
 * <p>{@code importBusinessFacts} 逐行做 {@code periodPasses}：期间年份命中
 * {@link BusinessFactService#CLOSED_PERIOD_YEAR_PREFIXES}（现状 {@code "2099"}，对齐
 * 09 册 FC-05 period lock）→ 该行进 rejected 且 ruleId = {@code "ECOS-DATA-022"}，
 * <b>不入库</b>。反向：正常期间（如 {@code 2025-07}）不受影响。本测试用 Mockito 打桩
 * {@link JdbcTemplate} 做运行时判定。</p>
 */
@DisplayName("F02-06-5 关账期间写入拒绝（ECOS-DATA-022，P0）")
class ClosedPeriodWriteDeniedTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessFactService svc = new BusinessFactService(jdbc);

    private static Map<String, Object> stageRow(String period) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project_id", "p-1");
        r.put("department_id", "d-1");
        r.put("period", period);
        r.put("stage", "DELIVERY");
        r.put("fact_type", "REVENUE");
        r.put("amount", "1000.00");
        r.put("currency", "CNY");
        return r;
    }

    @Test
    @DisplayName("ClosedPeriodWriteDeniedTest — 期间命中 2099 前缀 → rejected 且 ruleId=ECOS-DATA-022")
    @SuppressWarnings("unchecked")
    void period2099_rejectedWithEc022() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(stageRow("2099-01")); // 关账期
        rows.add(stageRow("2025-07")); // 正常期（对照组）

        BusinessFactService.ImportResult res = svc.importBusinessFacts("stage", "batch-C1", rows);

        assertEquals(1, res.accepted(), "仅正常期间 1 行 accepted");
        assertEquals(1, res.rejected().size(), "2099-01 应被拒 1 行");

        BusinessFactService.RejectedRow r = res.rejected().get(0);
        assertEquals("ECOS-DATA-022", r.ruleId(), "ruleId 必须为 ECOS-DATA-022 关账期语义; 实际 " + r.ruleId());
        assertTrue(r.field().contains("period"), "定位字段应指 period; 实际 " + r.field());
        assertTrue(r.suggestion() != null && !r.suggestion().isBlank(), "关账期拒绝必须带修复建议");

        // 正常期 1 行落库；坏期 0 行
        ArgumentCaptor<List<Object[]>> argsCap = ArgumentCaptor.forClass((Class) List.class);
        org.mockito.Mockito.verify(jdbc).batchUpdate(anyString(), argsCap.capture());
        assertEquals(1, argsCap.getValue().size(), "关账期行不入库; 仅正常 1 行");
    }

    @Test
    @DisplayName("ClosedPeriodWriteDeniedTest（反向）— 非 2099 前缀期间不拒")
    void normalPeriod_passes() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(stageRow("2025-08"));
        BusinessFactService.ImportResult res = svc.importBusinessFacts("stage", "batch-C2", rows);
        assertEquals(1, res.accepted(), "正常期间应全 accepted");
        assertTrue(res.rejected().isEmpty(), "正常期间不应产生 rejected");
    }
}
