-- P3-5 OpenTelemetry: 遥测数据表
-- 存储 HTTP 请求 Span 和 Token 用量审计

CREATE TABLE IF NOT EXISTS ecos_spans (
    span_id VARCHAR(64) PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    operation_name VARCHAR(512),
    service_name VARCHAR(128),
    http_method VARCHAR(16),
    http_path VARCHAR(512),
    http_status INT DEFAULT 0,
    start_time TIMESTAMP NOT NULL DEFAULT NOW(),
    end_time TIMESTAMP,
    duration_ms BIGINT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'OK',
    attributes JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_spans_trace ON ecos_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_spans_created ON ecos_spans(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_spans_path ON ecos_spans(http_path);

CREATE TABLE IF NOT EXISTS ecos_token_usage (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64),
    model VARCHAR(64),
    operation VARCHAR(256),
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    cost_estimate DECIMAL(10,6) DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_token_usage_trace ON ecos_token_usage(trace_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_created ON ecos_token_usage(created_at DESC);
