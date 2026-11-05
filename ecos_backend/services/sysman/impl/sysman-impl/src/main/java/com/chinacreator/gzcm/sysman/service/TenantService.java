package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.TenantQueryDao;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 租户服务 — 从 TenantController 下沉的 JdbcTemplate 访问。
 * 操作统一后的 ecos_tenant 表（小写列名），并合并了原 TenantBillingController 的配额/用量/账单查询。
 *
 * <p><b>#147CJ（DoD DI-2 第十四批）</b>：行面由 {@code Map} 改 record（{@link TenantRow}/
 * {@link QuotaRow}/{@link UsageMaxRow}/{@link DailyUsageRow}/{@link MonthlyUsageRow}/
 * {@link QuotaDefRow}），Controller 不再持有弱类型 Map，也不再自拼 SQL。record 组件用
 * {@code @JsonProperty} 钉住旧列名（snake_case），时间列保持 JDBC 类型（{@code Timestamp}/
 * {@code Date}）由同一 ObjectMapper 渲染。属主方法仅 Controller 消费（security 侧走的是另一个
 * {@code ITenantService}），故可整面改签名。</p>
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantQueryDao tenantDao;

    public TenantService(TenantQueryDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    // ═══════════════════════════════════════════════════════════
    //  基础 CRUD
    // ═══════════════════════════════════════════════════════════

    /** 租户列表查询（分页）。 */
    public TenantPage listTenants(String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            where.append("AND status = ? ");
            params.add(status);
        }

        String countSql = "SELECT COUNT(*) FROM ecos_tenant" + where;
        int total = tenantDao.queryForObject(countSql, Integer.class, params.toArray());

        String sql = "SELECT id, tenant_name, tenant_code, status, max_users, " +
                "max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                "created_at, updated_at " +
                "FROM ecos_tenant" + where + " ORDER BY created_at DESC NULLS LAST " +
                "LIMIT ? OFFSET ?";
        List<Object> pagedParams = new ArrayList<>(params);
        pagedParams.add(size);
        pagedParams.add((page - 1) * size);

        List<TenantRow> data = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(sql, pagedParams.toArray())) {
            data.add(toTenantRow(row));
        }
        return new TenantPage(page, size, total, data);
    }

    /** 租户详情（不存在返回 null）。 */
    public TenantRow getTenant(String id) {
        String sql = "SELECT id, tenant_name, tenant_code, status, max_users, " +
                "max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                "created_at, updated_at " +
                "FROM ecos_tenant WHERE id = ?";
        List<Map<String, Object>> rows = tenantDao.queryForList(sql, id);
        return rows.isEmpty() ? null : toTenantRow(rows.get(0));
    }

    /** 创建租户。 */
    public void createTenant(String id, String tenantName, String tenantCode, String status,
                             int maxUsers, long maxStorageMb, long maxApiPerDay,
                             String isolationMode, String schemaName, String databaseUrl) {
        String sql = "INSERT INTO ecos_tenant (id, tenant_name, tenant_code, status, " +
                "max_users, max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        tenantDao.update(sql, id, tenantName, tenantCode, status,
                maxUsers, maxStorageMb, maxApiPerDay, isolationMode, schemaName, databaseUrl);
    }

    /** 检查租户是否存在，返回行数。 */
    public int countTenant(String id) {
        String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
        return tenantDao.queryForObject(checkSql, Integer.class, id);
    }

    /** 更新租户：执行动态 SET 更新。 */
    public void updateTenant(String id, StringBuilder set, List<Object> params) {
        set.append("updated_at = NOW()");
        params.add(id);

        String sql = "UPDATE ecos_tenant SET " + set + " WHERE id = ?";
        tenantDao.update(sql, params.toArray());
    }

    /** 软删除租户：返回受影响行数。 */
    public int deleteTenant(String id) {
        String sql = "UPDATE ecos_tenant SET status = 'DELETED', updated_at = NOW() WHERE id = ?";
        return tenantDao.update(sql, id);
    }

    // ═══════════════════════════════════════════════════════════
    //  配额 / 用量 / 账单
    // ═══════════════════════════════════════════════════════════

    /** 查询租户配额列表。 */
    public List<QuotaRow> queryTenantQuotas(String id) {
        List<QuotaRow> rows = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(
                "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                "FROM ecos_tenant_quota WHERE tenant_id = ?", id)) {
            rows.add(toQuotaRow(row));
        }
        return rows;
    }

    /** 查询租户用量（按 quota_type 聚合）。 */
    public List<UsageMaxRow> queryTenantUsageSummary(String id) {
        String usageSql = "SELECT quota_type, MAX(used_count) AS used_count " +
                "FROM ecos_tenant_usage WHERE tenant_id = ? GROUP BY quota_type";
        List<UsageMaxRow> rows = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(usageSql, id)) {
            rows.add(new UsageMaxRow(asString(row.get("quota_type")), asLong(row.get("used_count"))));
        }
        return rows;
    }

    /** 指定 quota_type 的配额是否已存在。 */
    public boolean quotaExists(String id, String quotaType) {
        List<Map<String, Object>> rows = tenantDao.queryForList(
                "SELECT id FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?",
                id, quotaType);
        return !rows.isEmpty();
    }

    /** 插入新配额。 */
    public void insertQuota(String id, String quotaType, long dailyLimit, long monthlyLimit) {
        tenantDao.update(
                "INSERT INTO ecos_tenant_quota (tenant_id, quota_type, daily_limit, monthly_limit) " +
                "VALUES (?, ?, ?, ?)", id, quotaType, dailyLimit, monthlyLimit);
    }

    /** 更新配额（动态字段）。 */
    public void updateQuota(String id, String quotaType, StringBuilder sql, List<Object> params) {
        sql.append(" WHERE tenant_id = ? AND quota_type = ?");
        params.add(id);
        params.add(quotaType);
        tenantDao.update(sql.toString(), params.toArray());
    }

    /** 查询更新后的配额行（不存在返回 null）。 */
    public QuotaRow queryQuotaRow(String id, String quotaType) {
        Map<String, Object> row = tenantDao.queryForMap(
                "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                "FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?", id, quotaType);
        return row == null ? null : toQuotaRow(row);
    }

    /** 查询用量明细（按日期范围）。 */
    public List<DailyUsageRow> queryDailyUsage(String id, LocalDate startDate) {
        List<DailyUsageRow> rows = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(
                "SELECT usage_date, quota_type, used_count " +
                "FROM ecos_tenant_usage " +
                "WHERE tenant_id = ? AND usage_date >= ? " +
                "ORDER BY usage_date DESC, quota_type",
                id, startDate)) {
            rows.add(new DailyUsageRow(asSqlDate(row.get("usage_date")), asString(row.get("quota_type")),
                    asLong(row.get("used_count"))));
        }
        return rows;
    }

    /** 查询月度用量汇总。 */
    public List<MonthlyUsageRow> queryMonthlyUsage(String id, LocalDate start, LocalDate end) {
        List<MonthlyUsageRow> rows = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(
                "SELECT quota_type, SUM(used_count) AS total_used " +
                "FROM ecos_tenant_usage " +
                "WHERE tenant_id = ? AND usage_date BETWEEN ? AND ? " +
                "GROUP BY quota_type",
                id, start, end)) {
            rows.add(new MonthlyUsageRow(asString(row.get("quota_type")), asLong(row.get("total_used"))));
        }
        return rows;
    }

    /** 查询租户配额定义。 */
    public List<QuotaDefRow> queryQuotaDefinitions(String id) {
        List<QuotaDefRow> rows = new ArrayList<>();
        for (Map<String, Object> row : tenantDao.queryForList(
                "SELECT quota_type, daily_limit, monthly_limit " +
                "FROM ecos_tenant_quota WHERE tenant_id = ?", id)) {
            rows.add(new QuotaDefRow(asString(row.get("quota_type")), asLong(row.get("daily_limit")),
                    asLong(row.get("monthly_limit"))));
        }
        return rows;
    }

    // ── 行映射与类型归一 ──────────────────────────────────────

    private static TenantRow toTenantRow(Map<String, Object> row) {
        return new TenantRow(asString(row.get("id")), asString(row.get("tenant_name")),
                asString(row.get("tenant_code")), asString(row.get("status")),
                asInt(row.get("max_users")), asLong(row.get("max_storage_mb")),
                asLong(row.get("max_api_per_day")), asString(row.get("isolation_mode")),
                asString(row.get("schema_name")), asString(row.get("database_url")),
                asTimestamp(row.get("created_at")), asTimestamp(row.get("updated_at")));
    }

    private static QuotaRow toQuotaRow(Map<String, Object> row) {
        return new QuotaRow(asString(row.get("id")), asString(row.get("tenant_id")),
                asString(row.get("quota_type")), asLong(row.get("daily_limit")),
                asLong(row.get("monthly_limit")), asTimestamp(row.get("created_at")),
                asTimestamp(row.get("updated_at")));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static java.sql.Timestamp asTimestamp(Object value) {
        return value instanceof java.sql.Timestamp ts ? ts : null;
    }

    private static java.sql.Date asSqlDate(Object value) {
        return value instanceof java.sql.Date d ? d : null;
    }

    // ── 行面 record（#147CJ：键名＝旧列名，键序＝SELECT 顺序） ──────

    /** 租户行（12 键）。 */
    public record TenantRow(@JsonProperty("id") String id,
                            @JsonProperty("tenant_name") String tenantName,
                            @JsonProperty("tenant_code") String tenantCode,
                            @JsonProperty("status") String status,
                            @JsonProperty("max_users") Integer maxUsers,
                            @JsonProperty("max_storage_mb") Long maxStorageMb,
                            @JsonProperty("max_api_per_day") Long maxApiPerDay,
                            @JsonProperty("isolation_mode") String isolationMode,
                            @JsonProperty("schema_name") String schemaName,
                            @JsonProperty("database_url") String databaseUrl,
                            @JsonProperty("created_at") java.sql.Timestamp createdAt,
                            @JsonProperty("updated_at") java.sql.Timestamp updatedAt) {
    }

    /** 租户分页（4 键 page/size/total/data）。 */
    public record TenantPage(int page, int size, int total, List<TenantRow> data) {
    }

    /** 配额行（7 键）。 */
    public record QuotaRow(@JsonProperty("id") String id,
                           @JsonProperty("tenant_id") String tenantId,
                           @JsonProperty("quota_type") String quotaType,
                           @JsonProperty("daily_limit") Long dailyLimit,
                           @JsonProperty("monthly_limit") Long monthlyLimit,
                           @JsonProperty("created_at") java.sql.Timestamp createdAt,
                           @JsonProperty("updated_at") java.sql.Timestamp updatedAt) {
    }

    /** 用量峰值行（2 键）。 */
    public record UsageMaxRow(@JsonProperty("quota_type") String quotaType,
                              @JsonProperty("used_count") Long usedCount) {
    }

    /** 日用量行（3 键）。 */
    public record DailyUsageRow(@JsonProperty("usage_date") java.sql.Date usageDate,
                                @JsonProperty("quota_type") String quotaType,
                                @JsonProperty("used_count") Long usedCount) {
    }

    /** 月用量汇总行（2 键）。 */
    public record MonthlyUsageRow(@JsonProperty("quota_type") String quotaType,
                                  @JsonProperty("total_used") Long totalUsed) {
    }

    /** 配额定义行（3 键）。 */
    public record QuotaDefRow(@JsonProperty("quota_type") String quotaType,
                              @JsonProperty("daily_limit") Long dailyLimit,
                              @JsonProperty("monthly_limit") Long monthlyLimit) {
    }
}
