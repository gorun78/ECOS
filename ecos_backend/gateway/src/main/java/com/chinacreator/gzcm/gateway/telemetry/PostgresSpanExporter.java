package com.chinacreator.gzcm.gateway.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

/**
 * P3-5 OpenTelemetry SpanExporter — 将 OTel Span 写入 PostgreSQL ecos_spans 表。
 * <p>
 * 替代原来 TelemetryInterceptor 中直接 JDBC 写库的方式，
 * 改为通过标准 OTel SpanExporter 接口异步写入。
 */
public class PostgresSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(PostgresSpanExporter.class);

    private final JdbcTemplate jdbc;

    public PostgresSpanExporter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        for (SpanData span : spans) {
            try {
                writeSpan(span);
            } catch (Exception e) {
                log.warn("Failed to export span {}: {}", span.getSpanId(), e.getMessage());
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    private void writeSpan(SpanData span) {
        Attributes attrs = span.getAttributes();

        String spanId = span.getSpanId();
        String traceId = span.getTraceId();
        String parentSpanId = span.getParentSpanId();
        String operationName = span.getName();
        String serviceName = span.getResource().getAttribute(
                io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME);
        if (serviceName == null) {
            serviceName = "ecos-gateway";
        }

        String httpMethod = attrs.get(AttributeKey.stringKey("http.method"));
        String httpPath = attrs.get(AttributeKey.stringKey("http.path"));
        Long httpStatus = attrs.get(AttributeKey.longKey("http.status"));
        int httpStatusInt = httpStatus != null ? httpStatus.intValue() : 0;

        long startEpochNanos = span.getStartEpochNanos();
        long endEpochNanos = span.getEndEpochNanos();
        long durationMs = (endEpochNanos - startEpochNanos) / 1_000_000;

        Instant startTime = Instant.ofEpochMilli(startEpochNanos / 1_000_000);
        Instant endTime = Instant.ofEpochMilli(endEpochNanos / 1_000_000);

        String statusLabel = span.getStatus().getStatusCode().name();
        if ("UNSET".equals(statusLabel)) {
            statusLabel = httpStatus != null && httpStatus < 400 ? "OK" : "ERROR";
        }

        jdbc.update(
                "INSERT INTO ecos_spans (span_id, trace_id, parent_span_id, operation_name, " +
                "service_name, http_method, http_path, http_status, " +
                "start_time, end_time, duration_ms, status, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT (span_id) DO NOTHING",
                spanId, traceId,
                parentSpanId.isEmpty() ? null : parentSpanId,
                operationName,
                serviceName,
                httpMethod, httpPath, httpStatusInt,
                Timestamp.from(startTime),
                Timestamp.from(endTime),
                durationMs, statusLabel,
                Timestamp.from(Instant.now())
        );
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        log.info("PostgresSpanExporter shutting down");
        return CompletableResultCode.ofSuccess();
    }
}
