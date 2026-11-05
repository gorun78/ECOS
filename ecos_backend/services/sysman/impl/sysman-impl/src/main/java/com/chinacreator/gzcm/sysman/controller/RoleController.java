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

/**
 * 角色 CRUD 与角色-权限分配（{@code /api/v1/system/roles}）。
 *
 * <p><b>#147BZ（DoD DI-2 第五批）</b>：请求体由弱类型 Map 改 record/POJO DTO，响应由
 * {@code toMap} 改 {@link RoleVO}。wire 键面逐字不变：角色 10 键（含 {@code createdTime/
 * updatedTime} 仍为 Date 经同一 ObjectMapper 渲染）、分页 4 键且键序 page/pageSize/total/data。</p>
 *
 * <p><b>保真的两处旧口径</b>：① 建角色别名——{@code roleName} 优先、回退 {@code name}，
 * {@code roleCode} 优先、回退 {@code code}，{@code roleType} 缺省 {@code CUSTOM}、状态固定
 * {@code ACTIVE}；② 改角色只用 {@code name/code}（<b>不</b>读 roleName/roleCode），且按
 * {@code containsKey} 语义判定是否提供——DTO 的 {@code xxxPresent} 标记保证显式 null 仍写空。</p>
 */
@RestController
@RequestMapping("/api/v1/system/roles")
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);
    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<RolePageVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            List<Role> all = roleService.listRoles(keyword, null);
            int total = all.size();
            int from = (page - 1) * pageSize;
            int to = Math.min(from + pageSize, total);
            List<Role> paged = total > 0 && from < total ? all.subList(from, to) : Collections.emptyList();

            return ApiResponse.success(new RolePageVO(page, pageSize, total,
                    paged.stream().map(RoleController::toVO).collect(Collectors.toList())));
        } catch (Exception e) {
            log.error("查询角色列表失败", e);
            return ApiResponse.internalError("查询角色列表失败");
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleVO> get(@PathVariable String id) {
        try {
            Role role = roleService.getRole(id);
            if (role == null) return ApiResponse.notFound("角色不存在");
            return ApiResponse.success(toVO(role));
        } catch (Exception e) {
            log.error("查询角色详情失败", e);
            return ApiResponse.internalError("查询角色详情失败");
        }
    }

    @PostMapping
    @RequirePermission(permission = "system:role:create")
    public ApiResponse<RoleVO> create(@RequestBody RoleCreateRequest body) {
        try {
            Role role = new Role();
            role.setRoleId(UUID.randomUUID().toString().replace("-", ""));
            role.setRoleName(body.getRoleName() != null ? body.getRoleName() : body.getName());
            role.setRoleCode(body.getRoleCode() != null ? body.getRoleCode() : body.getCode());
            role.setDescription(body.getDescription());
            role.setRoleType(body.getRoleType() != null ? body.getRoleType() : "CUSTOM");
            role.setStatus("ACTIVE");
            role.setTenantId(body.getTenantId());

            Role created = roleService.createRole(role, "admin");
            return ApiResponse.success(toVO(created));
        } catch (Exception e) {
            log.error("创建角色失败", e);
            return ApiResponse.internalError("创建角色失败");
        }
    }

    @PutMapping("/{id}")
    @RequirePermission(permission = "system:role:update")
    public ApiResponse<RoleVO> update(@PathVariable String id, @RequestBody RoleUpdateRequest body) {
        try {
            Role existing = roleService.getRole(id);
            if (existing == null) return ApiResponse.notFound("角色不存在");

            if (body.isNamePresent()) existing.setRoleName(body.getName());
            if (body.isCodePresent()) existing.setRoleCode(body.getCode());
            if (body.isDescriptionPresent()) existing.setDescription(body.getDescription());
            if (body.isStatusPresent()) existing.setStatus(body.getStatus());
            if (body.isRoleTypePresent()) existing.setRoleType(body.getRoleType());

            Role updated = roleService.updateRole(existing, "admin");
            return ApiResponse.success(toVO(updated));
        } catch (Exception e) {
            log.error("更新角色失败", e);
            return ApiResponse.internalError("更新角色失败");
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
            return ApiResponse.internalError("删除角色失败");
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
            return ApiResponse.internalError("查询角色权限失败");
        }
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission(permission = "system:role:update")
    public ApiResponse<Void> assignPermissions(@PathVariable String id, @RequestBody PermissionIdsRequest body) {
        try {
            List<String> permIds = body.getPermissionIds();
            if (permIds == null) return ApiResponse.badRequest("permissionIds 不能为空");

            // 先删除该角色的所有现有权限关联，再重新分配（全量替换模式）
            List<Permission> currentPerms = roleService.getRolePermissions(id);
            for (Permission p : currentPerms) {
                roleService.revokePermission(id, p.getPermissionId(), "admin");
            }
            // 分配新权限（空列表 = 清空所有权限）
            for (String permId : permIds) {
                roleService.assignPermission(id, permId, "admin");
            }
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配角色权限失败", e);
            return ApiResponse.internalError("分配角色权限失败");
        }
    }

    static RoleVO toVO(Role r) {
        return new RoleVO(r.getRoleId(), r.getRoleName(), r.getRoleCode(), r.getDescription(),
                r.getRoleType(), r.getStatus(), r.getTenantId(), r.getParentRoleId(),
                r.getCreatedTime(), r.getUpdatedTime());
    }

    // ── 请求 DTO（#147BZ） ───────────────────────────────────

    /** 建角色请求（roleName/code 与 name/code 双别名，与旧 Map 读取口径一致）。 */
    public static final class RoleCreateRequest {
        private String roleName;
        private String name;
        private String roleCode;
        private String code;
        private String description;
        private String roleType;
        private String tenantId;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getRoleType() {
            return roleType;
        }

        public void setRoleType(String roleType) {
            this.roleType = roleType;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    /** 改角色请求（只认 name/code；{@code xxxPresent} 保留旧 containsKey 语义）。 */
    public static final class RoleUpdateRequest {
        private String name;
        private String code;
        private String description;
        private String status;
        private String roleType;
        private boolean namePresent;
        private boolean codePresent;
        private boolean descriptionPresent;
        private boolean statusPresent;
        private boolean roleTypePresent;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
            this.namePresent = true;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
            this.codePresent = true;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
            this.descriptionPresent = true;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
            this.statusPresent = true;
        }

        public String getRoleType() {
            return roleType;
        }

        public void setRoleType(String roleType) {
            this.roleType = roleType;
            this.roleTypePresent = true;
        }

        public boolean isNamePresent() {
            return namePresent;
        }

        public boolean isCodePresent() {
            return codePresent;
        }

        public boolean isDescriptionPresent() {
            return descriptionPresent;
        }

        public boolean isStatusPresent() {
            return statusPresent;
        }

        public boolean isRoleTypePresent() {
            return roleTypePresent;
        }
    }

    /** 角色权限全量替换请求。 */
    public static final class PermissionIdsRequest {
        private List<String> permissionIds;

        public List<String> getPermissionIds() {
            return permissionIds;
        }

        public void setPermissionIds(List<String> permissionIds) {
            this.permissionIds = permissionIds;
        }
    }

    // ── 响应 VO（#147BZ：键集与旧 Map 逐字一致） ─────────────────

    /** 角色（10 键；时间字段仍为 {@link java.util.Date}，由同一 ObjectMapper 渲染）。 */
    public record RoleVO(String roleId, String roleName, String roleCode, String description,
                         String roleType, String status, String tenantId, String parentRoleId,
                         Date createdTime, Date updatedTime) {
    }

    /** 角色分页（键序 page/pageSize/total/data 与旧 Map 一致）。 */
    public record RolePageVO(int page, int pageSize, int total, List<RoleVO> data) {
    }
}
