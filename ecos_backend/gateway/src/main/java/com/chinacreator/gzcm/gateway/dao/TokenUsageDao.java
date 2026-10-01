package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code TokenAuditService}（P3-5 Token 用量审计）
 * 内联的 {@code ecos_token_usage} 裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Service 层逐字节一致。</p>
 */
@Repository
public class TokenUsageDao extends JdbcTemplateDaoSupport {

    /** 记录一次 Token 用量（P3-5）。 */
    public void recordUsage(String traceId, String model, int promptTokens, int completionTokens,
                            String operation) {
        int total = promptTokens + completionTokens;
        jdbcTemplate.update(
            "INSERT INTO ecos_token_usage (trace_id, model, operation, " +
            "prompt_tokens, completion_tokens, total_tokens, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            traceId, model, operation,
            promptTokens, completionTokens, total,
            Instant.now());
    }

    /** 带延迟的记录（P3-5）。 */
    public void recordUsage(String traceId, String model, int promptTokens, int completionTokens,
                            String operation, long latencyMs) {
        int total = promptTokens + completionTokens;
        jdbcTemplate.update(
            "INSERT INTO ecos_token_usage (trace_id, model, operation, " +
            "prompt_tokens, completion_tokens, total_tokens, latency_ms, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            traceId, model, operation,
            promptTokens, completionTokens, total,
            latencyMs, Instant.now());
    }
}
