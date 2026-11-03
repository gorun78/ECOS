package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;

/**
 * 撤权失效腿门（§2.9.8④⑥；#147AA）。
 *
 * <p>{@code PUT /api/v1/system/users/{id}/roles} 是<b>授权关系的唯一在线写面</b>，
 * 但它在 #147AA 之前<b>从不失效权限缓存</b>——旧缓存又没有 TTL，于是撤权后旧决策可以永久命中。
 * 本门钉住「先提交权威数据、再失效该用户全部视图」的次序，并配一条失败腿：
 * 写库未成功时<b>不得</b>失效（否则等于把失败当生效）。</p>
 *
 * @author ECOS Sysman #147AA
 */
class UserControllerRevocationEvictionTest {

    private static Map<String, Object> body(String... roleIds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roleIds", List.of(roleIds));
        return map;
    }

    @Test
    @DisplayName("授权写库成功后，按删除/插入/失效的次序执行，且失效只针对该用户")
    void evictsUserCacheAfterCommittingRoleChanges() {
        UserQueryService queries = mock(UserQueryService.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);
        UserController controller = new UserController(mock(IUserService.class), queries, cache, mock(com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService.class));

        ApiResponse<Void> response = controller.assignRoles("u-1", body("r-1", "r-2"));

        assertTrue(response.isSuccess(), "成功腿必须仍是成功腿（本批只加失效动作）");
        InOrder order = inOrder(queries, cache);
        order.verify(queries).update(contains("DELETE FROM TD_USER_ROLE"), eq("u-1"));
        order.verify(queries).update(contains("INSERT INTO TD_USER_ROLE"), eq("u-1"), eq("r-1"));
        order.verify(queries).update(contains("INSERT INTO TD_USER_ROLE"), eq("u-1"), eq("r-2"));
        order.verify(cache).evictUser("u-1");
        verify(cache, never()).evictUser("u-2");
        verify(cache, never()).evictAll();
    }

    @Test
    @DisplayName("roleIds 缺失＝请求失败，既不写库也不失效")
    void missingRoleIdsWritesNothingAndEvictsNothing() {
        UserQueryService queries = mock(UserQueryService.class);
        PermissionCacheService cache = mock(PermissionCacheService.class);
        UserController controller = new UserController(mock(IUserService.class), queries, cache, mock(com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService.class));

        ApiResponse<Void> response = controller.assignRoles("u-1", new LinkedHashMap<>());

        assertFalse(response.isSuccess());
        verify(queries, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object>any());
        verify(cache, never()).evictUser("u-1");
    }

    @Test
    @DisplayName("插入授权失败＝不失效缓存（失败不能伪装成撤权已生效）")
    void failedWriteDoesNotEvict() {
        UserQueryService queries = mock(UserQueryService.class);
        when(queries.update(contains("INSERT INTO TD_USER_ROLE"), eq("u-1"), eq("r-1")))
                .thenThrow(new RuntimeException("db down"));
        PermissionCacheService cache = mock(PermissionCacheService.class);
        UserController controller = new UserController(mock(IUserService.class), queries, cache, mock(com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService.class));

        ApiResponse<Void> response = controller.assignRoles("u-1", body("r-1"));

        assertFalse(response.isSuccess(), "写库异常须落失败腿");
        verify(cache, never()).evictUser("u-1");
    }
}
