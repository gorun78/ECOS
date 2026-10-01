package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code QuotaFilter}（P2-17 租户配额）内联的
 * {@code ecos_tenant_quota} / {@code ecos_tenant_usage} 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Filter 层逐字节一致；
 * {@code determineQuotaType} / 缓存等配额决策逻辑仍留在 Filter。</p>
 */
@Repository
public class QuotaDao extends JdbcTemplateDaoSupport {

    /** 查询该租户对应配额类型的日限额（daily_limit 列）。 */
    public List<Map<String, Object>> findDailyLimit(String tenantId, String quotaType) {
        return jdbcTemplate.queryForList(
            "SELECT daily_limit FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?",
            tenantId, quotaType);
    }

    /**
     * PostgreSQL 原子 UPSERT + RETURNING：当日已用配额原子递增，返回递增后的 used_count。
     * 超过 dailyLimit 时 WHERE 子句阻止递增，返回空列表。
     */
    public List<Long> atomicIncrementUsage(String tenantId, String today, String quotaType,
                                           long usageIncrement, long dailyLimit) {
        String atomicSql =
            "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
            "VALUES (?, ?::date, ?, ?, NOW()) " +
            "ON CONFLICT (tenant_id, usage_date, quota_type) " +
            "DO UPDATE SET used_count = ecos_tenant_usage.used_count + ?, updated_at = NOW() " +
            "WHERE ecos_tenant_usage.used_count < ? " +
            "RETURNING used_count";
        return jdbcTemplate.query(atomicSql,
            (ResultSet rs, int rowNum) -> rs.getLong("used_count"),
            tenantId, today, quotaType, usageIncrement, usageIncrement, dailyLimit);
    }

    /** 查询当日当前已用量（超限兜底回填用）。 */
    public List<Map<String, Object>> findUsageCount(String tenantId, String today, String quotaType) {
        return jdbcTemplate.queryForList(
            "SELECT used_count FROM ecos_tenant_usage WHERE tenant_id = ? AND usage_date = ?::date AND quota_type = ?",
            tenantId, today, quotaType);
    }
}
