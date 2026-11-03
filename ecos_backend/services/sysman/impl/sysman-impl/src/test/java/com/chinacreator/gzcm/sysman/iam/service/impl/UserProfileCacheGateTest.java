package com.chinacreator.gzcm.sysman.iam.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import com.chinacreator.gzcm.sysman.dao.UserLookupDao;
import com.chinacreator.gzcm.sysman.iam.dao.UserDao;
import com.chinacreator.gzcm.sysman.iam.entity.UserAccount;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;

/**
 * 用户基础信息投影缓存门（14 类矩阵「用户基础信息」；§2.9.8⑥；#147AF/D）。
 *
 * <p>三条：① 投影只含白名单字段——口令/身份证/手机/邮箱/地址/证书序列 <b>不得</b>进入返回值；
 * ② 开缓存时两次读取只回源一次；③ 写路径（删除）后缓存立即失效。</p>
 *
 * @author ECOS Sysman #147AF
 */
class UserProfileCacheGateTest {

    private static final String USER = "u-1";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static ObjectProvider<RuntimeCacheManager> provider(RuntimeCacheManager manager) {
        return new ObjectProvider<>() {
            @Override
            public RuntimeCacheManager getObject(Object... args) {
                return manager;
            }

            @Override
            public RuntimeCacheManager getObject() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfAvailable() {
                return manager;
            }

            @Override
            public RuntimeCacheManager getIfUnique() {
                return manager;
            }
        };
    }

    private static RuntimeCacheManager manager(CacheSettings settings) {
        CacheSettingsSource src = () -> settings;
        return new RuntimeCacheManager(new ObjectProvider<>() {
            @Override
            public CacheSettingsSource getObject(Object... args) {
                return src;
            }

            @Override
            public CacheSettingsSource getObject() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfAvailable() {
                return src;
            }

            @Override
            public CacheSettingsSource getIfUnique() {
                return src;
            }
        });
    }

    private static UserAccount account() {
        UserAccount u = new UserAccount();
        u.setUserId(USER);
        u.setUsername("admin");
        u.setRealName("管理员");
        u.setOrgId("org-1");
        u.setStatus("ACTIVE");
        u.setLocked("0");
        u.setUserType("INTERNAL");
        // 以下均为敏感列：投影必须丢弃
        u.setPassword("$2a$10$hash");
        u.setIdCard("110101199001011234");
        u.setMobileTel1("13800000000");
        u.setEmail("a@b.c");
        u.setAddress("某地");
        u.setCertSn("CERT-SN-XYZ");
        return u;
    }

    private static UserServiceImpl service(UserDao dao, RuntimeCacheManager manager) {
        UserServiceImpl svc = new UserServiceImpl(dao, mock(SysConfigService.class), mock(UserLookupDao.class));
        ReflectionTestUtils.setField(svc, "profileCacheProvider", provider(manager));
        return svc;
    }

    @Test
    @DisplayName("投影白名单：敏感字段一律不出现")
    void projectionDropsSensitiveFields() throws Exception {
        UserDao dao = mock(UserDao.class);
        when(dao.findById(USER)).thenReturn(account());
        UserServiceImpl svc = service(dao, manager(CacheSettings.DISABLED));

        IUserService.UserBasicProfile p = svc.getUserBasicProfile(USER);

        assertEquals(USER, p.userId());
        assertEquals("admin", p.username());
        assertEquals("org-1", p.orgId());
        String json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(p);
        for (String secret : new String[]{"$2a$10$hash", "110101", "13800000000", "a@b.c", "某地", "CERT-SN-XYZ"}) {
            assertTrue(!json.contains(secret), "投影不得包含敏感值: " + secret + " in " + json);
        }
    }

    @Test
    @DisplayName("开缓存：两次读取只回源一次；内容一致")
    void cachesProfile() throws Exception {
        UserDao dao = mock(UserDao.class);
        when(dao.findById(USER)).thenReturn(account());
        UserServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        svc.getUserBasicProfile(USER);
        IUserService.UserBasicProfile second = svc.getUserBasicProfile(USER);

        assertEquals("管理员", second.realName());
        verify(dao, times(1)).findById(USER);
    }

    @Test
    @DisplayName("写路径（删除用户）后缓存立即失效")
    void deleteInvalidatesProfile() throws Exception {
        UserDao dao = mock(UserDao.class);
        when(dao.findById(USER)).thenReturn(account());
        UserServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        svc.getUserBasicProfile(USER);
        svc.deleteUser(USER, "tester");
        svc.getUserBasicProfile(USER);

        // 1 次首次读取 + 1 次删除前存在性校验 + 1 次失效后回源 = 3
        verify(dao, times(3)).findById(USER);
        verify(dao).delete(USER);
    }

    @Test
    @DisplayName("用户不存在：返回 null（调用方自报 404），不写缓存")
    void missingUserReturnsNull() throws Exception {
        UserDao dao = mock(UserDao.class);
        when(dao.findById("nope")).thenReturn(null);
        UserServiceImpl svc = service(dao, manager(new CacheSettings(true, 600L, 60L, false, "dev")));
        TenantContextHolder.setTenantId("tenant-a");

        assertNull(svc.getUserBasicProfile("nope"));
    }
}
