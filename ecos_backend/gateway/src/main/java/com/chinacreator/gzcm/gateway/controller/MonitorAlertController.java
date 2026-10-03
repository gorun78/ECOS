package com.chinacreator.gzcm.gateway.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.core.alert.service.impl.AlertMonitorAdminService;
import com.chinacreator.gzcm.runtime.core.alert.vo.AlertItem;
import com.chinacreator.gzcm.runtime.core.alert.vo.AlertPage;
import com.chinacreator.gzcm.runtime.core.alert.vo.DltPage;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;

/**
 * 告警中心 / DLQ 运维端点（详细设计-00 §D.5.3，operationId 与 D 章 OpenAPI 逐一照抄）。
 * <p>
 * 与既有 {@link MonitorController}（{@code /api/monitor}，仪表盘统计）同族：
 * 本类承载 {@code /api/v1/monitor/alerts*} 与 {@code /api/v1/monitor/dlq*} 六端点，
 * <b>API 只增不改</b>，既有 {@link MonitorController} 零改动。
 *
 * <pre>
 * GET  /api/v1/monitor/alerts                listAlerts            （severity/status/page/size；status=open|acked|closed）
 * GET  /api/v1/monitor/alerts/{id}           getAlertDetail        （非法 id → 404）
 * POST /api/v1/monitor/alerts/{id}/ack       ackAlert              （body 可选 {operator,note}）
 * POST /api/v1/monitor/alerts/{id}/close     closeAlert
 * GET  /api/v1/monitor/dlq                   listDeadLetterEvents  （status/page/size）
 * POST /api/v1/monitor/dlq/{id}/replay       replayDeadLetterEvent
 * </pre>
 *
 * <p>权限：本组端点走 ClearanceInterceptor PATH_RULES 的 /api/v1/ 默认 L1；不额外加权限注解。
 * ack/close 写审计：复用宿主 sysman 审计通道（{@link IAuditLogService}，可选注入；
 * 缺席时仅 log，不阻断运维动作 — TODO(W12) 通道归口待 01 册统一裁决）。
 *
 * <p>DLQ 重放委托 {@link AlertMonitorAdminService#replayDltEvent(String)}；
 * {@code DltReplayService.NOT_FOUND} 常量字面量裁判 — 避免 gateway 反向依赖 {@code runtime-event}
 * （API 只增不改：AlertMonitorAdminService 已在 runtime-monitor 内统一收口 DLQ 重放路径）。
 */
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorAlertController {

    private static final Logger log = LoggerFactory.getLogger(MonitorAlertController.class);
    /** DltReplayService.NOT_FOUND 字面量（详见 runtime-event DltReplayService） */
    private static final String NOT_FOUND = "NOT_FOUND";

    private final AlertMonitorAdminService alertMonitorAdminService;
    private final ObjectProvider<IAuditLogService> auditLogServiceProvider;

    public MonitorAlertController(AlertMonitorAdminService alertMonitorAdminService,
                                  ObjectProvider<IAuditLogService> auditLogServiceProvider) {
        this.alertMonitorAdminService = alertMonitorAdminService;
        this.auditLogServiceProvider = auditLogServiceProvider;
    }

    // ── 告警 ──────────────────────────────────────────────────────

    /** operationId: listAlerts — page 包络 {items,total}，occurred_at desc；size 上限 200 */
    @GetMapping("/alerts")
    public ApiResponse<AlertPage> listAlerts(
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        if (status != null && !status.isEmpty()
                && !"open".equals(status) && !"acked".equals(status) && !"closed".equals(status)) {
            return ApiResponse.error(400, "ECOS-MON-001", "status must be one of open|acked|closed");
        }
        try {
            return ApiResponse.success(alertMonitorAdminService.pageAlerts(severity, status, page, size));
        } catch (Exception e) {
            log.warn("[MonitorAlert] listAlerts 失败: {}", e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-002", "alert store unavailable: " + e.getMessage());
        }
    }

    /** operationId: getAlertDetail — 非法 id → 404（errorCode ECOS-MON-003） */
    @GetMapping("/alerts/{id}")
    public ApiResponse<AlertItem> getAlertDetail(@PathVariable("id") String id) {
        AlertItem item;
        try {
            item = alertMonitorAdminService.getAlert(id);
        } catch (Exception e) {
            log.warn("[MonitorAlert] getAlertDetail 失败 id={}: {}", id, e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-002", "alert store unavailable: " + e.getMessage());
        }
        if (item == null) {
            return ApiResponse.error(404, "ECOS-MON-003", "alert not found: " + id);
        }
        return ApiResponse.success(item);
    }

    /** operationId: ackAlert — 置 status=acked（ack_by/ack_time）；只 mark 不自动 close（人工口径） */
    @PostMapping("/alerts/{id}/ack")
    public ApiResponse<AlertItem> ackAlert(@PathVariable("id") String id,
                                           @RequestBody(required = false) Map<String, String> body) {
        AlertItem item;
        try {
            item = alertMonitorAdminService.ackAlert(id,
                    body != null ? body.get("operator") : null,
                    body != null ? body.get("note") : null);
        } catch (Exception e) {
            log.warn("[MonitorAlert] ackAlert 失败 id={}: {}", id, e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-002", "alert store unavailable: " + e.getMessage());
        }
        if (item == null) {
            return ApiResponse.error(404, "ECOS-MON-003", "alert not found: " + id);
        }
        logAudit("MONITOR_ALERT_ACK", "openapi-alerts", id);
        return ApiResponse.success(item);
    }

    /** operationId: closeAlert — 置 status=closed（close_time） */
    @PostMapping("/alerts/{id}/close")
    public ApiResponse<AlertItem> closeAlert(@PathVariable("id") String id,
                                             @RequestBody(required = false) Map<String, String> body) {
        AlertItem item;
        try {
            item = alertMonitorAdminService.closeAlert(id, body != null ? body.get("operator") : null);
        } catch (Exception e) {
            log.warn("[MonitorAlert] closeAlert 失败 id={}: {}", id, e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-002", "alert store unavailable: " + e.getMessage());
        }
        if (item == null) {
            return ApiResponse.error(404, "ECOS-MON-003", "alert not found: " + id);
        }
        logAudit("MONITOR_ALERT_CLOSE", "openapi-alerts", id);
        return ApiResponse.success(item);
    }

    // ── DLQ ───────────────────────────────────────────────────────

    /** operationId: listDeadLetterEvents — page 包络 {items,total}，first_seen_at desc */
    @GetMapping("/dlq")
    public ApiResponse<DltPage> listDeadLetterEvents(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        try {
            return ApiResponse.success(alertMonitorAdminService.pageDltEvents(status, page, size));
        } catch (Exception e) {
            log.warn("[MonitorAlert] listDeadLetterEvents 失败: {}", e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-004", "dlq store unavailable: " + e.getMessage());
        }
    }

    /** operationId: replayDeadLetterEvent — 委托 AlertMonitorAdminService.replayDltEvent */
    @PostMapping("/dlq/{id}/replay")
    public ApiResponse<Map<String, String>> replayDeadLetterEvent(@PathVariable("id") String id) {
        String result;
        try {
            result = alertMonitorAdminService.replayDltEvent(id);
        } catch (Exception e) {
            log.warn("[MonitorAlert] replayDeadLetterEvent 失败 id={}: {}", id, e.getMessage(), e);
            return ApiResponse.error(503, "ECOS-MON-004", "dlq store unavailable: " + e.getMessage());
        }
        if (NOT_FOUND.equals(result)) {
            return ApiResponse.error(404, "ECOS-MON-005", "dlq event not found: " + id);
        }
        logAudit("MONITOR_DLQ_REPLAY", "openapi-dlq", id);
        return ApiResponse.success(Map.of("id", id, "status", result));
    }

    // ── 工具 ──────────────────────────────────────────────────────

    private void logAudit(String eventType, String resource, String targetId) {
        IAuditLogService audit = auditLogServiceProvider.getIfAvailable();
        if (audit == null) {
            log.info("[MonitorAlert] AUDIT(channel-miss) {} {} target={}", eventType, resource, targetId);
            return;
        }
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setResource(resource);
        event.setTimestamp(LocalDateTime.now());
        event.setResult("SUCCESS");
        event.setUserId(currentOperator());
        audit.log(event);
    }

    private static String currentOperator() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
                return auth.getName();
            }
        } catch (Throwable ignore) {
            // spring-security 缺席（如单测）时降级系统操作者
        }
        return "monitor-api";
    }

    private static int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}
