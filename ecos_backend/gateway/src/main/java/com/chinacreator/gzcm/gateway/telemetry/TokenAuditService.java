package com.chinacreator.gzcm.gateway.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * P3-5 Token 用量审计服务。
 * 记录 LLM Token 消耗到 ecos_token_usage 表。
 */
@Service
public class TokenAuditService {

    private static final Logger log = LoggerFactory.getLogger(TokenAuditService.class);

    private final JdbcTemplate jdbc;

    public TokenAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 记录一次 Token 用量。
     *
     * @param traceId          关联的 Trace ID
     * @param model            模型名称
     * @param promptTokens     输入 Token 数
     * @param completionTokens 输出 Token 数
     * @param operation        操作类型 (e.g. "chat", "embedding")
     */
    public void recordUsage(String traceId, String model,
                            int promptTokens, int completionTokens,
                            String operation) {
        int total = promptTokens + completionTokens;
        try {
            jdbc.update(
                "INSERT INTO ecos_token_usage (trace_id, model, operation, " +
                "prompt_tokens, completion_tokens, total_tokens, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                traceId, model, operation,
                promptTokens, completionTokens, total,
                Instant.now()
            );
            log.debug("Token audit recorded: traceId={}, model={}, total={}", traceId, model, total);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }

    /**
     * 带延迟的记录。
     */
    public void recordUsage(String traceId, String model,
                            int promptTokens, int completionTokens,
                            String operation, long latencyMs) {
        int total = promptTokens + completionTokens;
        try {
            jdbc.update(
                "INSERT INTO ecos_token_usage (trace_id, model, operation, " +
                "prompt_tokens, completion_tokens, total_tokens, latency_ms, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                traceId, model, operation,
                promptTokens, completionTokens, total,
                latencyMs, Instant.now()
            );
            log.debug("Token audit recorded: traceId={}, model={}, total={}, latency={}ms",
                traceId, model, total, latencyMs);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }
}
