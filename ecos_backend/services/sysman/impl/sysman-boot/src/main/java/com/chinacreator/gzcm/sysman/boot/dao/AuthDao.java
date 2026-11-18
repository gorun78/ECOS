package com.chinacreator.gzcm.sysman.boot.dao;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 认证数据访问 DAO — 承接 sysman-boot {@code AuthService} 中的 JdbcTemplate 操作。
 * <p>铁律 §2.5：Service 不直用 JdbcTemplate，统一经 DAO。
 *
 * <p><b>登录权威表 = {@code td_user}（V347，2026-11-17）</b>。本 DAO 原先读 {@code users}，
 * 那是一张只有 2 行、靠 {@code LoginCredentialService} 从 td_user 单向镜像出来的平行表：
 * 用户管理改 td_user、登录读 users，锁定态已经分叉（u-d01 在 users 侧 locked_until 有值、
 * td_user 侧 locked=0）。现统一到 td_user 单表，镜像层随之拆除。
 *
 * <p><b>返回 Map 的键面刻意保持旧形态</b>（{@code id}/{@code password_hash}/
 * {@code display_name}/{@code password_change_required}/{@code password_history}），
 * 用列别名对齐 td_user 的真列（{@code user_id}/{@code password}/{@code real_name}/
 * {@code is_password_change_required}/{@code password_history_json}），
 * 这样 {@code AuthController} 的取值面与 HTTP 契约都不必跟着改。
 *
 * <p><b>两种锁语义并存，登录侧都认</b>：管理锁 {@code locked='1'}（无到期，需管理员解锁）
 * 与爆破锁 {@code locked_until}（到期自动放行）。爆破锁刻意不写 {@code locked='1'}，
 * 否则临时锁定会被管理锁判据变成永久锁定。
 *
 * <p>表名不限定 schema，与本模块既有语句（{@code findTenantByUsername}）及
 * {@code SecurityProfileQueryDao.hasRoleCode} 同口径，靠 JDBC {@code currentSchema}
 * 首序 {@code ecos_control} 解析；td_user/td_role/td_user_role 三表全库仅 ecos_control 一份，
 * 不存在同名族歧义。
 */
@Repository
public class AuthDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 登录可用的账号状态：INACTIVE/DELETED 一律拒登（与 AuthServiceImpl 同口径）。 */
    private static final String LOGINABLE = " AND status = 'ACTIVE'";

    /** 登录安全字段投影（别名对齐旧 users 键面）。 */
    private static final String LOGIN_COLUMNS =
            "user_id AS id, username, password AS password_hash, real_name AS display_name, "
                    + "status, locked, failed_attempts, locked_until, "
                    + "is_password_change_required AS password_change_required, "
                    + "last_password_change, password_history_json AS password_history ";

    // ── 登录 ──────────────────────────────────────────────

    /** 查询用户（含安全字段）。 */
    public List<Map<String, Object>> findUserForLogin(String username) {
        return jdbcTemplate.queryForList(
                "SELECT " + LOGIN_COLUMNS + "FROM td_user WHERE username = ?" + LOGINABLE,
                username);
    }

    /**
     * 锁定账户（达到最大失败次数）—— 只写爆破锁到期时刻，不动管理锁标志。
     */
    public void lockAccount(int failedAttempts, int lockoutDurationMinutes, String username) {
        jdbcTemplate.update(
                "UPDATE td_user SET failed_attempts = ?, "
                        + "locked_until = NOW() + (? * INTERVAL '1 minute') WHERE username = ?",
                failedAttempts, lockoutDurationMinutes, username);
    }

    /** 更新失败登录计数。 */
    public void updateFailedAttempts(int failedAttempts, String username) {
        jdbcTemplate.update(
                "UPDATE td_user SET failed_attempts = ? WHERE username = ?",
                failedAttempts, username);
    }

    /**
     * 重置失败计数与爆破锁（按用户ID）。
     * <p>不清 {@code locked}/{@code lock_time}：那是管理员施加的管理锁，
     * 只有 {@code UserServiceImpl.unlockUser} 有权解除；本方法只在口令比对通过后到达，
     * 此时管理锁必然未生效（生效则登录已被拒）。
     */
    public void resetFailedAttempts(String userId) {
        jdbcTemplate.update(
                "UPDATE td_user SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?",
                userId);
    }

    /**
     * 查询用户租户ID。
     *
     * <p>列名必须按 DDL 真实形态（不加引号的小写）书写：PostgreSQL 的双引号标识符<b>大小写敏感</b>，
     * 旧写法 {@code "TENANT_ID"} 在真实库上必报 {@code column "TENANT_ID" does not exist}
     * （DDL 单源建的是小写列）。该异常被登录端 catch 成"无租户"，于是所有用户都被兜到默认租户，
     * 租户隔离形同虚设。</p>
     */
    public List<Map<String, Object>> findTenantByUsername(String username) {
        return jdbcTemplate.queryForList(
                "SELECT tenant_id FROM td_user WHERE username = ?", username);
    }

    /**
     * 取用户的平台角色码集（真实隶属链 td_user_role → td_role）。
     *
     * <p><b>排序不是美观问题，是授权判据</b>：{@code opa-policies/rbac.rego} 的
     * {@code input.role} 取角色集<b>首角色</b>，判据为 {@code =="admin"} / {@code =="ROLE_SUPER_ADMIN"}；
     * {@code PermissionAspect} 与 {@code GitRepoOrgGuard} 用大小写敏感的
     * {@code "ROLE_SUPER_ADMIN".equals(a) || "admin".equals(a)}。td_role 里系统管理员的
     * role_code 是 {@code ADMIN}，按字典序会排在 {@code ROLE_SUPER_ADMIN} 前面而让上述判据全部落空，
     * 故超管码必须显式置首。
     *
     * <p>不再读 {@code users.roles} 那个 JSON 字符串列：角色单源是隶属表，
     * 与 {@code SecurityProfileQueryDao.hasRoleCode} 的 JOIN 形态一致。
     */
    public List<String> findRoleCodes(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT r.role_code FROM td_user_role ur "
                        + "JOIN td_role r ON ur.role_id = r.role_id "
                        + "WHERE ur.user_id = ? AND r.role_code IS NOT NULL "
                        + "ORDER BY CASE WHEN r.role_code = 'ROLE_SUPER_ADMIN' THEN 0 ELSE 1 END, r.role_code",
                String.class, userId);
    }

    // ── 获取当前用户信息 ──────────────────────────────────

    /** 按用户ID查询用户信息（用户名、显示名）。角色另经 {@link #findRoleCodes} 取。 */
    public List<Map<String, Object>> findUserById(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT username, real_name AS display_name FROM td_user WHERE user_id = ?" + LOGINABLE,
                userId);
    }

    // ── 修改密码 ──────────────────────────────────────────

    /** 按用户ID查询密码相关字段（用于修改密码场景）。 */
    public List<Map<String, Object>> findUserForPasswordChange(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id AS id, username, password AS password_hash, "
                        + "password_history_json AS password_history "
                        + "FROM td_user WHERE user_id = ?" + LOGINABLE,
                userId);
    }

    /**
     * 更新密码、清除强制改密标记、更新密码历史。
     * <p>历史列是 TEXT 存 JSON 数组（DR04 的 {@code _json} 后缀），故直接绑字符串，
     * 不用 {@code CAST(? AS jsonb)}（MC03 禁 PG 专有裸用，且多库不可移植）。
     */
    public void updatePassword(String newPasswordHash, String updatedHistoryJson, String userId) {
        jdbcTemplate.update(
                "UPDATE td_user SET password = ?, is_password_change_required = FALSE, "
                        + "last_password_change = NOW(), password_history_json = ? WHERE user_id = ?",
                newPasswordHash, updatedHistoryJson, userId);
    }

    // ── R1.1 工作站登录校验 ──────────────────────────────────────────

    /** 查询用户绑定的固定工作站 IP（td_user_security_profile.linked_workstation）。 */
    public List<Map<String, Object>> queryLinkedWorkstation(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT linked_workstation FROM td_user_security_profile " +
                "WHERE scope_type = 'user' AND user_id = ? LIMIT 1",
                userId);
    }

    /** R1.1: 写入工作站不匹配审计日志到 ecos_audit_log，返回自增 id。 */
    public Long insertWorkstationMismatchAudit(String username, String operation, String entityType,
                                               String entityId, String changesJson, String ipAddress,
                                               String category) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO ecos_audit_log (username, operation, entity_type, entity_id, changes, ip_address, created_at, category) " +
                "VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, NOW(), ?) RETURNING id",
                Long.class,
                username,
                operation,
                entityType,
                entityId,
                changesJson,
                ipAddress,
                category);
    }
}