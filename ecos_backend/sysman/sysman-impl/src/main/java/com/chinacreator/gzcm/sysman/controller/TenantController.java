package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P0-1 统一租户控制器。
 * <p>
 * 操作统一后的 ecos_tenant 表（小写列名），
 * 并合并了原 TenantBillingController 的配额/用量/账单端点。
 */
@RestController
@RequestMapping({"/api/v1/system/tenants", "/api/system/tenants"})
public class TenantController {

    private static final Logger log = LoggerFactory.getLogger(TenantController.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbc;

    public TenantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════════════════════════════════════════════════
    //  基础 CRUD
    // ═══════════════════════════════════════════════════════════

    // ── 1. 租户列表 ──────────────────────────────────────

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            StringBuilder where = new StringBuilder(" WHERE 1=1 ");
            List<Object> params = new ArrayList<>();

            if (status != null && !status.isEmpty()) {
                where.append("AND status = ? ");
                params.add(status);
            }

            // Count
            String countSql = "SELECT COUNT(*) FROM ecos_tenant" + where;
            int total = jdbc.queryForObject(countSql, Integer.class, params.toArray());

            // Paginate
            String sql = "SELECT id, tenant_name, tenant_code, status, max_users, " +
                    "max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                    "created_at, updated_at " +
                    "FROM ecos_tenant" + where + " ORDER BY created_at DESC NULLS LAST " +
                    "LIMIT ? OFFSET ?";
            List<Object> pagedParams = new ArrayList<>(params);
            pagedParams.add(size);
            pagedParams.add((page - 1) * size);

            List<Map<String, Object>> rows = jdbc.queryForList(sql, pagedParams.toArray());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("page", page);
            result.put("size", size);
            result.put("total", total);
            result.put("data", rows);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询租户列表失败", e);
            return ApiResponse.internalError("查询租户列表失败: " + e.getMessage());
        }
    }

    // ── 2. 租户详情 ──────────────────────────────────────

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            String sql = "SELECT id, tenant_name, tenant_code, status, max_users, " +
                    "max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                    "created_at, updated_at " +
                    "FROM ecos_tenant WHERE id = ?";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
            if (rows.isEmpty()) {
                return ApiResponse.notFound("租户不存在: " + id);
            }
            return ApiResponse.success(rows.get(0));
        } catch (Exception e) {
            log.error("查询租户详情失败: id={}", id, e);
            return ApiResponse.internalError("查询租户详情失败: " + e.getMessage());
        }
    }

    // ── 3. 创建租户 ──────────────────────────────────────

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String id = (String) body.getOrDefault("id", "T-" + UUID.randomUUID().toString().substring(0, 8));
            String tenantName = (String) body.get("tenantName");
            String tenantCode = (String) body.getOrDefault("tenantCode", id.toLowerCase());
            String status = (String) body.getOrDefault("status", "ACTIVE");
            int maxUsers = body.containsKey("maxUsers") ? ((Number) body.get("maxUsers")).intValue() : 0;
            long maxStorageMb = body.containsKey("maxStorageMb") ? ((Number) body.get("maxStorageMb")).longValue() : 0;
            long maxApiPerDay = body.containsKey("maxApiPerDay") ? ((Number) body.get("maxApiPerDay")).longValue() : 0;
            String isolationMode = (String) body.getOrDefault("isolationMode", "ROW_FILTER");
            String schemaName = (String) body.getOrDefault("schemaName", null);
            String databaseUrl = (String) body.getOrDefault("databaseUrl", null);

            if (tenantName == null || tenantName.isEmpty()) {
                return ApiResponse.badRequest("tenantName 不能为空");
            }

            String sql = "INSERT INTO ecos_tenant (id, tenant_name, tenant_code, status, " +
                    "max_users, max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                    "created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
            jdbc.update(sql, id, tenantName, tenantCode, status,
                    maxUsers, maxStorageMb, maxApiPerDay, isolationMode, schemaName, databaseUrl);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("tenantName", tenantName);
            result.put("tenantCode", tenantCode);
            result.put("status", status);
            return ApiResponse.success("租户创建成功", result);
        } catch (Exception e) {
            log.error("创建租户失败", e);
            return ApiResponse.internalError("创建租户失败: " + e.getMessage());
        }
    }

    // ── 4. 更新租户 ──────────────────────────────────────

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
            int count = jdbc.queryForObject(checkSql, Integer.class, id);
            if (count == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            StringBuilder set = new StringBuilder();
            List<Object> params = new ArrayList<>();

            if (body.containsKey("tenantName")) {
                set.append("tenant_name = ?, ");
                params.add(body.get("tenantName"));
            }
            if (body.containsKey("tenantCode")) {
                set.append("tenant_code = ?, ");
                params.add(body.get("tenantCode"));
            }
            if (body.containsKey("status")) {
                set.append("status = ?, ");
                params.add(body.get("status"));
            }
            if (body.containsKey("maxUsers")) {
                set.append("max_users = ?, ");
                params.add(((Number) body.get("maxUsers")).intValue());
            }
            if (body.containsKey("maxStorageMb")) {
                set.append("max_storage_mb = ?, ");
                params.add(((Number) body.get("maxStorageMb")).longValue());
            }
            if (body.containsKey("maxApiPerDay")) {
                set.append("max_api_per_day = ?, ");
                params.add(((Number) body.get("maxApiPerDay")).longValue());
            }
            if (body.containsKey("isolationMode")) {
                set.append("isolation_mode = ?, ");
                params.add(body.get("isolationMode"));
            }
            if (body.containsKey("schemaName")) {
                set.append("schema_name = ?, ");
                params.add(body.get("schemaName"));
            }
            if (body.containsKey("databaseUrl")) {
                set.append("database_url = ?, ");
                params.add(body.get("databaseUrl"));
            }

            if (set.isEmpty()) {
                return ApiResponse.badRequest("没有要更新的字段");
            }

            set.append("updated_at = NOW()");
            params.add(id);

            String sql = "UPDATE ecos_tenant SET " + set + " WHERE id = ?";
            jdbc.update(sql, params.toArray());

            return get(id);
        } catch (Exception e) {
            log.error("更新租户失败: id={}", id, e);
            return ApiResponse.internalError("更新租户失败: " + e.getMessage());
        }
    }

    // ── 5. 软删除 ──────────────────────────────────────

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String id) {
        try {
            String sql = "UPDATE ecos_tenant SET status = 'DELETED', updated_at = NOW() WHERE id = ?";
            int rows = jdbc.update(sql, id);
            if (rows == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }
            return ApiResponse.success("租户已删除", null);
        } catch (Exception e) {
            log.error("删除租户失败: id={}", id, e);
            return ApiResponse.internalError("删除租户失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  配额 / 用量 / 账单（从 TenantBillingController 迁入）
    // ═══════════════════════════════════════════════════════════

    // ── 6. 租户配额信息 ──────────────────────────────

    @GetMapping("/{id}/quota")
    public ApiResponse<Map<String, Object>> getQuota(@PathVariable String id) {
        try {
            // Verify tenant exists
            String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
            int count = jdbc.queryForObject(checkSql, Integer.class, id);
            if (count == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            List<Map<String, Object>> quotas = jdbc.queryForList(
                    "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                    "FROM ecos_tenant_quota WHERE tenant_id = ?", id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenantId", id);
            result.put("quotas", quotas);

            // Fetch usage from ecos_tenant_usage
            try {
                String usageSql = "SELECT quota_type, MAX(used_count) AS used_count " +
                        "FROM ecos_tenant_usage WHERE tenant_id = ? GROUP BY quota_type";
                List<Map<String, Object>> usageRows = jdbc.queryForList(usageSql, id);
                result.put("usage", usageRows);
            } catch (Exception ex) {
                result.put("usage", Collections.emptyList());
            }

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询租户配额失败: id={}", id, e);
            return ApiResponse.internalError("查询租户配额失败: " + e.getMessage());
        }
    }

    // ── 7. 更新租户配额 ──────────────────────────────

    @PutMapping("/{id}/quota")
    public ApiResponse<Map<String, Object>> updateQuota(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            // Verify tenant exists
            String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
            int count = jdbc.queryForObject(checkSql, Integer.class, id);
            if (count == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            String quotaType = (String) body.get("quota_type");
            Object dailyLimitObj = body.get("daily_limit");
            Object monthlyLimitObj = body.get("monthly_limit");

            if (quotaType == null || quotaType.isBlank()) {
                return ApiResponse.badRequest("quota_type 为必填项");
            }

            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?",
                    id, quotaType);

            if (existing.isEmpty()) {
                long dailyLimit = dailyLimitObj != null ? ((Number) dailyLimitObj).longValue() : 0L;
                long monthlyLimit = monthlyLimitObj != null ? ((Number) monthlyLimitObj).longValue() : 0L;
                jdbc.update(
                        "INSERT INTO ecos_tenant_quota (tenant_id, quota_type, daily_limit, monthly_limit) " +
                        "VALUES (?, ?, ?, ?)", id, quotaType, dailyLimit, monthlyLimit);
            } else {
                StringBuilder sql = new StringBuilder("UPDATE ecos_tenant_quota SET updated_at = NOW()");
                List<Object> params = new ArrayList<>();

                if (dailyLimitObj != null) {
                    sql.append(", daily_limit = ?");
                    params.add(((Number) dailyLimitObj).longValue());
                }
                if (monthlyLimitObj != null) {
                    sql.append(", monthly_limit = ?");
                    params.add(((Number) monthlyLimitObj).longValue());
                }

                sql.append(" WHERE tenant_id = ? AND quota_type = ?");
                params.add(id);
                params.add(quotaType);

                jdbc.update(sql.toString(), params.toArray());
            }

            Map<String, Object> updated = jdbc.queryForMap(
                    "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                    "FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?", id, quotaType);

            log.info("Quota updated: tenantId={}, quotaType={}", id, quotaType);
            return ApiResponse.success("配额已更新", updated);
        } catch (Exception e) {
            log.error("更新租户配额失败: id={}", id, e);
            return ApiResponse.internalError("更新租户配额失败: " + e.getMessage());
        }
    }

    // ── 8. 用量统计 ──────────────────────────────────

    @GetMapping("/{id}/usage")
    public ApiResponse<Map<String, Object>> getUsage(
            @PathVariable String id,
            @RequestParam(defaultValue = "30d") String range) {
        try {
            // Verify tenant exists
            String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
            int count = jdbc.queryForObject(checkSql, Integer.class, id);
            if (count == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            int days = 30;
            if (range.endsWith("d")) {
                try { days = Integer.parseInt(range.replace("d", "")); } catch (NumberFormatException ignored) {}
            }

            LocalDate startDate = LocalDate.now().minusDays(days - 1);

            List<Map<String, Object>> dailyUsage = jdbc.queryForList(
                    "SELECT usage_date, quota_type, used_count " +
                    "FROM ecos_tenant_usage " +
                    "WHERE tenant_id = ? AND usage_date >= ? " +
                    "ORDER BY usage_date DESC, quota_type",
                    id, startDate);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("tenant_id", id);
            summary.put("range_days", days);
            summary.put("daily_usage", dailyUsage);

            Map<String, Long> totals = dailyUsage.stream()
                    .collect(Collectors.groupingBy(
                            row -> (String) row.get("quota_type"),
                            LinkedHashMap::new,
                            Collectors.summingLong(row -> ((Number) row.get("used_count")).longValue())
                    ));
            summary.put("totals_by_type", totals);

            return ApiResponse.success(summary);
        } catch (Exception e) {
            log.error("查询用量失败: id={}", id, e);
            return ApiResponse.internalError("查询用量失败: " + e.getMessage());
        }
    }

    // ── 9. 模拟账单 ──────────────────────────────────

    @GetMapping("/{id}/invoice")
    public ApiResponse<Map<String, Object>> getInvoice(
            @PathVariable String id,
            @RequestParam(defaultValue = "") String month) {
        try {
            // Verify tenant exists
            String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
            int count = jdbc.queryForObject(checkSql, Integer.class, id);
            if (count == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            YearMonth ym;
            if (month.isBlank()) {
                ym = YearMonth.now();
            } else {
                ym = YearMonth.parse(month, MONTH_FMT);
            }

            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();

            List<Map<String, Object>> monthlyUsage = jdbc.queryForList(
                    "SELECT quota_type, SUM(used_count) AS total_used " +
                    "FROM ecos_tenant_usage " +
                    "WHERE tenant_id = ? AND usage_date BETWEEN ? AND ? " +
                    "GROUP BY quota_type",
                    id, start, end);

            List<Map<String, Object>> quotas = jdbc.queryForList(
                    "SELECT quota_type, daily_limit, monthly_limit " +
                    "FROM ecos_tenant_quota WHERE tenant_id = ?", id);

            Map<String, Long> usageMap = new LinkedHashMap<>();
            long totalCostCents = 0;
            List<Map<String, Object>> lineItems = new ArrayList<>();

            for (Map<String, Object> row : monthlyUsage) {
                String type = (String) row.get("quota_type");
                long totalUsed = ((Number) row.get("total_used")).longValue();
                usageMap.put(type, totalUsed);

                long costCents = 0;
                if ("API_CALLS".equals(type)) {
                    costCents = Math.round(totalUsed * 0.10 / 1000.0 * 100);
                } else if ("STORAGE_MB".equals(type)) {
                    costCents = Math.round(totalUsed * 0.05 / 1024.0 * 100);
                }

                totalCostCents += costCents;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("quota_type", type);
                item.put("usage", totalUsed);
                item.put("unit_price", "API_CALLS".equals(type) ? "¥0.10/千次" : "¥0.05/GB");
                item.put("cost_cents", costCents);
                item.put("cost_display", String.format("¥%.2f", costCents / 100.0));
                lineItems.add(item);
            }

            Map<String, Object> invoice = new LinkedHashMap<>();
            invoice.put("tenant_id", id);
            invoice.put("month", ym.toString());
            invoice.put("quota_definitions", quotas);
            invoice.put("line_items", lineItems);
            invoice.put("total_cost_cents", totalCostCents);
            invoice.put("total_cost_display", String.format("¥%.2f", totalCostCents / 100.0));
            invoice.put("generated_at", java.time.Instant.now().toString());

            return ApiResponse.success(invoice);
        } catch (Exception e) {
            log.error("生成账单失败: id={}", id, e);
            return ApiResponse.internalError("生成账单失败: " + e.getMessage());
        }
    }
}
