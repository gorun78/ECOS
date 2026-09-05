package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RlsPolicyCrudTest — Wave-5.4 T-19 seed 单测.
 *
 * <p>覆盖 RlsController 的 /policies CRUD 端点（list / get / create / update / delete），
 * mock 掉底层 RowLevelSecurityServiceImpl，避免触碰 PG。
 */
@ExtendWith(MockitoExtension.class)
class RlsPolicyCrudTest {

    @Mock
    private RowLevelSecurityServiceImpl rlsService;

    private RlsController controller;

    @BeforeEach
    void setUp() {
        this.controller = new RlsController(rlsService);
    }

    @Test
    void listPolicies_withTableName_shouldReturnSuccess() {
        when(rlsService.listPolicies("customer")).thenReturn(List.of(Map.of("id", "p1")));

        ApiResponse<List<Map<String, Object>>> resp = controller.listPolicies("customer");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        verify(rlsService).listPolicies("customer");
    }

    @Test
    void getPolicy_found_shouldReturnSuccess() {
        when(rlsService.getPolicy("p1")).thenReturn(Map.of("id", "p1", "name", "x"));

        ApiResponse<?> resp = controller.getPolicy("p1");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
    }

    @Test
    void getPolicy_missing_shouldReturnNotFound() {
        when(rlsService.getPolicy("missing")).thenReturn(null);

        ApiResponse<?> resp = controller.getPolicy("missing");

        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getCode());
    }

    @Test
    void createPolicy_success_shouldReturnSuccess() {
        when(rlsService.createPolicy(anyMap())).thenReturn(Map.of("id", "p2"));

        ApiResponse<?> resp = controller.createPolicy(Map.of("tableName", "customer"));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
    }

    @Test
    void updatePolicy_found_shouldReturnSuccess() {
        when(rlsService.updatePolicy(eq("p1"), anyMap())).thenReturn(Map.of("id", "p1"));

        ApiResponse<?> resp = controller.updatePolicy("p1", Map.of("enabled", true));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
    }

    @Test
    void updatePolicy_missing_shouldReturnNotFound() {
        when(rlsService.updatePolicy(eq("gone"), anyMap())).thenReturn(null);

        ApiResponse<?> resp = controller.updatePolicy("gone", Map.of("enabled", false));

        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getCode());
    }

    @Test
    void deletePolicy_success_shouldReturnSuccess() {
        when(rlsService.deletePolicy("p1")).thenReturn(true);

        ApiResponse<?> resp = controller.deletePolicy("p1");

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
    }

    @Test
    void deletePolicy_notFound_shouldReturnNotFound() {
        when(rlsService.deletePolicy("gone")).thenReturn(false);

        ApiResponse<?> resp = controller.deletePolicy("gone");

        assertEquals(ApiResponse.CODE_NOT_FOUND, resp.getCode());
    }
}
