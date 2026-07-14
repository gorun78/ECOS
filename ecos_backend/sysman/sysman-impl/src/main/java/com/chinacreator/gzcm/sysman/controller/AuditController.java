package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    @Autowired(required = false)
    private IAuditLogService auditLogService;

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
}
