package com.chinacreator.gzcm.services.identity.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    private final JdbcTemplate jdbcTemplate;

    public PrivacyController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/export")
    public ApiResponse exportUserData(@RequestParam String userId) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("userId", userId);
        export.put("exportDate", new Date());

        Map<String, Object> userProfile = jdbcTemplate.queryForMap(
            "SELECT id, username, email, mobile, status, created_at FROM ecos_identity.td_user WHERE id = ?",
            UUID.fromString(userId)
        );
        export.put("profile", userProfile);

        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
            "SELECT r.code, r.name FROM ecos_identity.td_role r " +
            "JOIN ecos_identity.td_user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            UUID.fromString(userId)
        );
        export.put("roles", roles);

        List<Map<String, Object>> auditLogs = jdbcTemplate.queryForList(
            "SELECT action, resource, created_at FROM ecos_audit.td_audit_log WHERE user_id = ? ORDER BY created_at DESC LIMIT 100",
            UUID.fromString(userId)
        );
        export.put("auditTrail", auditLogs);

        return ApiResponse.success(export);
    }

    @PostMapping("/delete")
    public ApiResponse deleteUserData(@RequestParam String userId) {
        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET username = 'DELETED_' || id, email = NULL, mobile = NULL, status = 'DELETED' WHERE id = ?",
            UUID.fromString(userId)
        );
        jdbcTemplate.update("DELETE FROM ecos_identity.td_user_role WHERE user_id = ?", UUID.fromString(userId));
        jdbcTemplate.update("DELETE FROM ecos_identity.td_user_organization WHERE user_id = ?", UUID.fromString(userId));

        return ApiResponse.success("User data anonymized and associations removed");
    }
}
