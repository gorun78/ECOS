package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code UsageCollector}（A7 租户用量定时聚合）
 * 内联的 {@code ecos_spans}/{@code ecos_token_usage} → {@code ecos_tenant_usage} 聚合
 * 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Collector 层逐字节一致
 * （Wave-7 T-29 (R5) 修复口径：按 'unknown' 租户兜底聚合，列名 total_tokens）。</p>
 */
@Repository
public class UsageAggregateDao extends JdbcTemplateDaoSupport {

    /** 聚合当日 API 调用量（ecos_spans → ecos_tenant_usage，quota_type=API_CALLS）。 */
    public int aggregateApiCalls(String today) {
        String aggregateSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT 'unknown' AS tenant_id, ?::date, 'API_CALLS', COUNT(*) AS used_count, NOW() " +
                "FROM ecos_spans s " +
                "WHERE s.created_at::date = ?::date " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";
        return jdbcTemplate.update(aggregateSql, today, today);
    }

    /** 聚合当日 token 用量（ecos_token_usage → ecos_tenant_usage，quota_type=TOKENS）。 */
    public int aggregateTokens(String today) {
        String tokenAggregateSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT 'unknown' AS tenant_id, ?::date, 'TOKENS', COALESCE(SUM(total_tokens), 0) AS used_count, NOW() " +
                "FROM ecos_token_usage " +
                "WHERE created_at::date = ?::date " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";
        return jdbcTemplate.update(tokenAggregateSql, today, today);
    }
}
