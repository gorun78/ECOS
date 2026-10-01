package com.chinacreator.gzcm.gateway.telemetry;

import com.chinacreator.gzcm.gateway.dao.TokenUsageDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * P3-5 Token 用量审计服务。
 * 记录 LLM Token 消耗到 ecos_token_usage 表。
 *
 * <p>B6b 数据访问收口（架构铁律 §2.5 / IR06）：裸 SQL 全量下沉至 {@link TokenUsageDao}；
 * 本 Service 减薄为编排层——保留 recordUsage 的异常兜底（审计失败不抛）与日志，公共方法签名维持不变。</p>
 */
@Service
public class TokenAuditService {

    private static final Logger log = LoggerFactory.getLogger(TokenAuditService.class);

    private final TokenUsageDao dao;

    public TokenAuditService(TokenUsageDao dao) {
        this.dao = dao;
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
            dao.recordUsage(traceId, model, promptTokens, completionTokens, operation);
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
            dao.recordUsage(traceId, model, promptTokens, completionTokens, operation, latencyMs);
            log.debug("Token audit recorded: traceId={}, model={}, total={}, latency={}ms",
                traceId, model, total, latencyMs);
        } catch (Exception e) {
            log.warn("Failed to record token usage: {}", e.getMessage());
        }
    }
}
