package com.chinacreator.gzcm.engine.security.abac.service.impl;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.controller.SecurityConfigController;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import org.junit.jupiter.api.BeforeEach;
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
 * AbacTenantNullBypassTest — 租户空值 bypass / 未授权普通用户兜底。
 *
 * <p>Wave-5.1 T-05：在 UserContext.getCurrentTenantId() 为 null（super-admin /
 * 系统用户）场景下，SecurityConfigController 各查询入口仍应 200 透传
 * SecurityConfigService 的结果，作为 permission-checker 的运维旁证。
 */
@ExtendWith(MockitoExtension.class)
class AbacTenantNullBypassTest {

    @Mock
    private SecurityConfigService service;

    private SecurityConfigController controller;

    @BeforeEach
    void setUp() {
        this.controller = new SecurityConfigController(service);
    }

    @Test
    @DisplayName("tenant=null — user-profiles 接口应直接透传 service 结果 (super-admin bypass)")
    void listUserProfilesWhenTenantNull() {
        SecurityProfile p = new SecurityProfile();
        p.setUserId("super-admin");
        p.setClearanceLevel(4);

        when(service.queryAllUserProfiles()).thenReturn(List.of(p));

        ApiResponse<?> resp = controller.listUserProfiles();
        assertTrue(resp.isSuccess(), "tenant=null 时 listUserProfiles 应 200");
        List<?> data = (List<?>) resp.getData();
        assertEquals(1, data.size());
        assertEquals("super-admin", ((SecurityProfile) data.get(0)).getUserId());
    }

    @Test
    @DisplayName("tenant=null — list 分页接口 dataType 应为非空 Map 且 total=1")
    void listPaginationWhenTenantNull() {
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

    @Test
    @DisplayName("service 异常 — listProfile 应返回 internalError 而不是抛出")
    void listUserProfilesPropagatesServiceException() {
        when(service.queryAllUserProfiles()).thenThrow(new RuntimeException("PG down"));

        ApiResponse<?> resp = controller.listUserProfiles();
        assertTrue(!resp.isSuccess(), "service 异常时应转 internalError");
    }
}
