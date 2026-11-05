package com.chinacreator.gzcm.sysman.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.Role;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionService;
import com.chinacreator.gzcm.sysman.iam.service.IRoleService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 角色/权限面 wire 键面与更新语义保真门（#147BZ · DoD DI-2 第五批）。
 *
 * <p><b>钉的是什么</b>：
 * <ul>
 *   <li>角色 10 键 + 分页 4 键（键序 page/pageSize/total/data）、权限 5 键，集合相等；</li>
 *   <li>建角色别名与默认值：{@code roleName}/{@code name}、{@code roleCode}/{@code code}、
 *       {@code roleType} 缺省 CUSTOM、状态固定 ACTIVE；</li>
 *   <li><b>更新语义逐字保旧</b>：旧实现按 {@code containsKey} 判定，显式 {@code null} 会写空；
 *       DTO 的 {@code xxxPresent} 必须保持这一点（禁退化成 null 即不更新）；</li>
 *   <li>{@code permissionIds} 缺失仍 400，空数组＝清空全部。</li>
 * </ul></p>
 *
 * <p><b>变异自证</b>：把 {@code RoleVO#roleName} 改名 ⇒ 键集断言红；把
 * {@code RoleUpdateRequest#setName} 去掉 present 标记 ⇒ 「显式 null 写空」用例红。</p>
 */
class RolePermissionWireShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> ROLE_KEYS = List.of("roleId", "roleName", "roleCode",
            "description", "roleType", "status", "tenantId", "parentRoleId", "createdTime",
            "updatedTime");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    private static List<String> keysOf(Object value) {
        return new ArrayList<>(asMap(value).keySet());
    }

    private static Role role() {
        Role r = new Role();
        r.setRoleId("r-1");
        r.setRoleName("管理员");
        r.setRoleCode("ADMIN");
        r.setDescription("d");
        r.setRoleType("BUILTIN");
        r.setStatus("ACTIVE");
        r.setTenantId("t-1");
        r.setParentRoleId(null);
        r.setCreatedTime(new Date(1700000000000L));
        r.setUpdatedTime(new Date(1700000001000L));
        return r;
    }

    private static Permission permission() {
        Permission p = new Permission();
        p.setPermissionId("p-1");
        p.setResource("config");
        p.setAction("READ");
        p.setConditionExpr("x>1");
        p.setDescription("desc");
        return p;
    }

    @Test
    @DisplayName("角色列表键面：分页 4 键（键序）＋角色 10 键（集合相等）")
    void roleListKeepsKeysAndOrder() throws Exception {
        IRoleService service = mock(IRoleService.class);
        when(service.listRoles(any(), any())).thenReturn(List.of(role()));
        RoleController controller = new RoleController(service);

        Map<String, Object> page = asMap(controller.list(null, 1, 20).getData());
        assertEquals(List.of("page", "pageSize", "total", "data"), new ArrayList<>(page.keySet()));
        assertEquals(1, page.get("total"));
        assertEquals(ROLE_KEYS, keysOf(((List<?>) page.get("data")).get(0)));
    }

    @Test
    @DisplayName("建角色别名与默认：roleName→name 回退、roleType 缺省 CUSTOM、状态 ACTIVE")
    void createRoleAliasesAndDefaults() throws Exception {
        IRoleService service = mock(IRoleService.class);
        Role created = role();
        created.setRoleName("别名");
        created.setRoleCode("ALIAS");
        created.setRoleType("CUSTOM");
        when(service.createRole(any(), anyString())).thenReturn(created);
        RoleController controller = new RoleController(service);

        RoleController.RoleCreateRequest request = new RoleController.RoleCreateRequest();
        request.setName("别名");
        request.setCode("ALIAS");

        assertEquals(ROLE_KEYS, keysOf(controller.create(request).getData()));
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(service).createRole(captor.capture(), anyString());
        assertEquals("别名", captor.getValue().getRoleName());
        assertEquals("ALIAS", captor.getValue().getRoleCode());
        assertEquals("CUSTOM", captor.getValue().getRoleType());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertTrue(captor.getValue().getRoleId() != null && !captor.getValue().getRoleId().isBlank());
    }

    @Test
    @DisplayName("改角色：显式 null 仍写空（containsKey 语义），未提供字段不动")
    void updateRolePreservesPresentSemantics() throws Exception {
        IRoleService service = mock(IRoleService.class);
        when(service.getRole(anyString())).thenAnswer(invocation -> role());
        when(service.updateRole(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        RoleController controller = new RoleController(service);

        RoleController.RoleUpdateRequest onlyStatus = MAPPER.readValue(
                "{\"status\":\"INACTIVE\"}", RoleController.RoleUpdateRequest.class);
        controller.update("r-1", onlyStatus);

        RoleController.RoleUpdateRequest explicitNull = MAPPER.readValue(
                "{\"name\":null}", RoleController.RoleUpdateRequest.class);
        controller.update("r-1", explicitNull);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(service, org.mockito.Mockito.atLeast(2)).updateRole(captor.capture(), anyString());
        List<Role> updates = captor.getAllValues();
        assertEquals("INACTIVE", updates.get(0).getStatus());
        assertEquals("管理员", updates.get(0).getRoleName(), "只给 status 时不得动 roleName");
        assertNull(updates.get(1).getRoleName(), "显式 name=null 必须写空（旧 containsKey 语义）");
    }

    @Test
    @DisplayName("角色权限分配：缺 permissionIds ⇒ 400；空数组 ⇒ 清空且不再分配")
    void assignPermissionsValidation() throws Exception {
        IRoleService service = mock(IRoleService.class);
        when(service.getRolePermissions("r-1")).thenReturn(List.of(permission()));
        RoleController controller = new RoleController(service);

        RoleController.PermissionIdsRequest missing = new RoleController.PermissionIdsRequest();
        assertEquals(400, controller.assignPermissions("r-1", missing).getCode());

        RoleController.PermissionIdsRequest empty = new RoleController.PermissionIdsRequest();
        empty.setPermissionIds(List.of());
        assertEquals(ApiResponse.CODE_SUCCESS, controller.assignPermissions("r-1", empty).getCode());
        verify(service).revokePermission("r-1", "p-1", "admin");
        verify(service, never()).assignPermission(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("权限面：列表/详情 5 键（集合相等）；显式 null 仍写空")
    void permissionKeysAndNullSemantics() throws Exception {
        IPermissionService service = mock(IPermissionService.class);
        when(service.listPermissions()).thenReturn(List.of(permission()));
        when(service.getPermission("p-1")).thenReturn(permission());
        when(service.updatePermission(any(), anyString())).thenReturn(permission());
        PermissionController controller = new PermissionController(service);

        assertEquals(List.of("permissionId", "resource", "action", "conditionExpr", "description"),
                keysOf(controller.list().getData().get(0)));
        assertEquals(List.of("permissionId", "resource", "action", "conditionExpr", "description"),
                keysOf(controller.get("p-1").getData()));

        PermissionController.PermissionRequest request = MAPPER.readValue(
                "{\"description\":null}", PermissionController.PermissionRequest.class);
        controller.update("p-1", request);

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(service).updatePermission(captor.capture(), anyString());
        assertNull(captor.getValue().getDescription(), "显式 description=null 必须写空");
        assertEquals("config", captor.getValue().getResource(), "未提供 resource 不得被清空");
    }
}
