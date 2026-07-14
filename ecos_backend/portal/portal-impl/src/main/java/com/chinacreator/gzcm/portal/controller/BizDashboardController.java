package com.chinacreator.gzcm.portal.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 经营看板 Controller — 提供经营仪表盘数据端点。
 * <p>
 * 使用 JdbcTemplate 直接查询，无 Service/Repository 层。
 * 每个端点都有 try/catch 保护，数据缺失时优雅降级返回空数据。
 * </p>
 *
 * <h3>端点清单：</h3>
 * <ol>
 *   <li>GET /api/v1/ecos/biz/dashboard — 经营看板综合数据</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/ecos/biz")
public class BizDashboardController {

    private static final Logger log = LoggerFactory.getLogger(BizDashboardController.class);

    private final JdbcTemplate jdbc;

    public BizDashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ════════════════════════════════════════════════════════════════
    // GET /dashboard — 经营看板综合数据
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getBizDashboard() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();

            // --- 部门列表 ---
            data.put("departments", queryBizTable("ecos_biz_department",
                    "SELECT id, name, manager, parent_id FROM ecos_biz_department ORDER BY id"));

            // --- 项目统计 ---
            data.put("projectStats", buildProjectStats());

            // --- 合同统计 ---
            data.put("contractStats", buildContractStats());

            // --- 经营指标 (最新6个月) ---
            data.put("metrics", buildMetricsSummary());

            // --- 年度目标 ---
            data.put("targets", queryBizTable("ecos_biz_target",
                    "SELECT id, dept_id, target_type, target_value, target_year FROM ecos_biz_target ORDER BY id"));

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to build biz dashboard", e);
            return ApiResponse.success(Map.of("error", e.getMessage()));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 私有辅助方法
    // ════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> queryBizTable(String table, String sql) {
        try {
            return jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                int cols = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                return row;
            });
        } catch (Exception e) {
            log.warn("Failed to query {}: {}", table, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> buildProjectStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("total", jdbc.queryForObject("SELECT COUNT(*) FROM ecos_biz_project", Long.class));
            stats.put("inProgress", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE status='in_progress'", Long.class));
            stats.put("completed", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE status='completed'", Long.class));
            stats.put("planning", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE status='planning'", Long.class));

            // Production vs Research vs Management
            stats.put("production", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE project_type='production'", Long.class));
            stats.put("research", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE project_type='research'", Long.class));
            stats.put("management", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_project WHERE project_type='management'", Long.class));

            // Total contract amount
            stats.put("totalAmount", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(contract_amount), 0) FROM ecos_biz_project", Double.class));
        } catch (Exception e) {
            log.warn("Project stats error: {}", e.getMessage());
            stats.put("total", 0);
        }
        return stats;
    }

    private Map<String, Object> buildContractStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("totalContracts", jdbc.queryForObject("SELECT COUNT(*) FROM ecos_biz_contract", Long.class));
            stats.put("incomeCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_contract WHERE contract_type='income'", Long.class));
            stats.put("expenseCount", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_contract WHERE contract_type='expense'", Long.class));
            stats.put("incomeAmount", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM ecos_biz_contract WHERE contract_type='income'", Double.class));
            stats.put("expenseAmount", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM ecos_biz_contract WHERE contract_type='expense'", Double.class));
            stats.put("totalAmount", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM ecos_biz_contract", Double.class));
            stats.put("activeContracts", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ecos_biz_contract WHERE status='active'", Long.class));
        } catch (Exception e) {
            log.warn("Contract stats error: {}", e.getMessage());
        }
        return stats;
    }

    private List<Map<String, Object>> buildMetricsSummary() {
        List<Map<String, Object>> summary = new ArrayList<>();
        try {
            String sql = """
                SELECT metric_month, metric_type, SUM(metric_value) as total_value
                FROM ecos_biz_metric
                GROUP BY metric_month, metric_type
                ORDER BY metric_month, metric_type
                """;
            return jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("month", rs.getString("metric_month"));
                m.put("type", rs.getString("metric_type"));
                m.put("value", rs.getDouble("total_value"));
                return m;
            });
        } catch (Exception e) {
            log.warn("Metrics summary error: {}", e.getMessage());
        }
        return summary;
    }
}
