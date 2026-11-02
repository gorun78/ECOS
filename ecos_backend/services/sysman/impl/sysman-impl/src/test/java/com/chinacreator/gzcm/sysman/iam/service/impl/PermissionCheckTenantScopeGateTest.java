package com.chinacreator.gzcm.sysman.iam.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
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
 * RBAC 决策链的租户维度门（§2.9.8⑥；#147AG）。
 *
 * <p><b>缺陷实况</b>：`td_user_role` 只有 (user, role) 两列、不带租户；旧实现
 * {@code userRoleDao.listByUserId(userId)} 把该用户的**全部**角色平铺合并，
 * 于是「在 A 租户被授予的角色」会在 B 租户视图里同样生效——缓存键加 {@code scope} 只能把
 * 错误结果按租户分槽，不修根因。角色归属由 {@code Role.tenantId} 承载（dev 库实测
 * 14 行角色：9 行全局、5 行 tenant-a），故隔离在合并处按角色归属判定：
 * **本租户角色 ∪ 全局角色**，其余一律不并入（含父角色链上的跨租户父角色）；
 * 无服务端租户上下文时**不查库、直接空集**（fail-closed）。
 *
 * @author ECOS Sysman #147AG
 */
class PermissionCheckTenantScopeGateTest {

    private static final String USER = "u-1";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private static Role role(String id, String tenant, String parent) {
        Role r = new Role();
        r.setRoleId(id);
        r.setTenantId(tenant);
        r.setParentRoleId(parent);
        return r;
    }

    private static UserRole link(String roleId) {
        UserRole ur = new UserRole();
        ur.setUserId(USER);
        ur.setRoleId(roleId);
        return ur;
    }

    private static RolePermission grant(String roleId, String permissionId) {
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rp.setEffect("ALLOW");
        rp.setPriority(10);
        return rp;
    }

    private static Permission permission(String id, String resource, String action) {
        Permission p = new Permission();
        p.setPermissionId(id);
        p.setResource(resource);
        p.setAction(action);
        return p;
    }

    private record Fixture(PermissionCheckServiceImpl service, IUserService userService,
                           IRoleService roleService, PermissionDao permissionDao,
                           RolePermissionDao rolePermissionDao, UserRoleDao userRoleDao,
                           PermissionCacheService cache) {
    }

    /** 两租户各一角色 + 一枚全局角色，权限各一张。 */
    private static Fixture fixture() throws Exception {
        IUserService userService = mock(IUserService.class);
        IRoleService roleService = mock(IRoleService.class);
        PermissionDao permissionDao = mock(PermissionDao.class);
        RolePermissionDao rolePermissionDao = mock(RolePermissionDao.class);
        UserRoleDao userRoleDao = mock(UserRoleDao.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);
        when(cache.getUserPermissionDecisions(anyString())).thenReturn(java.util.Map.of());

        when(userRoleDao.listByUserId(USER))
                .thenReturn(List.of(link("r-a"), link("r-b"), link("r-g")));
        when(roleService.getRole("r-a")).thenReturn(role("r-a", "tenant-a", null));
        when(roleService.getRole("r-b")).thenReturn(role("r-b", "tenant-b", null));
        when(roleService.getRole("r-g")).thenReturn(role("r-g", null, null));

        when(rolePermissionDao.listByRoleId("r-a")).thenReturn(List.of(grant("r-a", "perm-a")));
        when(rolePermissionDao.listByRoleId("r-b")).thenReturn(List.of(grant("r-b", "perm-b")));
        when(rolePermissionDao.listByRoleId("r-g")).thenReturn(List.of(grant("r-g", "perm-g")));
        when(permissionDao.listAll()).thenReturn(List.of(
                permission("perm-a", "res-a", "read"),
                permission("perm-b", "res-b", "read"),
                permission("perm-g", "res-g", "read")));

        PermissionCheckServiceImpl service = new PermissionCheckServiceImpl(
                userService, roleService, permissionDao, rolePermissionDao, userRoleDao, cache);
        return new Fixture(service, userService, roleService, permissionDao,
                rolePermissionDao, userRoleDao, cache);
    }

    @Test
    @DisplayName("反证：tenant-a 上下文内，tenant-b 的角色权限必须不可见（旧平铺实现会放行）")
    void crossTenantRoleIsNotMerged() throws Exception {
        Fixture f = fixture();
        TenantContextHolder.setTenantId("tenant-a");

        assertTrue(f.service().checkPermission(USER, "res-a", "read"), "本租户角色放行");
        assertFalse(f.service().checkPermission(USER, "res-b", "read"),
                "他租户角色放行＝跨租户越权（本门要堵的形态）");
        assertTrue(f.service().checkPermission(USER, "res-g", "read"),
                "全局角色（tenant_id 空）在任一租户可见——否则会锁死 ADMIN 类角色");

        TenantContextHolder.setTenantId("tenant-b");
        assertTrue(f.service().checkPermission(USER, "res-b", "read"));
        assertFalse(f.service().checkPermission(USER, "res-a", "read"), "对称反证");
    }

    @Test
    @DisplayName("无服务端租户上下文＝不查库、空集（fail-closed）")
    void missingTenantContextFailsClosedWithoutQuery() throws Exception {
        Fixture f = fixture();

        assertFalse(f.service().checkPermission(USER, "res-a", "read"));
        assertFalse(f.service().checkPermission(USER, "res-g", "read"),
                "无租户时连全局角色也不评估：宁可拒绝，不做无隔离域授权");
        verify(f.userRoleDao(), never()).listByUserId(anyString());
    }

    @Test
    @DisplayName("跨租户父角色不沿链并入：子角色在本租户、父角色在他租户 ⇒ 父权限不可见")
    void crossTenantParentRoleIsNotMerged() throws Exception {
        IUserService userService = mock(IUserService.class);
        IRoleService roleService = mock(IRoleService.class);
        PermissionDao permissionDao = mock(PermissionDao.class);
        RolePermissionDao rolePermissionDao = mock(RolePermissionDao.class);
        UserRoleDao userRoleDao = mock(UserRoleDao.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);
        when(cache.getUserPermissionDecisions(anyString())).thenReturn(java.util.Map.of());

        when(userRoleDao.listByUserId(USER)).thenReturn(List.of(link("r-child")));
        when(roleService.getRole("r-child")).thenReturn(role("r-child", "tenant-a", "r-parent"));
        when(roleService.getRole("r-parent")).thenReturn(role("r-parent", "tenant-b", null));
        when(rolePermissionDao.listByRoleId("r-child")).thenReturn(List.of(grant("r-child", "perm-child")));
        when(rolePermissionDao.listByRoleId("r-parent")).thenReturn(List.of(grant("r-parent", "perm-parent")));
        when(permissionDao.listAll()).thenReturn(List.of(
                permission("perm-child", "res-child", "read"),
                permission("perm-parent", "res-parent", "read")));

        PermissionCheckServiceImpl service = new PermissionCheckServiceImpl(
                userService, roleService, permissionDao, rolePermissionDao, userRoleDao, cache);
        TenantContextHolder.setTenantId("tenant-a");

        assertTrue(service.checkPermission(USER, "res-child", "read"));
        assertFalse(service.checkPermission(USER, "res-parent", "read"),
                "父角色属他租户时不得沿继承链把权限带进来");
    }

    @Test
    @DisplayName("缺角色行（脏链接）＝不并入，且不因查角色异常而放弃整次判定")
    void missingRoleRowIsSkippedNotGranted() throws Exception {
        IUserService userService = mock(IUserService.class);
        IRoleService roleService = mock(IRoleService.class);
        PermissionDao permissionDao = mock(PermissionDao.class);
        RolePermissionDao rolePermissionDao = mock(RolePermissionDao.class);
        UserRoleDao userRoleDao = mock(UserRoleDao.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);
        when(cache.getUserPermissionDecisions(anyString())).thenReturn(java.util.Map.of());

        when(userRoleDao.listByUserId(USER)).thenReturn(List.of(link("r-dangling"), link("r-ok")));
        when(roleService.getRole("r-dangling")).thenReturn(null);
        when(roleService.getRole("r-ok")).thenReturn(role("r-ok", "tenant-a", null));
        when(rolePermissionDao.listByRoleId("r-dangling")).thenReturn(List.of(grant("r-dangling", "perm-x")));
        when(rolePermissionDao.listByRoleId("r-ok")).thenReturn(List.of(grant("r-ok", "perm-a")));
        when(permissionDao.listAll()).thenReturn(List.of(
                permission("perm-x", "res-x", "read"),
                permission("perm-a", "res-a", "read")));

        PermissionCheckServiceImpl service = new PermissionCheckServiceImpl(
                userService, roleService, permissionDao, rolePermissionDao, userRoleDao, cache);
        TenantContextHolder.setTenantId("tenant-a");

        assertFalse(service.checkPermission(USER, "res-x", "read"),
                "无角色行无法判定归属 ⇒ 不授权");
        assertTrue(service.checkPermission(USER, "res-a", "read"), "同批其它角色不受影响");
    }
}
