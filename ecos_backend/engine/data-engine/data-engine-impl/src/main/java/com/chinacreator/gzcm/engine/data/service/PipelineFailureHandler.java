package com.chinacreator.gzcm.engine.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Pipeline 失败处理 — 重试 + 降级策略（PMO-36 T1）。
 *
 * <p>节点失败重试 N 次（指数退避）→ 仍失败 → Fallback（跳过/降级/终止）。</p>
 */
@Component
public class PipelineFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(PipelineFailureHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 降级策略 */
    public enum Fallback { SKIP, TERMINATE, DEFAULT_VALUE }

    /** 执行结果 */
    public static class HandlerResult {
        public final boolean success;
        public final Object data;
        public final String errorMsg;
        public final int attempts;
        public final boolean fallbackApplied;

        public HandlerResult(boolean success, Object data, String errorMsg, int attempts, boolean fallbackApplied) {
            this.success = success;
            this.data = data;
            this.errorMsg = errorMsg;
            this.attempts = attempts;
            this.fallbackApplied = fallbackApplied;
        }
    }

    /**
     * 带重试执行节点逻辑。
     *
     * @param stepRunId 步骤运行 ID
     * @param configJson 节点配置 JSON（含 retry/fallback 配置）
     * @param action 实际执行逻辑
     * @return 执行结果
     */
    public HandlerResult executeWithRetry(String stepRunId, String configJson, Supplier<Object> action) {
        Map<String, Object> config = parseConfig(configJson);
        int maxRetry = config.containsKey("retry") ? ((Number) config.get("retry")).intValue() : 2;
        long backoffMs = config.containsKey("backoff_ms") ? ((Number) config.get("backoff_ms")).longValue() : 1000L;
        Fallback fallback = Fallback.valueOf(
            ((String) config.getOrDefault("fallback", "SKIP")).toUpperCase());

        Exception lastError = null;
        int attempt = 0;

        for (attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                Object result = action.get();
                if (attempt > 1) {
                    log.info("Pipeline 步骤重试成功: stepRunId={}, attempt={}", stepRunId, attempt);
                }
                return new HandlerResult(true, result, null, attempt, false);
            } catch (Exception e) {
                lastError = e;
                log.warn("Pipeline 步骤失败: stepRunId={}, attempt={}/{}, error={}",
                    stepRunId, attempt, maxRetry, e.getMessage());
                if (attempt < maxRetry) {
                    long delay = backoffMs * (1L << (attempt - 1)); // 指数退避
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { break; }
                }
            }
        }

        // 重试耗尽 → 降级
        log.warn("Pipeline 步骤重试耗尽, 应用降级策略: stepRunId={}, fallback={}", stepRunId, fallback);
        switch (fallback) {
            case TERMINATE:
                throw new RuntimeException("步骤失败且策略为 TERMINATE: " +
                    (lastError != null ? lastError.getMessage() : "unknown"), lastError);
            case SKIP:
                return new HandlerResult(false, null, lastError != null ? lastError.getMessage() : "retry exhausted",
                    attempt - 1, true);
            case DEFAULT_VALUE:
                return new HandlerResult(true, null, "fallback to default value", attempt - 1, true);
            default:
                return new HandlerResult(false, null, lastError != null ? lastError.getMessage() : "retry exhausted",
                    attempt - 1, true);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) return Map.of();
        try {
            return MAPPER.readValue(configJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
