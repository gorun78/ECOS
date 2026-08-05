package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/security/audit")
public class SecurityAuditController {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditController.class);

    private final JdbcTemplate jdbcTemplate;

    public SecurityAuditController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        asyncWrite(id, userId, username, action, resource, result, detail, ipAddress);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("status", "accepted");
        resp.put("action", action);
        return ApiResponse.success(resp);
    }

    @Async
    public void asyncWrite(String id, String userId, String username, String action,
                           String resource, String result, String detail, String ipAddress) {
        try {
            jdbcTemplate.update(
                "INSERT INTO ecos_audit_log (id, user_id, username, action, resource, result, detail, ip_address) VALUES (?,?,?,?,?,?,?,?)",
                id, userId, username, action, resource, result, detail, ipAddress
            );
            log.debug("审计日志写入成功: id={}, action={}", id, action);
        } catch (Exception e) {
            log.error("审计日志异步写入失败: id={}, action={}", id, action, e);
        }
    }
}
