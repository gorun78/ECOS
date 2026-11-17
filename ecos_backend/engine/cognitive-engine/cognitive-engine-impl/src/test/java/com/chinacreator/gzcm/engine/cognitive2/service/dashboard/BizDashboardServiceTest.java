package com.chinacreator.gzcm.engine.cognitive2.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.engine.cognitive2.dao.BizDashboardDao;

/**
 * D33 契约门：经营看板聚合服务的字段映射与诚实口径。
 *
 * <p>钉住四条不变量：</p>
 * <ol>
 *   <li>字段名对齐前端 BizData（camelCase，如 projectStats.inProgress / contractStats.totalValue 为串）；</li>
 *   <li>空数据返回 0 / 空数组，不编造业务数值；</li>
 *   <li>departments.manager 在组织表无对应列 ⇒ 空串（不拿占位姓名造假）；</li>
 *   <li>ops 复用腿 monthlyTrend / contractTrend 真实派生（rate 为占全年笔数比例，非编造）。</li>
 * </ol>
 */
class BizDashboardServiceTest {

    private BizDashboardDao dao() {
        return mock(BizDashboardDao.class);
    }

    @Test
    @DisplayName("字段名对齐前端契约 + 数值类型正确（totalValue 为串、金额 BigDecimal）")
    void mapsFrontendContractShape() {
        BizDashboardDao dao = dao();
        when(dao.listDepartments()).thenReturn(List.of());
        when(dao.projectStats()).thenReturn(Map.of(
                "total", 3L, "in_progress", 3L, "completed", 0L, "planning", 0L,
                "total_amount", new BigDecimal("1200.50")));
        when(dao.contractStats()).thenReturn(Map.of(
                "total", 5L, "total_value", new BigDecimal("999.99"), "income_count", 3L, "expense_count", 2L));
        when(dao.listMetrics()).thenReturn(List.of());
        when(dao.listTargets()).thenReturn(List.of());
        when(dao.projectMonthlyTrend()).thenReturn(List.of());
        when(dao.contractMonthlyTrend()).thenReturn(List.of());

        Map<String, Object> out = new BizDashboardService(dao).dashboard();

        assertTrue(out.containsKey("departments"));
        assertTrue(out.containsKey("projectStats"));
        assertTrue(out.containsKey("contractStats"));
        assertTrue(out.containsKey("metrics"));
        assertTrue(out.containsKey("targets"));
        assertTrue(out.containsKey("monthlyTrend"), "ops 复用腿 monthlyTrend 必须在");
        assertTrue(out.containsKey("contractTrend"), "ops 复用腿 contractTrend 必须在");

        @SuppressWarnings("unchecked")
        Map<String, Object> ps = (Map<String, Object>) out.get("projectStats");
        assertEquals(3L, ps.get("total"));
        assertEquals(3L, ps.get("inProgress"), "camelCase inProgress（前端契约）");
        assertEquals(new BigDecimal("1200.50"), ps.get("totalAmount"));

        @SuppressWarnings("unchecked")
        Map<String, Object> cs = (Map<String, Object>) out.get("contractStats");
        assertEquals("999.99", cs.get("totalValue"), "totalValue 必须是字符串（前端 interface 声明 string）");
        assertEquals(3L, cs.get("incomeCount"));
        assertEquals(2L, cs.get("expenseCount"));
    }

    @Test
    @DisplayName("空库 ⇒ 0 / 空数组，不编造业务数值")
    void emptyDataYieldsZeroNotFabrication() {
        BizDashboardDao dao = dao();
        when(dao.listDepartments()).thenReturn(List.of());
        when(dao.projectStats()).thenReturn(Map.of(
                "total", 0L, "in_progress", 0L, "completed", 0L, "planning", 0L,
                "total_amount", BigDecimal.ZERO));
        when(dao.contractStats()).thenReturn(Map.of(
                "total", 0L, "total_value", BigDecimal.ZERO, "income_count", 0L, "expense_count", 0L));
        when(dao.listMetrics()).thenReturn(List.of());
        when(dao.listTargets()).thenReturn(List.of());
        when(dao.projectMonthlyTrend()).thenReturn(List.of());
        when(dao.contractMonthlyTrend()).thenReturn(List.of());

        Map<String, Object> out = new BizDashboardService(dao).dashboard();

        assertEquals(List.of(), out.get("departments"));
        assertEquals(List.of(), out.get("metrics"));
        assertEquals(List.of(), out.get("targets"));
        assertEquals(List.of(), out.get("monthlyTrend"));
        assertEquals(List.of(), out.get("contractTrend"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cs = (Map<String, Object>) out.get("contractStats");
        assertEquals("0", cs.get("totalValue"));
        assertEquals(0L, cs.get("incomeCount"));
    }

    @Test
    @DisplayName("departments.manager 空串（组织表无负责人列，不造假）")
    void departmentManagerIsEmptyNotFaked() {
        BizDashboardDao dao = dao();
        when(dao.listDepartments()).thenReturn(List.of(
                new java.util.LinkedHashMap<>(Map.of("org_id", "org1", "org_name", "研发中心"))));
        when(dao.projectStats()).thenReturn(Map.of("total", 0L, "in_progress", 0L, "completed", 0L, "planning", 0L, "total_amount", BigDecimal.ZERO));
        when(dao.contractStats()).thenReturn(Map.of("total", 0L, "total_value", BigDecimal.ZERO, "income_count", 0L, "expense_count", 0L));
        when(dao.listMetrics()).thenReturn(List.of());
        when(dao.listTargets()).thenReturn(List.of());
        when(dao.projectMonthlyTrend()).thenReturn(List.of());
        when(dao.contractMonthlyTrend()).thenReturn(List.of());

        Map<String, Object> out = new BizDashboardService(dao).dashboard();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> depts = (List<Map<String, Object>>) out.get("departments");
        assertEquals(1, depts.size());
        assertEquals("研发中心", depts.get(0).get("name"));
        assertEquals("", depts.get(0).get("manager"), "manager 必须空串，不得编造负责人");
        assertFalse(depts.get(0).containsKey("parent_id"), "parent 为空时不输出 parent_id");
    }

    @Test
    @DisplayName("contractTrend.rate 为占全年笔数比例（真实派生）")
    void contractTrendRateIsDerivedNotInvented() {
        BizDashboardDao dao = dao();
        when(dao.listDepartments()).thenReturn(List.of());
        when(dao.projectStats()).thenReturn(Map.of("total", 0L, "in_progress", 0L, "completed", 0L, "planning", 0L, "total_amount", BigDecimal.ZERO));
        when(dao.contractStats()).thenReturn(Map.of("total", 0L, "total_value", BigDecimal.ZERO, "income_count", 0L, "expense_count", 0L));
        when(dao.listMetrics()).thenReturn(List.of());
        when(dao.listTargets()).thenReturn(List.of());
        when(dao.projectMonthlyTrend()).thenReturn(List.of());
        when(dao.contractMonthlyTrend()).thenReturn(List.of(
                Map.of("month", "2026-10", "cnt", 3L),
                Map.of("month", "2026-11", "cnt", 1L)));

        Map<String, Object> out = new BizDashboardService(dao).dashboard();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) out.get("contractTrend");
        assertEquals(2, trend.size());
        assertEquals(0.75, (Double) trend.get(0).get("rate"), 1e-9, "3/4 = 0.75");
        assertEquals(0.25, (Double) trend.get(1).get("rate"), 1e-9, "1/4 = 0.25");
    }
}
