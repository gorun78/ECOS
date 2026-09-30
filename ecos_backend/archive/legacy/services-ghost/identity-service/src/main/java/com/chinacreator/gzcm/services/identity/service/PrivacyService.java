package com.chinacreator.gzcm.services.identity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 隐私数据服务 — 用户数据导出与删除（PMO-E2: 从 PrivacyController 下沉）。
 */
@Service
public class PrivacyService {

    private final JdbcTemplate jdbcTemplate;

    public PrivacyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> exportUserData(String userId) {
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

        return export;
    }

    public void deleteUserData(String userId) {
        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET username = 'DELETED_' || id, email = NULL, mobile = NULL, status = 'DELETED' WHERE id = ?",
            UUID.fromString(userId)
        );
        jdbcTemplate.update("DELETE FROM ecos_identity.td_user_role WHERE user_id = ?", UUID.fromString(userId));
        jdbcTemplate.update("DELETE FROM ecos_identity.td_user_organization WHERE user_id = ?", UUID.fromString(userId));
    }
}
