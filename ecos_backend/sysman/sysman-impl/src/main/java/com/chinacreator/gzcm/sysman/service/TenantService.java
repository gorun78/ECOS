package com.chinacreator.gzcm.sysman.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户服务 — 从 TenantController 下沉的 JdbcTemplate 访问。
 * 操作统一后的 ecos_tenant 表（小写列名），并合并了原 TenantBillingController 的配额/用量/账单查询。
 * SQL 与原 Controller 保持一致。
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbc;

    public TenantService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DateTimeFormatter getMonthFmt() {
        return MONTH_FMT;
    }

    // ═══════════════════════════════════════════════════════════
    //  基础 CRUD
    // ═══════════════════════════════════════════════════════════

    /** 租户列表查询：返回 [total, rows] */
    public Map<String, Object> listTenants(String status, int page, int size) {
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
        return result;
    }

    /** 租户详情：返回行列表（可能为空） */
    public List<Map<String, Object>> getTenant(String id) {
        String sql = "SELECT id, tenant_name, tenant_code, status, max_users, " +
                "max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                "created_at, updated_at " +
                "FROM ecos_tenant WHERE id = ?";
        return jdbc.queryForList(sql, id);
    }

    /** 创建租户 */
    public void createTenant(String id, String tenantName, String tenantCode, String status,
                             int maxUsers, long maxStorageMb, long maxApiPerDay,
                             String isolationMode, String schemaName, String databaseUrl) {
        String sql = "INSERT INTO ecos_tenant (id, tenant_name, tenant_code, status, " +
                "max_users, max_storage_mb, max_api_per_day, isolation_mode, schema_name, database_url, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        jdbc.update(sql, id, tenantName, tenantCode, status,
                maxUsers, maxStorageMb, maxApiPerDay, isolationMode, schemaName, databaseUrl);
    }

    /** 检查租户是否存在，返回行数 */
    public int countTenant(String id) {
        String checkSql = "SELECT COUNT(*) FROM ecos_tenant WHERE id = ?";
        return jdbc.queryForObject(checkSql, Integer.class, id);
    }

    /** 更新租户：执行动态 SET 更新 */
    public void updateTenant(String id, StringBuilder set, List<Object> params) {
        set.append("updated_at = NOW()");
        params.add(id);

        String sql = "UPDATE ecos_tenant SET " + set + " WHERE id = ?";
        jdbc.update(sql, params.toArray());
    }

    /** 软删除租户：返回受影响行数 */
    public int deleteTenant(String id) {
        String sql = "UPDATE ecos_tenant SET status = 'DELETED', updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, id);
    }

    // ═══════════════════════════════════════════════════════════
    //  配额 / 用量 / 账单（从 TenantBillingController 迁入）
    // ═══════════════════════════════════════════════════════════

    /** 查询租户配额列表 */
    public List<Map<String, Object>> queryTenantQuotas(String id) {
        return jdbc.queryForList(
                "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                "FROM ecos_tenant_quota WHERE tenant_id = ?", id);
    }

    /** 查询租户用量（按 quota_type 聚合），失败时返回空列表 */
    public List<Map<String, Object>> queryTenantUsageSummary(String id) {
        String usageSql = "SELECT quota_type, MAX(used_count) AS used_count " +
                "FROM ecos_tenant_usage WHERE tenant_id = ? GROUP BY quota_type";
        return jdbc.queryForList(usageSql, id);
    }

    /** 查询指定 quota_type 的配额是否存在（返回行列表） */
    public List<Map<String, Object>> queryExistingQuota(String id, String quotaType) {
        return jdbc.queryForList(
                "SELECT id FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?",
                id, quotaType);
    }

    /** 插入新配额 */
    public void insertQuota(String id, String quotaType, long dailyLimit, long monthlyLimit) {
        jdbc.update(
                "INSERT INTO ecos_tenant_quota (tenant_id, quota_type, daily_limit, monthly_limit) " +
                "VALUES (?, ?, ?, ?)", id, quotaType, dailyLimit, monthlyLimit);
    }

    /** 更新配额（动态字段） */
    public void updateQuota(String id, String quotaType, StringBuilder sql, List<Object> params) {
        sql.append(" WHERE tenant_id = ? AND quota_type = ?");
        params.add(id);
        params.add(quotaType);
        jdbc.update(sql.toString(), params.toArray());
    }

    /** 查询更新后的配额行 */
    public Map<String, Object> queryQuotaRow(String id, String quotaType) {
        return jdbc.queryForMap(
                "SELECT id, tenant_id, quota_type, daily_limit, monthly_limit, created_at, updated_at " +
                "FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?", id, quotaType);
    }

    /** 查询用量明细（按日期范围） */
    public List<Map<String, Object>> queryDailyUsage(String id, LocalDate startDate) {
        return jdbc.queryForList(
                "SELECT usage_date, quota_type, used_count " +
                "FROM ecos_tenant_usage " +
                "WHERE tenant_id = ? AND usage_date >= ? " +
                "ORDER BY usage_date DESC, quota_type",
                id, startDate);
    }

    /** 查询月度用量汇总 */
    public List<Map<String, Object>> queryMonthlyUsage(String id, LocalDate start, LocalDate end) {
        return jdbc.queryForList(
                "SELECT quota_type, SUM(used_count) AS total_used " +
                "FROM ecos_tenant_usage " +
                "WHERE tenant_id = ? AND usage_date BETWEEN ? AND ? " +
                "GROUP BY quota_type",
                id, start, end);
    }

    /** 查询租户配额定义 */
    public List<Map<String, Object>> queryQuotaDefinitions(String id) {
        return jdbc.queryForList(
                "SELECT quota_type, daily_limit, monthly_limit " +
                "FROM ecos_tenant_quota WHERE tenant_id = ?", id);
    }
}
