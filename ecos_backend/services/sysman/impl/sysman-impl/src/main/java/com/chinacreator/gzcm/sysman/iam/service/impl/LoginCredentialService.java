package com.chinacreator.gzcm.sysman.iam.service.impl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.sysman.iam.dao.impl.LoginCredentialDaoImpl;

/**
 * 把 IAM 账号生命周期镜像到登录表 {@code ecos_control.users}。
 *
 * <p>背景（缺陷 D86）：系统有两张用户表 —— {@code ecos_control.users} 是唯一真实登录表
 * （{@code AuthController} 只读它、只认 BCrypt 形态的 {@code password_hash}），
 * {@code ecos_control.td_user} 是 IAM 主数据表（用户管理 CRUD 与角色/组织关系的属主）。
 * 用户管理的写面过去只动 {@code td_user}，于是：</p>
 * <ul>
 *   <li>后台新建的账号在登录表里<b>不存在</b> ⇒ 永远 401「用户名或密码错误」；</li>
 *   <li>重置口令只改 {@code td_user.password}（PBKDF2），登录表不知情 ⇒ 新口令登不进；</li>
 *   <li>停用/删除账号只改 {@code td_user.status}，登录表 {@code enabled} 仍为 true ⇒ 已停用账号还能登录。</li>
 * </ul>
 *
 * <p>口令形态在此转换：IAM 侧是 PBKDF2（{@code pbkdf2$iter$salt$hash}），
 * 登录表校验器只认 BCrypt，故统一落 BCrypt。角色与租户按 userId 从 IAM 侧现查，
 * 保证登录表拿到的是权威值而不是调用方手里的快照。</p>
 */
@Service
public class LoginCredentialService {

    private static final Logger log = LoggerFactory.getLogger(LoginCredentialService.class);

    /** 登录表的口令校验器是 BCrypt，故镜像值必须是 BCrypt 形态。 */
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    private final LoginCredentialDaoImpl loginCredentialDao;

    public LoginCredentialService(LoginCredentialDaoImpl loginCredentialDao) {
        this.loginCredentialDao = loginCredentialDao;
    }

    /**
     * 新建账号时建立登录凭证。
     *
     * @param rawPassword 明文口令（调用方已过策略校验）
     */
    public void provision(String userId, String username, String rawPassword, String displayName) {
        String hash = encoder.encode(rawPassword);
        String rolesJson = toRolesJson(roleCodesOf(userId));
        String tenantId = tenantOf(userId);
        String existingId = resolveCredentialId(userId, username);
        if (existingId == null) {
            loginCredentialDao.insert(userId, username, hash, displayName, rolesJson, true, tenantId);
            log.info("登录凭证已建立: userId={}, username={}", userId, username);
            return;
        }
        loginCredentialDao.updatePasswordHash(existingId, hash);
        loginCredentialDao.updateProfile(existingId, username, displayName, rolesJson, true, tenantId);
        log.info("登录凭证已更新: userId={}, username={}", existingId, username);
    }

    /**
     * 改密后同步登录表口令。
     *
     * <p>登录表没有该账号时<b>补建凭证</b>而不是跳过：IAM 主数据里的存量账号
     * （迁移/seed 带进来的）从未进入登录表，跳过就等于"管理员重置了密码但这个人还是登不进"。</p>
     */
    public void syncPassword(String userId, String username, String rawPassword) {
        String hash = encoder.encode(rawPassword);
        String targetId = resolveCredentialId(userId, username);
        if (targetId == null) {
            String rolesJson = toRolesJson(roleCodesOf(userId));
            loginCredentialDao.insert(userId, username, hash, username, rolesJson, true, tenantOf(userId));
            log.info("登录表无该账号，已按改密补建凭证: userId={}, username={}", userId, username);
            return;
        }
        loginCredentialDao.updatePasswordHash(targetId, hash);
        log.info("登录表口令已同步: userId={}", targetId);
    }

    /** 资料/角色变更后同步登录表（不改口令）。 */
    public void syncProfile(String userId, String username, String displayName, boolean enabled) {
        String targetId = resolveCredentialId(userId, username);
        if (targetId == null) {
            return;
        }
        loginCredentialDao.updateProfile(targetId, username, displayName,
                toRolesJson(roleCodesOf(userId)), enabled, tenantOf(userId));
    }

    /**
     * 角色变更后只刷 roles 列（其它列保持）。登录表尚没有该账号时静默跳过——
     * 角色分配早于账号接入登录面的历史数据不应因本调用造出半条凭证。
     */
    public void syncRolesIfProvisioned(String userId) {
        String targetId = resolveCredentialId(userId, null);
        if (targetId == null) {
            return;
        }
        loginCredentialDao.updateRoles(targetId, toRolesJson(roleCodesOf(userId)));
        log.info("登录表角色已同步: userId={}", targetId);
    }

    /** 停用/删除账号后同步登录表（登录表不删行，置 enabled=false 即拒绝登录）。 */
    public void revoke(String userId, String username) {
        String targetId = resolveCredentialId(userId, username);
        if (targetId == null) {
            return;
        }
        loginCredentialDao.disable(targetId);
        log.info("登录凭据已停用: userId={}", targetId);
    }

    /**
     * 同步锁定态。登录表的锁定以 {@code locked_until} 表达；IAM 侧锁定时给一个远期时间，
     * 解锁时清空。不同步的话 IAM 锁了但登录仍放行。
     */
    public void syncLocked(String userId, String username, boolean locked) {
        String targetId = resolveCredentialId(userId, username);
        if (targetId == null) {
            return;
        }
        java.sql.Timestamp lockedUntil = locked
                ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusYears(100))
                : null;
        loginCredentialDao.updateLockedUntil(targetId, lockedUntil);
        log.info("登录表锁定态已同步: userId={}, locked={}", targetId, locked);
    }

    private String resolveCredentialId(String userId, String username) {
        List<Map<String, Object>> byId = loginCredentialDao.findById(userId);
        if (!byId.isEmpty()) {
            return String.valueOf(byId.get(0).get("id"));
        }
        if (username != null && !username.isBlank()) {
            List<Map<String, Object>> byName = loginCredentialDao.findByUsername(username);
            if (!byName.isEmpty()) {
                return String.valueOf(byName.get(0).get("id"));
            }
        }
        return null;
    }

    private List<String> roleCodesOf(String userId) {
        try {
            return loginCredentialDao.findRoleCodes(userId);
        } catch (Exception e) {
            // 角色解析失败不该让账号生命周期失败：无角色仍可登录，只是没有授权
            log.warn("读取 IAM 角色失败，登录表按空角色写入: userId={}, err={}", userId, e.getMessage());
            return List.of();
        }
    }

    private String tenantOf(String userId) {
        try {
            return loginCredentialDao.findTenantId(userId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * IAM 角色码 → 登录表 roles 列（JSON 数组字符串）。
     * 该值原样进 JWT claims 并被下游当权限字面量比对，故保留 {@code ROLE_} 形态。
     */
    private String toRolesJson(List<String> roleCodes) {
        StringBuilder sb = new StringBuilder("[");
        for (String code : roleCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append('"').append(code.replace("\"", "")).append('"');
        }
        return sb.append(']').toString();
    }
}
