package com.chinacreator.gzcm.gateway.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * P3-5 遥测表自动初始化。
 * 在应用启动完成后创建 ecos_spans 和 ecos_token_usage 表（幂等）。
 */
@Component
public class TelemetryTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryTableInitializer.class);

    private final JdbcTemplate jdbc;

    public TelemetryTableInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initTables() {
        try {
            jdbc.execute("""
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
                )
                """);

            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_spans_trace ON ecos_spans(trace_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_spans_created ON ecos_spans(created_at DESC)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_spans_path ON ecos_spans(http_path)");

            jdbc.execute("""
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
                )
                """);

            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_token_usage_trace ON ecos_token_usage(trace_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_token_usage_created ON ecos_token_usage(created_at DESC)");

            log.info("P3-5 telemetry tables initialized successfully");
        } catch (Exception e) {
            log.warn("P3-5 telemetry table init warning (may be ok if tables exist): {}", e.getMessage());
        }
    }
}
