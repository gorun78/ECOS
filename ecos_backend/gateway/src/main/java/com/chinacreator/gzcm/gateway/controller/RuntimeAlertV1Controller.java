package com.chinacreator.gzcm.gateway.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.gateway.service.RuntimeAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-00 B.4.2 告警中心页面（F00-09）— {@code /api/v1/monitor/alerts} 族。
 *
 * <p>承载 §6.2 落库表 {@code ecos_runtime_alert_record} 的列表 / 详情 / ack，
 * 以及 DLQ 列表 / 重放。持久化经 {@link RuntimeAlertRepository}（Controller 不直连
 * JdbcTemplate — ArchitectureTest 门禁）。ack 权限 = L2+，replay = L3+。</p>
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class RuntimeAlertV1Controller {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAlertV1Controller.class);

    private final RuntimeAlertRepository repo;

    public RuntimeAlertV1Controller(RuntimeAlertRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false, defaultValue = "open") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = repo.listAlerts(severity, status, size, offset);
            int total = repo.countAlerts(severity, status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ApiResponse.success(out).withTraceIdFromMdc();
        } catch (Exception ex) {
            log.error("RuntimeAlertV1 list failed (fail-closed): {}", ex.getMessage());
            return ApiResponse.<Map<String, Object>>error(503, "ECOS-AUTH-005",
                    "用户权限依赖不可用，请稍后重试").withTraceIdFromMdc();
        }
    }

    @GetMapping("/alerts/{id}")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        List<Map<String, Object>> rows = repo.findAlertById(id);
        if (rows.isEmpty()) {
            return ApiResponse.<Map<String, Object>>notFound("告警不存在或已删除").withTraceIdFromMdc();
        }
        return ApiResponse.success(rows.get(0)).withTraceIdFromMdc();
    }

    @PostMapping("/alerts/{id}/ack")
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> ack(@PathVariable String id) {
        String by = MDC.get("userId");
        if (by == null) by = "sys";
        int updated = repo.ackAlert(id, by);
        if (updated == 0) {
            return ApiResponse.<Map<String, Object>>badRequest("告警已被 ack 或不存在").withTraceIdFromMdc();
        }
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("id", id);
        ack.put("status", "acked");
        return ApiResponse.success(ack).withTraceIdFromMdc();
    }

    /** DLQ 列表（W13；表由 V243 建立）。 */
    @GetMapping("/dlq")
    @PreAuthorize("hasAuthority('platform:alert:read') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> dlq(
            @RequestParam(required = false, defaultValue = "pending") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int offset = Math.max(0, (page - 1) * size);
        try {
            List<Map<String, Object>> rows = repo.listDlq(status, size, offset);
            int total = repo.countDlq(status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("page", page);
            out.put("size", size);
            return ApiResponse.success(out).withTraceIdFromMdc();
        } catch (Exception ex) {
            log.error("dlq list failed: {}", ex.getMessage());
            return ApiResponse.<Map<String, Object>>error(503, "ECOS-AUTH-005",
                    "dlq 依赖不可用").withTraceIdFromMdc();
        }
    }

    @PostMapping("/dlq/{id}/replay")
    @PreAuthorize("hasAuthority('platform:alert:ack') or hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> replay(@PathVariable String id) {
        int updated = repo.replayDlq(id);
        if (updated == 0) {
            return ApiResponse.<Map<String, Object>>badRequest("DLQ 消费失败：记录不存在或已处理").withTraceIdFromMdc();
        }
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("id", id);
        rep.put("status", "replayed");
        return ApiResponse.success(rep).withTraceIdFromMdc();
    }
}
