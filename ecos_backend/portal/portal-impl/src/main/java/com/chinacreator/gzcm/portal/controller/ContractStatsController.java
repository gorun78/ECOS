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
 * 合同统计 Controller — 提供合同维度统计数据端点。
 * <p>
 * 使用 JdbcTemplate 直接查询 ecos_biz_contract 表，无 Service/Repository 层。
 * 数据缺失时优雅降级返回空 Map。
 * </p>
 *
 * <h3>端点清单：</h3>
 * <ol>
 *   <li>GET /api/v1/ecos/contracts/stats — 合同统计数据</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/ecos/contracts")
public class ContractStatsController {

    private static final Logger log = LoggerFactory.getLogger(ContractStatsController.class);

    private final JdbcTemplate jdbc;

    public ContractStatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ════════════════════════════════════════════════════════════════
    // GET /stats — 合同统计数据
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> buildContractStats() {
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

            return ApiResponse.success(stats);
        } catch (Exception e) {
            log.warn("Contract stats error: {}", e.getMessage());
            return ApiResponse.success(stats);
        }
    }
}
