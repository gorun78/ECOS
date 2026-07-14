package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Permission;
import com.chinacreator.gzcm.sysman.iam.entity.Role;
import com.chinacreator.gzcm.sysman.iam.service.IRoleService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/system/roles", "/api/system/roles"})
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);
    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<Role> all = roleService.listRoles(keyword, null);
            int total = all.size();
            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, total);
            List<Role> paged = total > 0 && from < total ? all.subList(from, to) : Collections.emptyList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("total", total);
            result.put("data", paged.stream().map(this::toMap).collect(Collectors.toList()));
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询角色列表失败", e);
            return ApiResponse.internalError("查询角色列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            Role role = roleService.getRole(id);
            if (role == null) return ApiResponse.notFound("角色不存在");
            return ApiResponse.success(toMap(role));
        } catch (Exception e) {
            log.error("查询角色详情失败", e);
            return ApiResponse.internalError("查询角色详情失败: " + e.getMessage());
        }
    }

    @PostMapping
    @RequirePermission(permission = "system:role:create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            Role role = new Role();
            role.setRoleId(UUID.randomUUID().toString().replace("-", ""));
            role.setRoleName((String) body.get("name"));
            role.setRoleCode((String) body.get("code"));
            role.setDescription((String) body.get("description"));
            role.setRoleType((String) body.getOrDefault("roleType", "CUSTOM"));
            role.setStatus("ACTIVE");
            role.setTenantId((String) body.get("tenantId"));

            Role created = roleService.createRole(role, "admin");
            return ApiResponse.success(toMap(created));
        } catch (Exception e) {
            log.error("创建角色失败", e);
            return ApiResponse.internalError("创建角色失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequirePermission(permission = "system:role:update")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            Role existing = roleService.getRole(id);
            if (existing == null) return ApiResponse.notFound("角色不存在");

            if (body.containsKey("name")) existing.setRoleName((String) body.get("name"));
            if (body.containsKey("code")) existing.setRoleCode((String) body.get("code"));
            if (body.containsKey("description")) existing.setDescription((String) body.get("description"));
            if (body.containsKey("status")) existing.setStatus((String) body.get("status"));
            if (body.containsKey("roleType")) existing.setRoleType((String) body.get("roleType"));

            Role updated = roleService.updateRole(existing, "admin");
            return ApiResponse.success(toMap(updated));
        } catch (Exception e) {
            log.error("更新角色失败", e);
            return ApiResponse.internalError("更新角色失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permission = "system:role:delete")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            roleService.deleteRole(id, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除角色失败", e);
            return ApiResponse.internalError("删除角色失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<String>> permissions(@PathVariable String id) {
        try {
            List<Permission> perms = roleService.getRolePermissions(id);
            List<String> permIds = perms.stream().map(Permission::getPermissionId).collect(Collectors.toList());
            return ApiResponse.success(permIds);
        } catch (Exception e) {
            log.error("查询角色权限失败", e);
            return ApiResponse.internalError("查询角色权限失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(permission = "system:role:update")
    public ApiResponse<Void> assignPermissions(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> permIds = (List<String>) body.get("permissionIds");
            if (permIds == null || permIds.isEmpty()) return ApiResponse.badRequest("permissionIds 不能为空");

            for (String permId : permIds) {
                roleService.assignPermission(id, permId, "admin");
            }
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配角色权限失败", e);
            return ApiResponse.internalError("分配角色权限失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toMap(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roleId", r.getRoleId());
        m.put("roleName", r.getRoleName());
        m.put("roleCode", r.getRoleCode());
        m.put("description", r.getDescription());
        m.put("roleType", r.getRoleType());
        m.put("status", r.getStatus());
        m.put("tenantId", r.getTenantId());
        m.put("parentRoleId", r.getParentRoleId());
        m.put("createdTime", r.getCreatedTime());
        m.put("updatedTime", r.getUpdatedTime());
        return m;
    }
}
