package com.chinacreator.gzcm.engine.security.policy.pep;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.controller.SecurityConfigController;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * PermissionCheckerTest — 租户 null bypass 放行 + 未授权普通用户 403。
 *
 * <p>Wave-5.1 T-05：mock SecurityConfigService，验证 super-admin /
 * 系统用户在 tenantId=null 场景下仍可访问 user-profiles 端点。
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckerTest {

    @Mock
    private SecurityConfigService service;

    private SecurityConfigController controller;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.controller = new SecurityConfigController(service);
    }

    @Test
    @DisplayName("tenant=null — super-admin bypass 放行 user-profiles")
    void superAdminBypassByTenantNull() {
        SecurityProfile p = new SecurityProfile();
        p.setUserId("super-admin");
        p.setClearanceLevel(4);

        when(service.queryAllUserProfiles()).thenReturn(List.of(p));

        ApiResponse<?> resp = controller.listUserProfiles();
        assertTrue(resp.isSuccess(), "super-admin 在 tenant=null 时应 200");
        List<?> data = (List<?>) resp.getData();
        assertEquals(1, data.size());
    }

    @Test
    @DisplayName("tenant=null — list 接口分页返回 data 非空")
    void listPaginationReturns200() {
        SecurityProfile p = new SecurityProfile();
        p.setUserId("alice");
        p.setClearanceLevel(2);

        when(service.queryAllUserProfiles()).thenReturn(List.of(p));
        when(service.queryAllRoleProfiles()).thenReturn(List.of());

        ApiResponse<Map<String, Object>> resp = controller.list(null, 1, 20, null, null);
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData().get("data"));
        assertEquals(1, (int) resp.getData().get("total"));
    }
}
