package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.entity.Organization;
import com.chinacreator.gzcm.sysman.iam.service.IOrganizationService;
import com.chinacreator.gzcm.common.annotation.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/system/organizations", "/api/system/organizations"})
public class OrganizationController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationController.class);
    private final IOrganizationService orgService;

    public OrganizationController(IOrganizationService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/all")
    public ApiResponse<List<Map<String, Object>>> all() {
        try {
            List<Organization> orgs = orgService.listOrganizations(null, null);
            return ApiResponse.success(orgs.stream().map(this::toFlatMap).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询机构列表失败", e);
            return ApiResponse.internalError("查询机构列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/tree")
    public ApiResponse<Map<String, Object>> tree(@RequestParam(required = false) String rootOrgId) {
        try {
            Organization root = orgService.getOrganizationTree(rootOrgId);
            return ApiResponse.success(toTreeMap(root));
        } catch (Exception e) {
            log.error("查询机构树失败", e);
            return ApiResponse.internalError("查询机构树失败: " + e.getMessage());
        }
    }

    @GetMapping("/children")
    public ApiResponse<List<Map<String, Object>>> children(@RequestParam(required = false) String parentId) {
        try {
            List<Organization> children = orgService.getChildren(parentId);
            return ApiResponse.success(children.stream().map(this::toFlatMap).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询子机构失败", e);
            return ApiResponse.internalError("查询子机构失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        try {
            Organization org = orgService.getOrganization(id);
            if (org == null) return ApiResponse.notFound("机构不存在");
            return ApiResponse.success(toFlatMap(org));
        } catch (Exception e) {
            log.error("查询机构详情失败", e);
            return ApiResponse.internalError("查询机构详情失败: " + e.getMessage());
        }
    }

    @PostMapping
    @RequirePermission(permission = "system:org:create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            String orgName = (String) body.get("orgName");
            String orgCode = (String) body.get("orgCode");
            if (orgName == null || orgName.isEmpty()) return ApiResponse.badRequest("机构名称不能为空");

            Organization org = orgService.createOrganization(
                orgName,
                orgCode != null ? orgCode : "",
                (String) body.get("parentOrgId"),
                (String) body.getOrDefault("orgType", "DEPARTMENT"),
                (String) body.get("description"),
                "admin"
            );
            return ApiResponse.success(toFlatMap(org));
        } catch (Exception e) {
            log.error("创建机构失败", e);
            return ApiResponse.internalError("创建机构失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            Organization org = orgService.updateOrganization(
                id,
                (String) body.get("orgName"),
                (String) body.get("orgCode"),
                (String) body.get("orgType"),
                (String) body.get("description"),
                "admin"
            );
            return ApiResponse.success(toFlatMap(org));
        } catch (Exception e) {
            log.error("更新机构失败", e);
            return ApiResponse.internalError("更新机构失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permission = "system:org:delete")
    public ApiResponse<Void> delete(@PathVariable String id) {
        try {
            orgService.deleteOrganization(id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除机构失败", e);
            return ApiResponse.internalError("删除机构失败: " + e.getMessage());
        }
    }

    @PutMapping("/{orgId}/users/{userId}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<Void> assignUser(@PathVariable String orgId, @PathVariable String userId,
                                         @RequestParam(defaultValue = "false") boolean primary) {
        try {
            orgService.assignUserToOrg(userId, orgId, primary, "admin");
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("分配用户到机构失败", e);
            return ApiResponse.internalError("分配用户到机构失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{orgId}/users/{userId}")
    @RequirePermission(permission = "system:org:update")
    public ApiResponse<Void> removeUser(@PathVariable String orgId, @PathVariable String userId) {
        try {
            orgService.removeUserFromOrg(userId, orgId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("移除用户机构关联失败", e);
            return ApiResponse.internalError("移除用户机构关联失败: " + e.getMessage());
        }
    }

    private Map<String, Object> toFlatMap(Organization o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orgId", o.getOrgId());
        m.put("orgName", o.getOrgName());
        m.put("orgCode", o.getOrgCode());
        m.put("parentOrgId", o.getParentOrgId());
        m.put("orgType", o.getOrgType());
        m.put("description", o.getDescription());
        m.put("status", o.getStatus());
        m.put("remark", o.getRemark());
        m.put("path", o.getPath());
        m.put("createdTime", o.getCreatedTime());
        return m;
    }

    private Map<String, Object> toTreeMap(Organization o) {
        if (o == null) return null;
        Map<String, Object> m = toFlatMap(o);
        if (o.getChildren() != null && !o.getChildren().isEmpty()) {
            m.put("children", o.getChildren().stream().map(this::toTreeMap).collect(Collectors.toList()));
        }
        return m;
    }
}
