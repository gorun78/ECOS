package com.chinacreator.gzcm.sysman.boot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 认证服务 — 从 AuthController 下沉的数据库访问层。
 * <p>
 * 封装所有 JdbcTemplate 操作（queryForList / queryForObject / update），
 * AuthController 通过此 Service 访问数据库，不再直接持有 JdbcTemplate。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JdbcTemplate jdbcTemplate;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── 登录 ──────────────────────────────────────────────

    /**
     * 查询用户（含安全字段）。
     */
    public List<Map<String, Object>> findUserForLogin(String username) {
        return jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, display_name, roles, "
                        + "failed_attempts, locked_until, password_change_required, "
                        + "last_password_change, password_history "
                        + "FROM users WHERE username = ? AND enabled = true",
                username);
    }

    /**
     * 锁定账户（达到最大失败次数）。
     */
    public void lockAccount(int failedAttempts, int lockoutDurationMinutes, String username) {
        jdbcTemplate.update(
                "UPDATE users SET failed_attempts = ?, locked_until = NOW() + (? * INTERVAL '1 minute') WHERE username = ?",
                failedAttempts, lockoutDurationMinutes, username);
    }

    /**
     * 更新失败登录计数。
     */
    public void updateFailedAttempts(int failedAttempts, String username) {
        jdbcTemplate.update(
                "UPDATE users SET failed_attempts = ? WHERE username = ?",
                failedAttempts, username);
    }

    /**
     * 重置失败计数与锁定状态（按用户ID）。
     */
    public void resetFailedAttempts(String userId) {
        jdbcTemplate.update(
                "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?",
                userId);
    }

    /**
     * 查询用户租户ID。
     */
    public List<Map<String, Object>> findTenantByUsername(String username) {
        return jdbcTemplate.queryForList(
                "SELECT \"TENANT_ID\" FROM TD_USER WHERE \"USERNAME\" = ?", username);
    }

    // ── 获取当前用户信息 ──────────────────────────────────

    /**
     * 按用户ID查询用户信息（用户名、显示名、角色）。
     */
    public List<Map<String, Object>> findUserById(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT username, display_name, roles FROM users WHERE id = ? AND enabled = true",
                userId);
    }

    // ── 修改密码 ──────────────────────────────────────────

    /**
     * 按用户ID查询密码相关字段（用于修改密码场景）。
     */
    public List<Map<String, Object>> findUserForPasswordChange(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, password_history FROM users WHERE id = ? AND enabled = true",
                userId);
    }

    /**
     * 更新密码、清除强制改密标记、更新密码历史。
     */
    public void updatePassword(String newPasswordHash, String updatedHistoryJson, String userId) {
        jdbcTemplate.update(
                "UPDATE users SET password_hash = ?, password_change_required = FALSE, "
                        + "last_password_change = NOW(), password_history = CAST(? AS jsonb) WHERE id = ?",
                newPasswordHash, updatedHistoryJson, userId);
    }
}
