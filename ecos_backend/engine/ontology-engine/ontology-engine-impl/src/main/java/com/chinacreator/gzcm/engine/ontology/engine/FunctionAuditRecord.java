package com.chinacreator.gzcm.engine.ontology.engine;

/**
 * FunctionAuditRecord — Function 审计落库的记录载体（W80/W81/W82 · F03-06）。
 *
 * <p>对应 V172 新表 {@code ecos_ontology.ecos_function_audit} 的可归因字段，
 * 以及 {@code FUNCTION_EXEC} Kafka 事件的 detail 清单：</p>
 * <ul>
 *   <li>{@code functionId}    → {@code function_id}（函数注册键；/execute 传 propertyId）</li>
 *   <li>{@code functionName}  → {@code function_name}（可归因函数名，禁止为 null）</li>
 *   <li>{@code expression}    → {@code expression}（事件侧截断 ≤512，落库存全长）</li>
 *   <li>{@code objectType}    → {@code object_type}（旧列 entity_name 的规范语义名）</li>
 *   <li>{@code params}        → {@code params_json}（DR04/MC02 JSON 文本，只做存取）</li>
 *   <li>{@code resultSummary} → {@code result_summary}</li>
 *   <li>{@code durationMs}    → {@code duration_ms}（INTEGER）</li>
 *   <li>{@code cacheHit}      → {@code is_cache_hit}（DR05 SMALLINT 0/1）</li>
 *   <li>{@code callerId}      → {@code caller_id}（只取 UserContext，缺失=400 ECOS-ONTO-060 拒收）</li>
 *   <li>{@code traceId}       → {@code trace_id}（REQ-NF-04 链路标识）</li>
 *   <li>{@code status}        → {@code status}（SUCCESS/ERROR/TIMEOUT/FORBIDDEN）</li>
 *   <li>{@code errorMessage}  → {@code error_message}</li>
 * </ul>
 *
 * <p>使用 {@link #builder()} 构造，保证字段语义清晰、禁止实参错位。</p>
 */
public record FunctionAuditRecord(
        String functionId,
        String functionName,
        String expression,
        String objectType,
        Object params,
        String resultSummary,
        long durationMs,
        boolean cacheHit,
        String callerId,
        String traceId,
        String status,
        String errorMessage) {

    public static Builder builder() {
        return new Builder();
    }

    /** 便于调试的可读表示。 */
    @Override
    public String toString() {
        return "FunctionAuditRecord{functionId=" + functionId + ", functionName=" + functionName
                + ", status=" + status + ", callerId=" + callerId + ", traceId=" + traceId + "}";
    }

    /** Fluent builder，避免 12 参数实参错位（W80 根因之一）。 */
    public static final class Builder {
        private String functionId;
        private String functionName;
        private String expression;
        private String objectType;
        private Object params;
        private String resultSummary;
        private long durationMs;
        private boolean cacheHit;
        private String callerId;
        private String traceId;
        private String status;
        private String errorMessage;

        public Builder functionId(String functionId) { this.functionId = functionId; return this; }
        public Builder functionName(String functionName) { this.functionName = functionName; return this; }
        public Builder expression(String expression) { this.expression = expression; return this; }
        public Builder objectType(String objectType) { this.objectType = objectType; return this; }
        public Builder params(Object params) { this.params = params; return this; }
        public Builder resultSummary(String resultSummary) { this.resultSummary = resultSummary; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder cacheHit(boolean cacheHit) { this.cacheHit = cacheHit; return this; }
        public Builder callerId(String callerId) { this.callerId = callerId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public FunctionAuditRecord build() {
            return new FunctionAuditRecord(functionId, functionName, expression, objectType,
                    params, resultSummary, durationMs, cacheHit, callerId, traceId, status, errorMessage);
        }
    }
}
