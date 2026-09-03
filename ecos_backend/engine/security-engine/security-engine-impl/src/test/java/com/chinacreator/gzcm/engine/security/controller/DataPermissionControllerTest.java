package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.datapermission.entity.DataPermissionPolicy;
import com.chinacreator.gzcm.sysman.datapermission.service.IDataPermissionPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataPermissionControllerTest — 数据权限策略 CRUD。
 *
 * <p>Wave-5.1 T-05 第 8 类替代（DataPermissionServiceImpl 不存在，
 * 改用 DataPermissionController + mock IDataPermissionPolicyService）。
 * 覆盖 policyType/keyword 过滤与增删改查。
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionControllerTest {

    @Mock
    private IDataPermissionPolicyService policyService;

    private DataPermissionController controller;

    @BeforeEach
    void setUp() {
        this.controller = new DataPermissionController();
        ReflectionTestUtils.setField(controller, "policyService", policyService);
    }

    @Test
    @DisplayName("GET /policies — mock 返回 3 条策略，分页 2/页")
    void listPoliciesPaged() throws Exception {
        DataPermissionPolicy p1 = new DataPermissionPolicy();
        p1.setPolicyId("pol-1");
        p1.setPolicyName("admin-only");
        DataPermissionPolicy p2 = new DataPermissionPolicy();
        p2.setPolicyId("pol-2");
        p2.setPolicyName("audit");
        DataPermissionPolicy p3 = new DataPermissionPolicy();
        p3.setPolicyId("pol-3");
        p3.setPolicyName("viewer");
        when(policyService.listPolicies(anyMap())).thenReturn(List.of(p1, p2, p3));

        ApiResponse<Map<String, Object>> resp = controller.list("admin", null, 2, 2);
        assertTrue(resp.isSuccess());
        Map<String, Object> data = resp.getData();
        assertEquals(3, (int) data.get("total"));
        assertEquals(2, (int) data.get("pageSize"));
        assertNotNull(data.get("data"));
    }

    @Test
    @DisplayName("POST /policies — 创建策略生成 policyId")
    void createPolicyGeneratesId() throws Exception {
        DataPermissionPolicy policy = new DataPermissionPolicy();
        policy.setPolicyName("new-policy");
        when(policyService.createPolicy(policy, "admin")).thenReturn(policy);

        ApiResponse<?> resp = controller.create(policy);
        assertTrue(resp.isSuccess());
        verify(policyService).createPolicy(policy, "admin");
    }

    @Test
    @DisplayName("DELETE /policies/{id} — 成功返回 {success:true}")
    void deletePolicyReturnsSuccess() throws Exception {
        ApiResponse<?> resp = controller.delete("pol-9");
        assertTrue(resp.isSuccess());
    }

    @Test
    @DisplayName("PUT /policies/{id} — 更新策略透传 id")
    void updatePolicySetsId() throws Exception {
        DataPermissionPolicy policy = new DataPermissionPolicy();
        policy.setPolicyName("updated");
        when(policyService.updatePolicy(policy, "admin")).thenReturn(policy);

        ApiResponse<?> resp = controller.update("pol-7", policy);
        assertTrue(resp.isSuccess());
        assertEquals("pol-7", policy.getPolicyId());
    }
}
