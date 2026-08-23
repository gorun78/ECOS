package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.SecurityAuditService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/security/audit")
public class SecurityAuditController {

    private final SecurityAuditService securityAuditService;

    public SecurityAuditController(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * 异步写入审计日志。
     * 请求格式: {userId, username, action, resource, result, detail, ipAddress}
     */
    @PostMapping("/log")
    public ApiResponse<Map<String, Object>> log(@RequestBody Map<String, Object> body) {
        String id = UUID.randomUUID().toString().replace("-", "");
        String userId = (String) body.getOrDefault("userId", null);
        String username = (String) body.getOrDefault("username", null);
        String action = (String) body.get("action");
        String resource = (String) body.getOrDefault("resource", null);
        String result = (String) body.getOrDefault("result", null);
        String detail = (String) body.getOrDefault("detail", null);
        String ipAddress = (String) body.getOrDefault("ipAddress", null);

        if (action == null || action.isBlank()) {
            return ApiResponse.badRequest("action不能为空");
        }

        // Fire-and-forget 异步写入
        securityAuditService.asyncWrite(id, userId, username, action, resource, result, detail, ipAddress);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("status", "accepted");
        resp.put("action", action);
        return ApiResponse.success(resp);
    }
}
