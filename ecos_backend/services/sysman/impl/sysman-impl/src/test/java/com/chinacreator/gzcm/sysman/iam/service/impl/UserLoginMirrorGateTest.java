package com.chinacreator.gzcm.sysman.iam.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.dao.UserLookupDao;
import com.chinacreator.gzcm.sysman.iam.dao.UserDao;
import com.chinacreator.gzcm.sysman.iam.dao.impl.LoginCredentialDaoImpl;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;

/**
 * 缺陷 D86 门禁：用户管理的写面必须同步登录表 {@code ecos_control.users}。
 *
 * <p>系统有两张用户表 —— 登录只读 {@code users}，用户管理只写 {@code td_user}。
 * 二者无同步时会出现三种"接口 200 但登不进/停不住"的静默缺陷：</p>
 * <ol>
 *   <li>新建账号在登录表不存在 ⇒ 永远 401；</li>
 *   <li>重置口令只改 td_user ⇒ 新口令登不进；</li>
 *   <li>停用/删除账号只改 td_user.status ⇒ 登录表 enabled 仍为 true，账号还能登。</li>
 * </ol>
 *
 * <p>本门对每条写路径各钉一条断言；反向探针（去掉同步调用）会让对应用例转红。</p>
 */
class UserLoginMirrorGateTest {

    private static UserAccount account(String id, String username, String status, String locked) {
        UserAccount u = new UserAccount();
        u.setUserId(id);
        u.setUsername(username);
        u.setRealName(username);
        u.setStatus(status);
        u.setLocked(locked);
        return u;
    }

    private static UserServiceImpl service(UserDao dao, LoginCredentialService mirror) {
        return new UserServiceImpl(dao, mock(SysConfigService.class), mock(UserLookupDao.class), mirror);
    }

    @Test
    @DisplayName("建号后必须写登录凭证（否则新账号永远 401）")
    void createUserProvisionsLoginCredential() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findByUsername("u-new")).thenReturn(null);
        UserAccount created = account("u-new", "u-new", "ACTIVE", "0");
        when(dao.findById("u-new")).thenReturn(created);

        service(dao, mirror).createUser(account("u-new", "u-new", "ACTIVE", "0"),
                "Verify@2026Aa", "admin");

        verify(mirror).provision(eq("u-new"), eq("u-new"), eq("Verify@2026Aa"), any());
    }

    @Test
    @DisplayName("改密后必须同步登录表口令（否则接口 200 但新口令登不进）")
    void resetPasswordSyncsCredential() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findById("u-d01")).thenReturn(account("u-d01", "u-d01", "ACTIVE", "0"));

        service(dao, mirror).resetPassword("u-d01", "Verify@2026Aa", "admin");

        verify(dao).updatePassword(any(UserAccount.class));
        verify(mirror).syncPassword(eq("u-d01"), eq("u-d01"), eq("Verify@2026Aa"));
    }

    @Test
    @DisplayName("停用账号必须同步停登录凭据（否则已停用账号还能登录）")
    void deactivateRevokesCredential() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findById("u-x")).thenReturn(account("u-x", "u-x", "ACTIVE", "0"));

        service(dao, mirror).deactivateUser("u-x", "admin");

        verify(mirror).revoke("u-x", "u-x");
    }

    @Test
    @DisplayName("重新启用必须把登录凭据打开（否则启用后仍登不进）")
    void activateRestoresCredential() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findById("u-x")).thenReturn(account("u-x", "u-x", "INACTIVE", "0"));

        service(dao, mirror).activateUser("u-x", "admin");

        verify(mirror).syncProfile(eq("u-x"), eq("u-x"), any(), eq(true));
    }

    @Test
    @DisplayName("删除账号必须停登录凭据（td_user 只是逻辑删，登录表不知情）")
    void deleteRevokesCredential() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findById("u-x")).thenReturn(account("u-x", "u-x", "ACTIVE", "0"));

        service(dao, mirror).deleteUser("u-x", "admin");

        verify(mirror).revoke("u-x", "u-x");
    }

    @Test
    @DisplayName("锁定/解锁必须同步登录表锁定态（否则 IAM 锁了但登录放行）")
    void lockAndUnlockSyncLockedState() throws Exception {
        UserDao dao = mock(UserDao.class);
        LoginCredentialService mirror = mock(LoginCredentialService.class);
        when(dao.findById("u-x")).thenReturn(account("u-x", "u-x", "ACTIVE", "0"),
                account("u-x", "u-x", "ACTIVE", "1"));
        UserServiceImpl svc = service(dao, mirror);

        svc.lockUser("u-x", "admin");
        verify(mirror).syncLocked("u-x", "u-x", true);

        svc.unlockUser("u-x", "admin");
        verify(mirror).syncLocked("u-x", "u-x", false);
    }

    @Test
    @DisplayName("镜像写入口令一律为 BCrypt 形态（登录表只认 BCrypt）")
    void provisionWritesBcryptHash() {
        LoginCredentialDaoImpl dao = mock(LoginCredentialDaoImpl.class);
        when(dao.findById("u-mirror")).thenReturn(List.of());
        when(dao.findByUsername("u-mirror")).thenReturn(List.of());
        when(dao.findRoleCodes("u-mirror")).thenReturn(List.of("ANALYST"));
        when(dao.findTenantId("u-mirror")).thenReturn("tenant-a");

        new LoginCredentialService(dao).provision("u-mirror", "u-mirror", "Verify@2026Aa", "某人");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(dao).insert(eq("u-mirror"), eq("u-mirror"), hash.capture(), anyString(),
                anyString(), anyBoolean(), eq("tenant-a"));
        assertNotNull(hash.getValue());
        assertTrue(hash.getValue().startsWith("$2"), "登录表校验器只认 BCrypt，实际: " + hash.getValue());
    }

    @Test
    @DisplayName("镜像缺省不抛：登录表查不到该账号时角色同步静默跳过")
    void syncRolesSkipsWhenNotProvisioned() {
        LoginCredentialDaoImpl dao = mock(LoginCredentialDaoImpl.class);
        when(dao.findById("u-orphan")).thenReturn(List.of());

        new LoginCredentialService(dao).syncRolesIfProvisioned("u-orphan");

        verify(dao, org.mockito.Mockito.never())
                .updateRoles(anyString(), anyString());
    }

    @Test
    @DisplayName("登录表无该账号时改密必须补建凭证（存量账号重置密码后要能登录）")
    void syncPasswordProvisionsWhenMissing() {
        LoginCredentialDaoImpl dao = mock(LoginCredentialDaoImpl.class);
        when(dao.findById("u-legacy")).thenReturn(List.of());
        when(dao.findByUsername("u-legacy")).thenReturn(List.of());
        when(dao.findRoleCodes("u-legacy")).thenReturn(List.of("ANALYST"));
        when(dao.findTenantId("u-legacy")).thenReturn("tenant-a");

        new LoginCredentialService(dao).syncPassword("u-legacy", "u-legacy", "Verify@2026Aa");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(dao).insert(eq("u-legacy"), eq("u-legacy"), hash.capture(), anyString(),
                anyString(), anyBoolean(), eq("tenant-a"));
        assertTrue(hash.getValue().startsWith("$2"),
                "补建的凭证必须能被 BCrypt 校验，实际: " + hash.getValue());
        verify(dao, org.mockito.Mockito.never())
                .updatePasswordHash(anyString(), anyString());
    }
}
