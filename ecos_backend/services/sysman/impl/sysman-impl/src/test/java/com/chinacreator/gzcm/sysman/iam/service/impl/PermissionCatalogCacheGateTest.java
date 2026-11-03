package com.chinacreator.gzcm.sysman.iam.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettings;
import com.chinacreator.gzcm.runtime.core.cache.CacheSettingsSource;
import com.chinacreator.gzcm.runtime.core.cache.RuntimeCacheManager;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.dao.PermissionDao;
import com.chinacreator.gzcm.sysman.iam.dao.RolePermissionDao;
import com.chinacreator.gzcm.sysman.iam.dao.UserRoleDao;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.Role;
import com.chinacreator.gzcm.sysman.iam.entity.RolePermission;
import com.chinacreator.gzcm.sysman.iam.entity.UserRole;
import com.chinacreator.gzcm.sysman.iam.service.IRoleService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;

/**
 * 权限目录缓存门（14 类矩阵「角色·权限·授权关系」目录面；#147AF/D）。
 *
 * <p>钉两件事：① 目录缓存开启时，两次判定只回源一次（消掉执法侧每请求目录查询）；
 * ② 缓存关闭时行为与旧版一致（每次回源）——缓存是加速层，不改变正确性。</p>
 *
 * @author ECOS Sysman #147AF
 */
class PermissionCatalogCacheGateTest {

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

    private static PermissionCatalogCacheGateTest.Fixture fixture(RuntimeCacheManager manager) throws Exception {
        IUserService userService = mock(IUserService.class);
        IRoleService roleService = mock(IRoleService.class);
        PermissionDao permissionDao = mock(PermissionDao.class);
        RolePermissionDao rolePermissionDao = mock(RolePermissionDao.class);
        UserRoleDao userRoleDao = mock(UserRoleDao.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);

        UserRole ur = new UserRole();
        ur.setUserId(USER);
        ur.setRoleId("r-1");
        when(userRoleDao.listByUserId(USER)).thenReturn(List.of(ur));
        Role role = new Role();
        role.setRoleId("r-1");
        when(roleService.getRole("r-1")).thenReturn(role);
        RolePermission rp = new RolePermission();
        rp.setRoleId("r-1");
        rp.setPermissionId("perm-deny");
        rp.setEffect("DENY");
        when(rolePermissionDao.listByRoleId("r-1")).thenReturn(List.of(rp));
        // 决策缓存由本测试直接给定（避免依赖其内部实现）
        when(cache.getUserPermissionDecisions(anyString()))
                .thenReturn(Map.of("perm-deny", Map.of("effect", "DENY", "priority", 5)));

        Permission denied = new Permission();
        denied.setPermissionId("perm-deny");
        denied.setResource("system:user");
        denied.setAction("delete");
        when(permissionDao.listAll()).thenReturn(List.of(denied));

        PermissionCheckServiceImpl service = new PermissionCheckServiceImpl(
                userService, roleService, permissionDao, rolePermissionDao, userRoleDao, cache);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "catalogCacheProvider", provider(manager));
        TenantContextHolder.setTenantId("tenant-a");
        return new PermissionCatalogCacheGateTest.Fixture(service, permissionDao);
    }

    private record Fixture(PermissionCheckServiceImpl service, PermissionDao permissionDao) {
    }

    @Test
    @DisplayName("目录缓存开启：两次判定只回源一次")
    void catalogIsCachedAcrossCalls() throws Exception {
        Fixture f = fixture(manager(new CacheSettings(true, 600L, 60L, false, "dev")));

        assertEquals(java.util.Set.of("system:user:delete"), f.service().getUserDeniedCodes(USER));
        assertEquals(java.util.Set.of("system:user:delete"), f.service().getUserDeniedCodes(USER));

        verify(f.permissionDao(), times(1)).listAll();
    }

    @Test
    @DisplayName("目录缓存关闭：行为与旧版一致（每次回源）")
    void catalogIsNotCachedWhenDisabled() throws Exception {
        Fixture f = fixture(manager(CacheSettings.DISABLED));

        f.service().getUserDeniedCodes(USER);
        f.service().getUserDeniedCodes(USER);

        verify(f.permissionDao(), times(2)).listAll();
    }

    @Test
    @DisplayName("缓存管理器缺席：直读目录（不影响正确性）")
    void catalogFallsBackWithoutManager() throws Exception {
        Fixture f = fixture(null);

        f.service().getUserDeniedCodes(USER);
        f.service().getUserDeniedCodes(USER);

        verify(f.permissionDao(), times(2)).listAll();
    }
}
