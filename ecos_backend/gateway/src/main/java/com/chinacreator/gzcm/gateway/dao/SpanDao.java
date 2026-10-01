package com.chinacreator.gzcm.gateway.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * B6b 数据访问收口（架构铁律 §2.5 / IR06）：{@code PostgresSpanExporter}（P3-5 OTel
 * SpanExporter）内联的 {@code ecos_spans} 写入裸 SQL 落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与 Exporter 层逐字节一致；
 * {@code SpanData → 列} 的字段归一化仍留在 Exporter（它持 OTel 语义），本 DAO 只落 SQL。</p>
 */
@Repository
public class SpanDao extends JdbcTemplateDaoSupport {

    public void insertSpan(String spanId, String traceId, String parentSpanId, String operationName,
                           String serviceName, String httpMethod, String httpPath, int httpStatus,
                           Timestamp startTime, Timestamp endTime, long durationMs, String statusLabel,
                           Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO ecos_spans (span_id, trace_id, parent_span_id, operation_name, " +
                "service_name, http_method, http_path, http_status, " +
                "start_time, end_time, duration_ms, status, created_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT (span_id) DO NOTHING",
                spanId, traceId,
                parentSpanId,
                operationName,
                serviceName,
                httpMethod, httpPath, httpStatus,
                startTime,
                endTime,
                durationMs, statusLabel,
                createdAt
        );
    }
}
