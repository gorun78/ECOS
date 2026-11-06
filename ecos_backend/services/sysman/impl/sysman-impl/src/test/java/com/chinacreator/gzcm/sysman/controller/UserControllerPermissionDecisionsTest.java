package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.cache.PermissionCacheService;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.chinacreator.gzcm.sysman.iam.service.IUserService;
import com.chinacreator.gzcm.sysman.service.UserQueryService;

/**
 * PEP 读面门（§2.9.8③ 缓存消费点；#147AF/D）。
 *
 * <p>钉两件事：① 未认证 ⇒ 401 分态（禁裸 200/500）；② 已认证 ⇒ 调
 * {@code getUserEffectiveDecisions} 原样回传（该方法是权限缓存的真实读路径；
 * 本测试只验证控制器接线与分态，缓存语义由 {@code PermissionCacheIsolationGateTest} 覆盖）。</p>
 *
 * @author ECOS Sysman #147AF
 */
class UserControllerPermissionDecisionsTest {

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static UserController controller(IPermissionCheckService checker) {
        return new UserController(mock(IUserService.class), mock(UserQueryService.class),
                mock(PermissionCacheService.class), checker);
    }

    @Test
    @DisplayName("未认证：抛 401 承载，不查权限")
    void unauthenticatedIs401() {
        IPermissionCheckService checker = mock(IPermissionCheckService.class);
        com.chinacreator.gzcm.common.exception.UnauthorizedException ex =
                assertThrows(com.chinacreator.gzcm.common.exception.UnauthorizedException.class,
                        () -> controller(checker).myPermissionDecisions());
        assertEquals(401, ex.getHttpStatus());
        assertEquals(401, ex.getErrorCode());
    }

    @Test
    @DisplayName("已认证：按当前用户名取有效决策并原样返回")
    void authenticatedReturnsDecisions() throws Exception {
        IPermissionCheckService checker = mock(IPermissionCheckService.class);
        when(checker.getUserEffectiveDecisions("u-admin")).thenReturn(Map.of("perm-1", "ALLOW:10"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u-admin", null));

        ApiResponse<Map<String, String>> resp = controller(checker).myPermissionDecisions();

        assertTrue(resp.isSuccess());
        assertEquals("ALLOW:10", resp.getData().get("perm-1"));
    }
}
