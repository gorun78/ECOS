package com.chinacreator.gzcm.gateway.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * A7: 租户用量定时聚合器。
 * <p>
 * 每分钟从 ecos_spans 和 ecos_token_usage 表中聚合租户 API 调用量，
 * 写入 ecos_tenant_usage 表。这确保配额检查的 ecos_tenant_usage 表
 * 始终反映最近一分钟内的实际用量。
 * </p>
 *
 * <h3>数据源</h3>
 * <ul>
 *   <li>ecos_spans: 按 tenant_id + 当日日期统计 API 请求数</li>
 *   <li>ecos_token_usage: 按 tenant_id + 当日日期汇总 token 用量</li>
 * </ul>
 *
 * <h3>目标表</h3>
 * <ul>
 *   <li>ecos_tenant_usage: UPSERT (tenant_id, usage_date, quota_type)</li>
 * </ul>
 */
@Component
public class UsageCollector {

    private static final Logger log = LoggerFactory.getLogger(UsageCollector.class);

    private final JdbcTemplate jdbc;

    public UsageCollector(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 每分钟执行一次 — 聚合过去 1 分钟内的 API 调用量。
     */
    @Scheduled(fixedRate = 60_000)
    public void aggregateApiUsage() {
        try {
            String today = LocalDate.now().toString();

            // 从 ecos_spans 聚合每个租户的当日 API 调用次数
            String aggregateSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT s.tenant_id, ?::date, 'API_CALLS', COUNT(*) AS used_count, NOW() " +
                "FROM ecos_spans s " +
                "WHERE s.created_at::date = ?::date AND s.tenant_id IS NOT NULL " +
                "GROUP BY s.tenant_id " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";

            int rows = jdbc.update(aggregateSql, today, today);
            if (rows > 0) {
                log.debug("A7 UsageCollector aggregated {} tenant usage records for {}", rows, today);
            }

            // 可选: 从 ecos_token_usage 聚合 token 用量
            String tokenAggregateSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "SELECT tenant_id, ?::date, 'TOKENS', SUM(tokens) AS used_count, NOW() " +
                "FROM ecos_token_usage " +
                "WHERE usage_date = ?::date AND tenant_id IS NOT NULL " +
                "GROUP BY tenant_id " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = EXCLUDED.used_count, updated_at = NOW()";

            int tokenRows = jdbc.update(tokenAggregateSql, today, today);
            if (tokenRows > 0) {
                log.debug("A7 UsageCollector aggregated {} tenant token usage records for {}", tokenRows, today);
            }

        } catch (Exception e) {
            log.warn("A7 UsageCollector aggregation failed: {}", e.getMessage());
        }
    }
}
