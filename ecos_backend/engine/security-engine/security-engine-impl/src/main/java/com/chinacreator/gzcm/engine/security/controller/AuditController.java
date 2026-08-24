package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/audit", "/api/v1/security/audit"})
public class AuditController {
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    @Autowired(required = false)
    private IAuditLogService auditLogService;

    @Autowired(required = false)
    private AuditHashChainService hashChainService;  // P1-3: 哈希链验证

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            if (auditLogService == null) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("data", Collections.emptyList());
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("pageSize", pageSize);
                return ApiResponse.success(empty);
            }
            IAuditLogService.AuditQueryCondition cond = new IAuditLogService.AuditQueryCondition();
            cond.setUserId(userId);
            if (action != null) cond.setEventType(action);
            if (resourceType != null) cond.setResource(resourceType);
            cond.setPage(page);
            cond.setPageSize(pageSize);
            List<AuditEvent> logs = auditLogService.query(cond);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", logs);
            result.put("total", logs.size());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询审计日志失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        try {
            if (auditLogService == null) return ApiResponse.internalError("审计服务未就绪");
            AuditEvent log = auditLogService.getById(id);
            if (log == null) return ApiResponse.notFound("日志不存在");
            return ApiResponse.success(log);
        } catch (Exception e) {
            log.error("查询审计日志详情失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * 审计统计 — 供前端安全审计页统计卡片使用
     * 返回: {todayCount, failureCount, activeUsers, anomalyIps, totalCount, successCount}
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            if (auditLogService == null) {
                result.put("todayCount", 0);
                result.put("failureCount", 0);
                result.put("activeUsers", 0);
                result.put("anomalyIps", 0);
                result.put("totalCount", 0);
                result.put("successCount", 0);
                return ApiResponse.success(result);
            }
            // Query all logs for stats (page 1, large page size)
            IAuditLogService.AuditQueryCondition cond = new IAuditLogService.AuditQueryCondition();
            cond.setPage(1);
            cond.setPageSize(1000);
            List<AuditEvent> allLogs = auditLogService.query(cond);

            // Today count
            java.time.LocalDate today = java.time.LocalDate.now();
            long todayCount = allLogs.stream()
                .filter(l -> l.getTimestamp() != null && l.getTimestamp().toLocalDate().equals(today))
                .count();

            // Failure count
            long failureCount = allLogs.stream()
                .filter(l -> "FAILURE".equals(l.getResult()) || "FAILED".equals(l.getResult()))
                .count();

            // Active users (distinct userIds)
            long activeUsers = allLogs.stream()
                .map(AuditEvent::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

            // Anomaly IPs (IPs with >3 failure events)
            Map<String, Long> ipFailureCount = allLogs.stream()
                .filter(l -> "FAILURE".equals(l.getResult()) || "FAILED".equals(l.getResult()))
                .filter(l -> l.getIpAddress() != null)
                .collect(java.util.stream.Collectors.groupingBy(AuditEvent::getIpAddress, java.util.stream.Collectors.counting()));
            long anomalyIps = ipFailureCount.values().stream().filter(c -> c > 3).count();

            result.put("todayCount", todayCount);
            result.put("failureCount", failureCount);
            result.put("activeUsers", activeUsers);
            result.put("anomalyIps", anomalyIps);
            result.put("totalCount", (long) allLogs.size());
            result.put("successCount", allLogs.stream().filter(l -> "SUCCESS".equals(l.getResult())).count());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("审计统计失败", e);
            return ApiResponse.internalError("统计失败: " + e.getMessage());
        }
    }

    /**
     * P1-3: 验证审计日志哈希链完整性
     * 检测审计日志是否被篡改
     */
    @GetMapping("/verify-integrity")
    public ApiResponse<Map<String, Object>> verifyIntegrity() {
        try {
            if (hashChainService == null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("valid", false);
                m.put("error", "哈希链服务未就绪");
                return ApiResponse.success(m);
            }
            return ApiResponse.success(hashChainService.verifyHashChain());
        } catch (Exception e) {
            log.error("审计哈希链验证失败", e);
            return ApiResponse.internalError("验证失败: " + e.getMessage());
        }
    }

    /**
     * 写入审计日志端点 — 异步落库，不阻塞调用方。
     * 请求体: {userId, action, resource, result, detail}
     */
    @PostMapping("/log")
    public ApiResponse<Map<String, Object>> writeLog(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        try {
            if (auditLogService == null) {
                return ApiResponse.internalError("审计服务未就绪");
            }

            // 构造审计事件
            AuditEvent event = new AuditEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setTimestamp(LocalDateTime.now());
            event.setUserId(asString(body.get("userId")));
            event.setAction(asString(body.get("action")));
            event.setResource(asString(body.get("resource")));
            event.setResult(asString(body.get("result")));
            event.setEventType(asString(body.get("action")));  // action 复用为 eventType
            event.setIpAddress(request != null ? request.getRemoteAddr() : null);
            event.setUserAgent(request != null ? request.getHeader("User-Agent") : null);

            // detail 放入 details Map
            Object detail = body.get("detail");
            if (detail != null) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("detail", detail);
                // 透传调用方提供的额外字段
                for (Map.Entry<String, Object> e : body.entrySet()) {
                    String k = e.getKey();
                    if (!"userId".equals(k) && !"action".equals(k) && !"resource".equals(k)
                            && !"result".equals(k) && !"detail".equals(k)) {
                        details.put(k, e.getValue());
                    }
                }
                event.setDetails(details);
            }

            // 异步写入（IAuditLogService.log 内部已用 CompletableFuture.runAsync 异步落库）
            auditLogService.log(event);

            return ApiResponse.success(Map.of("status", "accepted"));
        } catch (Exception e) {
            log.error("写入审计日志失败", e);
            return ApiResponse.internalError("写入失败: " + e.getMessage());
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
