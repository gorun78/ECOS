package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.sysman.service.TenantService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p><b>#147CJ（DoD DI-2 第十四批）</b>：出入参强类型化——SQL 与行映射全部收回
 * {@link TenantService}（行面 record 用 {@code @JsonProperty} 钉住旧列名），请求改 DTO
 * （更新用 {@code xxxPresent} 保 {@code containsKey} 语义），响应改 VO（用量/账单的混合
 * snake_case 键由属性注解保持）。wire 键面与键序不变。</p>
 */
@RestController
@RequestMapping("/api/v1/system/tenants")
public class TenantController {

    private final TenantService tenantService;
    private static final Logger log = LoggerFactory.getLogger(TenantController.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    // ── 1. 租户列表 ──────────────────────────────────────

    @GetMapping
    public ApiResponse<TenantService.TenantPage> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            return ApiResponse.success(tenantService.listTenants(status, page, size));
        } catch (Exception e) {
            log.error("查询租户列表失败", e);
            return ApiResponse.internalError("查询租户列表失败");
        }
    }

    // ── 2. 租户详情 ──────────────────────────────────────

    @GetMapping("/{id}")
    public ApiResponse<TenantService.TenantRow> get(@PathVariable String id) {
        try {
            TenantService.TenantRow row = tenantService.getTenant(id);
            return row == null ? ApiResponse.notFound("租户不存在: " + id) : ApiResponse.success(row);
        } catch (Exception e) {
            log.error("查询租户详情失败: id={}", id, e);
            return ApiResponse.internalError("查询租户详情失败");
        }
    }

    // ── 3. 创建租户 ──────────────────────────────────────

    @PostMapping
    public ApiResponse<CreateResultVO> create(@RequestBody TenantCreateRequest body) {
        try {
            String id = body.getId() != null ? body.getId()
                    : "T-" + UUID.randomUUID().toString().substring(0, 8);
            String tenantName = body.getTenantName();
            String tenantCode = body.getTenantCode() != null ? body.getTenantCode() : id.toLowerCase();
            String status = body.getStatus() != null ? body.getStatus() : "ACTIVE";
            int maxUsers = body.getMaxUsers() != null ? body.getMaxUsers() : 0;
            long maxStorageMb = body.getMaxStorageMb() != null ? body.getMaxStorageMb() : 0L;
            long maxApiPerDay = body.getMaxApiPerDay() != null ? body.getMaxApiPerDay() : 0L;
            String isolationMode = body.getIsolationMode() != null ? body.getIsolationMode() : "ROW_FILTER";

            if (tenantName == null || tenantName.isEmpty()) {
                return ApiResponse.badRequest("tenantName 不能为空");
            }

            tenantService.createTenant(id, tenantName, tenantCode, status, maxUsers, maxStorageMb,
                    maxApiPerDay, isolationMode, body.getSchemaName(), body.getDatabaseUrl());

            return ApiResponse.success("租户创建成功",
                    new CreateResultVO(id, tenantName, tenantCode, status));
        } catch (Exception e) {
            log.error("创建租户失败", e);
            return ApiResponse.internalError("创建租户失败");
        }
    }

    // ── 4. 更新租户 ──────────────────────────────────────

    @PutMapping("/{id}")
    public ApiResponse<TenantService.TenantRow> update(@PathVariable String id,
                                                       @RequestBody TenantUpdateRequest body) {
        try {
            if (tenantService.countTenant(id) == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            StringBuilder set = new StringBuilder();
            List<Object> params = new ArrayList<>();

            if (body.isTenantNamePresent()) {
                set.append("tenant_name = ?, ");
                params.add(body.getTenantName());
            }
            if (body.isTenantCodePresent()) {
                set.append("tenant_code = ?, ");
                params.add(body.getTenantCode());
            }
            if (body.isStatusPresent()) {
                set.append("status = ?, ");
                params.add(body.getStatus());
            }
            if (body.isMaxUsersPresent()) {
                set.append("max_users = ?, ");
                params.add(body.getMaxUsers());
            }
            if (body.isMaxStorageMbPresent()) {
                set.append("max_storage_mb = ?, ");
                params.add(body.getMaxStorageMb());
            }
            if (body.isMaxApiPerDayPresent()) {
                set.append("max_api_per_day = ?, ");
                params.add(body.getMaxApiPerDay());
            }
            if (body.isIsolationModePresent()) {
                set.append("isolation_mode = ?, ");
                params.add(body.getIsolationMode());
            }
            if (body.isSchemaNamePresent()) {
                set.append("schema_name = ?, ");
                params.add(body.getSchemaName());
            }
            if (body.isDatabaseUrlPresent()) {
                set.append("database_url = ?, ");
                params.add(body.getDatabaseUrl());
            }

            if (set.isEmpty()) {
                return ApiResponse.badRequest("没有要更新的字段");
            }

            tenantService.updateTenant(id, set, params);
            return get(id);
        } catch (Exception e) {
            log.error("更新租户失败: id={}", id, e);
            return ApiResponse.internalError("更新租户失败");
        }
    }

    // ── 5. 软删除 ──────────────────────────────────────

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            int rows = tenantService.deleteTenant(id);
            if (rows == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }
            return ApiResponse.<Void>success("租户已删除", null);
        } catch (Exception e) {
            log.error("删除租户失败: id={}", id, e);
            return ApiResponse.internalError("删除租户失败");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  配额 / 用量 / 账单（从 TenantBillingController 迁入）
    // ═══════════════════════════════════════════════════════════

    // ── 6. 租户配额信息 ──────────────────────────────

    @GetMapping("/{id}/quota")
    public ApiResponse<QuotaResultVO> getQuota(@PathVariable String id) {
        try {
            if (tenantService.countTenant(id) == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            List<TenantService.QuotaRow> quotas = tenantService.queryTenantQuotas(id);

            List<TenantService.UsageMaxRow> usage;
            try {
                usage = tenantService.queryTenantUsageSummary(id);
            } catch (Exception ex) {
                usage = Collections.emptyList();
            }
            return ApiResponse.success(new QuotaResultVO(id, quotas, usage));
        } catch (Exception e) {
            log.error("查询租户配额失败: id={}", id, e);
            return ApiResponse.internalError("查询租户配额失败");
        }
    }

    // ── 7. 更新租户配额 ──────────────────────────────

    @PutMapping("/{id}/quota")
    public ApiResponse<TenantService.QuotaRow> updateQuota(
            @PathVariable String id,
            @RequestBody QuotaUpdateRequest body) {
        try {
            if (tenantService.countTenant(id) == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            String quotaType = body.getQuotaType();
            Long dailyLimitObj = body.getDailyLimit();
            Long monthlyLimitObj = body.getMonthlyLimit();

            if (quotaType == null || quotaType.isBlank()) {
                return ApiResponse.badRequest("quota_type 为必填项");
            }

            if (!tenantService.quotaExists(id, quotaType)) {
                long dailyLimit = dailyLimitObj != null ? dailyLimitObj : 0L;
                long monthlyLimit = monthlyLimitObj != null ? monthlyLimitObj : 0L;
                tenantService.insertQuota(id, quotaType, dailyLimit, monthlyLimit);
            } else {
                StringBuilder sql = new StringBuilder("UPDATE ecos_tenant_quota SET updated_at = NOW()");
                List<Object> params = new ArrayList<>();

                if (dailyLimitObj != null) {
                    sql.append(", daily_limit = ?");
                    params.add(dailyLimitObj);
                }
                if (monthlyLimitObj != null) {
                    sql.append(", monthly_limit = ?");
                    params.add(monthlyLimitObj);
                }

                tenantService.updateQuota(id, quotaType, sql, params);
            }

            TenantService.QuotaRow updated = tenantService.queryQuotaRow(id, quotaType);

            log.info("Quota updated: tenantId={}, quotaType={}", id, quotaType);
            return ApiResponse.success("配额已更新", updated);
        } catch (Exception e) {
            log.error("更新租户配额失败: id={}", id, e);
            return ApiResponse.internalError("更新租户配额失败");
        }
    }

    // ── 8. 用量统计 ──────────────────────────────────

    @GetMapping("/{id}/usage")
    public ApiResponse<UsageResultVO> getUsage(
            @PathVariable String id,
            @RequestParam(defaultValue = "30d") String range) {
        try {
            if (tenantService.countTenant(id) == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            int days = 30;
            if (range.endsWith("d")) {
                try { days = Integer.parseInt(range.replace("d", "")); } catch (NumberFormatException ignored) {
                    log.debug("用量区间 range 解析失败，回退默认 30 天: range={}", range);
                }
            }

            LocalDate startDate = LocalDate.now().minusDays(days - 1);
            List<TenantService.DailyUsageRow> dailyUsage = tenantService.queryDailyUsage(id, startDate);

            Map<String, Long> totals = dailyUsage.stream()
                    .collect(Collectors.groupingBy(
                            TenantService.DailyUsageRow::quotaType,
                            LinkedHashMap::new,
                            Collectors.summingLong(row -> row.usedCount() == null ? 0L : row.usedCount())
                    ));

            return ApiResponse.success(new UsageResultVO(id, days, dailyUsage, totals));
        } catch (Exception e) {
            log.error("查询用量失败: id={}", id, e);
            return ApiResponse.internalError("查询用量失败");
        }
    }

    // ── 9. 模拟账单 ──────────────────────────────────

    @GetMapping("/{id}/invoice")
    public ApiResponse<InvoiceResultVO> getInvoice(
            @PathVariable String id,
            @RequestParam(defaultValue = "") String month) {
        try {
            if (tenantService.countTenant(id) == 0) {
                return ApiResponse.notFound("租户不存在: " + id);
            }

            YearMonth ym = month.isBlank() ? YearMonth.now() : YearMonth.parse(month, MONTH_FMT);

            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();

            List<TenantService.MonthlyUsageRow> monthlyUsage = tenantService.queryMonthlyUsage(id, start, end);
            List<TenantService.QuotaDefRow> quotas = tenantService.queryQuotaDefinitions(id);

            long totalCostCents = 0;
            List<InvoiceLineVO> lineItems = new ArrayList<>();

            for (TenantService.MonthlyUsageRow row : monthlyUsage) {
                String type = row.quotaType();
                long totalUsed = row.totalUsed() == null ? 0L : row.totalUsed();

                long costCents = 0;
                if ("API_CALLS".equals(type)) {
                    costCents = Math.round(totalUsed * 0.10 / 1000.0 * 100);
                } else if ("STORAGE_MB".equals(type)) {
                    costCents = Math.round(totalUsed * 0.05 / 1024.0 * 100);
                }

                totalCostCents += costCents;
                lineItems.add(new InvoiceLineVO(type, totalUsed,
                        "API_CALLS".equals(type) ? "¥0.10/千次" : "¥0.05/GB",
                        costCents, String.format("¥%.2f", costCents / 100.0)));
            }

            return ApiResponse.success(new InvoiceResultVO(id, ym.toString(), quotas, lineItems,
                    totalCostCents, String.format("¥%.2f", totalCostCents / 100.0),
                    java.time.Instant.now().toString()));
        } catch (Exception e) {
            log.error("生成账单失败: id={}", id, e);
            return ApiResponse.internalError("生成账单失败");
        }
    }

    // ── 请求 DTO（#147CJ） ─────────────────────────────────────

    /** 建租户请求（缺省值口径与旧 Map 读取一致）。 */
    public static final class TenantCreateRequest {
        private String id;
        private String tenantName;
        private String tenantCode;
        private String status;
        private Integer maxUsers;
        private Long maxStorageMb;
        private Long maxApiPerDay;
        private String isolationMode;
        private String schemaName;
        private String databaseUrl;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTenantName() {
            return tenantName;
        }

        public void setTenantName(String tenantName) {
            this.tenantName = tenantName;
        }

        public String getTenantCode() {
            return tenantCode;
        }

        public void setTenantCode(String tenantCode) {
            this.tenantCode = tenantCode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(Integer maxUsers) {
            this.maxUsers = maxUsers;
        }

        public Long getMaxStorageMb() {
            return maxStorageMb;
        }

        public void setMaxStorageMb(Long maxStorageMb) {
            this.maxStorageMb = maxStorageMb;
        }

        public Long getMaxApiPerDay() {
            return maxApiPerDay;
        }

        public void setMaxApiPerDay(Long maxApiPerDay) {
            this.maxApiPerDay = maxApiPerDay;
        }

        public String getIsolationMode() {
            return isolationMode;
        }

        public void setIsolationMode(String isolationMode) {
            this.isolationMode = isolationMode;
        }

        public String getSchemaName() {
            return schemaName;
        }

        public void setSchemaName(String schemaName) {
            this.schemaName = schemaName;
        }

        public String getDatabaseUrl() {
            return databaseUrl;
        }

        public void setDatabaseUrl(String databaseUrl) {
            this.databaseUrl = databaseUrl;
        }
    }

    /** 改租户请求（{@code xxxPresent} 保留旧 containsKey 语义）。 */
    public static final class TenantUpdateRequest {
        private String tenantName;
        private String tenantCode;
        private String status;
        private Integer maxUsers;
        private Long maxStorageMb;
        private Long maxApiPerDay;
        private String isolationMode;
        private String schemaName;
        private String databaseUrl;
        private boolean tenantNamePresent;
        private boolean tenantCodePresent;
        private boolean statusPresent;
        private boolean maxUsersPresent;
        private boolean maxStorageMbPresent;
        private boolean maxApiPerDayPresent;
        private boolean isolationModePresent;
        private boolean schemaNamePresent;
        private boolean databaseUrlPresent;

        public String getTenantName() {
            return tenantName;
        }

        public void setTenantName(String tenantName) {
            this.tenantName = tenantName;
            this.tenantNamePresent = true;
        }

        public String getTenantCode() {
            return tenantCode;
        }

        public void setTenantCode(String tenantCode) {
            this.tenantCode = tenantCode;
            this.tenantCodePresent = true;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
            this.statusPresent = true;
        }

        public Integer getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(Integer maxUsers) {
            this.maxUsers = maxUsers;
            this.maxUsersPresent = true;
        }

        public Long getMaxStorageMb() {
            return maxStorageMb;
        }

        public void setMaxStorageMb(Long maxStorageMb) {
            this.maxStorageMb = maxStorageMb;
            this.maxStorageMbPresent = true;
        }

        public Long getMaxApiPerDay() {
            return maxApiPerDay;
        }

        public void setMaxApiPerDay(Long maxApiPerDay) {
            this.maxApiPerDay = maxApiPerDay;
            this.maxApiPerDayPresent = true;
        }

        public String getIsolationMode() {
            return isolationMode;
        }

        public void setIsolationMode(String isolationMode) {
            this.isolationMode = isolationMode;
            this.isolationModePresent = true;
        }

        public String getSchemaName() {
            return schemaName;
        }

        public void setSchemaName(String schemaName) {
            this.schemaName = schemaName;
            this.schemaNamePresent = true;
        }

        public String getDatabaseUrl() {
            return databaseUrl;
        }

        public void setDatabaseUrl(String databaseUrl) {
            this.databaseUrl = databaseUrl;
            this.databaseUrlPresent = true;
        }

        public boolean isTenantNamePresent() {
            return tenantNamePresent;
        }

        public boolean isTenantCodePresent() {
            return tenantCodePresent;
        }

        public boolean isStatusPresent() {
            return statusPresent;
        }

        public boolean isMaxUsersPresent() {
            return maxUsersPresent;
        }

        public boolean isMaxStorageMbPresent() {
            return maxStorageMbPresent;
        }

        public boolean isMaxApiPerDayPresent() {
            return maxApiPerDayPresent;
        }

        public boolean isIsolationModePresent() {
            return isolationModePresent;
        }

        public boolean isSchemaNamePresent() {
            return schemaNamePresent;
        }

        public boolean isDatabaseUrlPresent() {
            return databaseUrlPresent;
        }
    }

    /** 改配额请求（JSON 键保持旧 snake_case）。 */
    public static final class QuotaUpdateRequest {
        @JsonProperty("quota_type")
        private String quotaType;
        @JsonProperty("daily_limit")
        private Long dailyLimit;
        @JsonProperty("monthly_limit")
        private Long monthlyLimit;

        public String getQuotaType() {
            return quotaType;
        }

        public void setQuotaType(String quotaType) {
            this.quotaType = quotaType;
        }

        public Long getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(Long dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public Long getMonthlyLimit() {
            return monthlyLimit;
        }

        public void setMonthlyLimit(Long monthlyLimit) {
            this.monthlyLimit = monthlyLimit;
        }
    }

    // ── 响应 VO（#147CJ） ─────────────────────────────────────

    /** 建租户回执（4 键）。 */
    public record CreateResultVO(String id, String tenantName, String tenantCode, String status) {
    }

    /** 配额视图（3 键）。 */
    public record QuotaResultVO(String tenantId, List<TenantService.QuotaRow> quotas,
                                List<TenantService.UsageMaxRow> usage) {
    }

    /** 用量视图（4 键；snake_case 由属性注解保持）。 */
    public record UsageResultVO(@JsonProperty("tenant_id") String tenantId,
                                @JsonProperty("range_days") int rangeDays,
                                @JsonProperty("daily_usage") List<TenantService.DailyUsageRow> dailyUsage,
                                @JsonProperty("totals_by_type") Map<String, Long> totalsByType) {
    }

    /** 账单视图（7 键；snake_case 由属性注解保持）。 */
    public record InvoiceResultVO(@JsonProperty("tenant_id") String tenantId,
                                  @JsonProperty("month") String month,
                                  @JsonProperty("quota_definitions") List<TenantService.QuotaDefRow> quotaDefinitions,
                                  @JsonProperty("line_items") List<InvoiceLineVO> lineItems,
                                  @JsonProperty("total_cost_cents") long totalCostCents,
                                  @JsonProperty("total_cost_display") String totalCostDisplay,
                                  @JsonProperty("generated_at") String generatedAt) {
    }

    /** 账单行（5 键）。 */
    public record InvoiceLineVO(@JsonProperty("quota_type") String quotaType,
                                @JsonProperty("usage") long usage,
                                @JsonProperty("unit_price") String unitPrice,
                                @JsonProperty("cost_cents") long costCents,
                                @JsonProperty("cost_display") String costDisplay) {
    }
}
