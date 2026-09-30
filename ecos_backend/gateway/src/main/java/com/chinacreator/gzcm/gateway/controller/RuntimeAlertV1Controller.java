package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 详细设计-00 B.4.2 告警中心页面（F00-09）— {@code /api/v1/monitor/alerts} 族。
 *
 * <p>承载详细设计-00 §6.2 落库表 {@code ecos_runtime_alert_record} 的列表 / 详情 / ack；
 * ack .permissions = L2+ （前端 {@code platform:alert:ack} 权限码）。</p>
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class RuntimeAlertV1Controller {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertV1Controller.class);

    private final JdbcTemplate jdbc;

    public RuntimeAlertV1Controller(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false, defaultValue = "open") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE status = ? AND is_deleted = 0");
        args.add(status);
        if (severity != null && !severity.isBlank()) {
            where.append(" AND severity = ?");
            args.add(severity);
        }
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, rule_code, severity, status, title, source_module, trace_id, ack_by, " +
                    "ack_time, close_time, occurred_at, tenant_id FROM ecos_runtime_alert_record " + where +
                    " ORDER BY occurred_at DESC LIMIT ? OFFSET ?",
                    args.toArray(new Object[0]));
            args.add(size);
            args.add(offset);
            int total = (int) count("SELECT COUNT(*) FROM ecos_runtime_alert_record " + where,
                    args.subList(0, args.size() - 2).toArray(new Object[0]));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ApiResponse.success(out).withTraceIdFromMdc();
        } catch (Exception ex) {
            log.error("RuntimeAlertV1 list failed (fail-closed): {}", ex.getMessage());
            return ApiResponse.<Map<String, Object>>error(503, "ECOS-AUTH-005", "用户权限依赖不可用，请稍后重试").withTraceIdFromMdc();
        }
    }

    private long count(String sql, Object[] args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @GetMapping("/alerts/{id}")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ecos_runtime_alert_record WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) return ApiResponse.<Map<String, Object>>notFound("告警不存在或已删除").withTraceIdFromMdc();
        return ApiResponse.success(rows.get(0)).withTraceIdFromMdc();
    }

    @PostMapping("/alerts/{id}/ack")
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> ack(@PathVariable String id) {
        String by = MDC.get("userId");
        if (by == null) by = "sys";
        int updated = jdbc.update(
                "UPDATE ecos_runtime_alert_record SET status='acked', ack_by=?, ack_time=NOW(), " +
                "update_time=NOW() WHERE id = ? AND status = 'open'", by, id);
        if (updated == 0) return ApiResponse.<Map<String, Object>>badRequest("告警已被 ack 或不存在").withTraceIdFromMdc();
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("id", id);
        ack.put("status", "acked");
        return ApiResponse.success(ack).withTraceIdFromMdc();
    }

    /** DLQ 列表 + 重放：W13 关闭后 DDL 已接管，前端告警中心可切换 tab 展示。 */
    @GetMapping("/dlq")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> dlq(
            @RequestParam(required = false, defaultValue = "pending") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id, topic, dlt_topic, error_message, attempts, first_seen_at, " +
                    "replayed_at, status FROM ecos_runtime_event_dlq WHERE status = ? AND is_deleted = 0 " +
                    "ORDER BY first_seen_at DESC LIMIT ? OFFSET ?", status, size, offset);
            int total = (int) count("SELECT COUNT(*) FROM ecos_runtime_event_dlq WHERE status = ?", new Object[]{status});
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ApiResponse.success(out).withTraceIdFromMdc();
        } catch (Exception ex) {
            log.error("dlq list failed: {}", ex.getMessage());
            return ApiResponse.<Map<String, Object>>error(503, "ECOS-AUTH-005", "dlq 依赖不可用").withTraceIdFromMdc();
        }
    }

    @PostMapping("/dlq/{id}/replay")
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> replay(@PathVariable String id) {
        int updated = jdbc.update(
                "UPDATE ecos_runtime_event_dlq SET status='replayed', replayed_at=NOW(), attempts=attempts+1, " +
                "update_time=NOW() WHERE id = ? AND status = 'pending'", id);
        if (updated == 0) return ApiResponse.<Map<String, Object>>badRequest("DLQ 消费失败：记录不存在或已处理").withTraceIdFromMdc();
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("id", id);
        rep.put("status", "replayed");
        return ApiResponse.success(rep).withTraceIdFromMdc();
    }
}
