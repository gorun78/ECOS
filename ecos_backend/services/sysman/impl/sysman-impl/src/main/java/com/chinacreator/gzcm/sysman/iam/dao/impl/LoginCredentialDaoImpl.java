package com.chinacreator.gzcm.sysman.iam.dao.impl;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 登录凭证表（ecos_control.users）读写面。
 *
 * <p>系统里有两张用户表，职责不同且都要维护：{@code ecos_control.users} 是<b>唯一真实登录表</b>
 * （{@code AuthDao.findUserForLogin} 只读它，口令列为 BCrypt 形态的 {@code password_hash}），
 * {@code ecos_control.td_user} 是 IAM 主数据（组织/角色/资料，口令为 PBKDF2 的 {@code password} 列）。
 * 用户管理的写面过去只碰 {@code td_user}，于是后台建的账号<b>永远登不进去</b>，
 * 重置的口令也到不了登录表。本 DAO 负责把 IAM 侧的账号生命周期镜像过去。</p>
 */
@Repository
public class LoginCredentialDaoImpl extends JdbcTemplateDaoSupport {

    /** 按用户名查登录凭证行。 */
    public List<Map<String, Object>> findByUsername(String username) {
        return jdbcTemplate.queryForList(
            "SELECT id, username FROM ecos_control.users WHERE username = ?", username);
    }

    /** 按 id 查登录凭证行。 */
    public List<Map<String, Object>> findById(String id) {
        return jdbcTemplate.queryForList(
            "SELECT id, username FROM ecos_control.users WHERE id = ?", id);
    }

    /** IAM 侧角色码（登录表 roles 列取值的权威来源）。 */
    public List<String> findRoleCodes(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT r.role_code FROM ecos_control.td_user_role ur "
                + "JOIN ecos_control.td_role r ON r.role_id = ur.role_id "
                + "WHERE ur.user_id = ?", String.class, userId);
    }

    /** IAM 侧租户归属。 */
    public String findTenantId(String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT tenant_id FROM ecos_control.td_user WHERE user_id = ?", userId);
        if (rows.isEmpty()) {
            return null;
        }
        Object tid = rows.get(0).get("tenant_id");
        return tid == null ? null : tid.toString();
    }

    /** 新建登录凭证行。 */
    public void insert(String id, String username, String passwordHash, String displayName,
                       String rolesJson, boolean enabled, String tenantId) {
        jdbcTemplate.update(
            "INSERT INTO ecos_control.users "
                + "(id, username, password_hash, display_name, roles, enabled, tenant_id, "
                + " password_change_required, last_password_change, password_history) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, NOW(), '[]')",
            id, username, passwordHash, displayName, rolesJson, enabled, tenantId);
    }

    /** 覆盖口令并清掉登录侧的失败计数与锁定（否则新口令仍被旧锁定挡住）。 */
    public int updatePasswordHash(String id, String passwordHash) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.users SET password_hash = ?, updated_at = NOW(), "
                + "failed_attempts = 0, locked_until = NULL, "
                + "password_change_required = FALSE, last_password_change = NOW() "
                + "WHERE id = ?",
            passwordHash, id);
    }

    /** 同步启用状态、显示名、角色与租户。 */
    public int updateProfile(String id, String username, String displayName, String rolesJson,
                             boolean enabled, String tenantId) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.users SET username = ?, display_name = ?, roles = ?, "
                + "enabled = ?, tenant_id = ?, updated_at = NOW() WHERE id = ?",
            username, displayName, rolesJson, enabled, tenantId, id);
    }

    /** 停用（不删行：登录表保留历史，enabled=false 即拒绝登录）。 */
    public int disable(String id) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.users SET enabled = FALSE, updated_at = NOW() WHERE id = ?", id);
    }

    /** 只刷角色列（JWT claims 的取值来源）。 */
    public int updateRoles(String id, String rolesJson) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.users SET roles = ?, updated_at = NOW() WHERE id = ?",
            rolesJson, id);
    }

    /** 镜像锁定态（登录表以 locked_until 表达锁定期限，null 即解锁）。 */
    public int updateLockedUntil(String id, Timestamp lockedUntil) {
        return jdbcTemplate.update(
            "UPDATE ecos_control.users SET locked_until = ?, failed_attempts = 0, updated_at = NOW() "
                + "WHERE id = ?",
            lockedUntil, id);
    }
}
