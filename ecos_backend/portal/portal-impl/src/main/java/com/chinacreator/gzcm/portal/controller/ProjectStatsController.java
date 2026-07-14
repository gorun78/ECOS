package com.chinacreator.gzcm.portal.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目统计 Controller — 提供项目维度统计数据端点。
 * <p>
 * 使用 JdbcTemplate 直接查询 ecos_biz_project 表，无 Service/Repository 层。
 * 数据缺失时优雅降级返回零值。
 * </p>
 *
 * <h3>端点清单：</h3>
 * <ol>
 *   <li>GET /api/v1/ecos/projects/stats — 项目统计数据</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/ecos/projects")
public class ProjectStatsController {

    private static final Logger log = LoggerFactory.getLogger(ProjectStatsController.class);

    private final JdbcTemplate jdbc;

    public ProjectStatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ════════════════════════════════════════════════════════════════
    // GET /stats — 项目统计数据
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> buildProjectStats() {
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

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.warn("Project stats error: {}", e.getMessage());
            stats.put("total", 0);
            return ApiResponse.success(stats);
        }
    }
}
