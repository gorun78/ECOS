package com.chinacreator.gzcm.services.identity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MFA服务 — TOTP设置/验证/禁用（PMO-E2: 从 MfaController 下沉）。
 */
@Service
public class MfaService {

    private final JdbcTemplate jdbcTemplate;

    public MfaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveTotpSecret(String userId, String base32Secret) {
        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET mfa_secret = ?, mfa_type = 'TOTP', mfa_enabled = false WHERE id = ?",
            base32Secret, UUID.fromString(userId)
        );
    }

    public Map<String, Object> getUserMfaInfo(String userId) {
        return jdbcTemplate.queryForMap(
            "SELECT mfa_secret, mfa_enabled FROM ecos_identity.td_user WHERE id = ?",
            UUID.fromString(userId)
        );
    }

    public void enableMfa(String userId) {
        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET mfa_enabled = true WHERE id = ?",
            UUID.fromString(userId)
        );
    }

    public void disableMfa(String userId) {
        jdbcTemplate.update(
            "UPDATE ecos_identity.td_user SET mfa_secret = NULL, mfa_type = NULL, mfa_enabled = false WHERE id = ?",
            UUID.fromString(userId)
        );
    }
}
